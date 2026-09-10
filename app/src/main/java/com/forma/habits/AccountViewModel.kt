package com.forma.habits

import android.app.Application
import android.content.Context
import android.content.MutableContextWrapper
import androidx.compose.runtime.*
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

/** Firebase owns credential persistence. Passwords and tokens never enter Kimi backups. */
internal object AccountSession {
    // Tests use a named Firebase app connected to the local Auth emulator.
    internal var testAuth: FirebaseAuth? = null
    val auth: FirebaseAuth get() = testAuth ?: FirebaseAuth.getInstance()
    val owner: String get() = auth.currentUser?.uid.orEmpty()
    fun preferenceName(base: String, owner: String = this.owner): String =
        if (owner.isEmpty()) base else base + "_" + java.security.MessageDigest.getInstance("SHA-256")
            .digest(owner.toByteArray()).joinToString("") { "%02x".format(it) }
}

data class KimiAccount(val uid: String, val email: String, val name: String, val verified: Boolean, val passwordProvider: Boolean)

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = AccountSession.auth
    private val app: Application get() = getApplication()
    private fun text(resId: Int, vararg args: Any) = app.getString(resId, *args)
    var account by mutableStateOf(snapshot())
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf(false)
        private set
    private val listener = FirebaseAuth.AuthStateListener { account = snapshot() }
    init { auth.addAuthStateListener(listener) }
    override fun onCleared() { auth.removeAuthStateListener(listener) }
    private fun snapshot() = auth.currentUser?.let { user ->
        KimiAccount(user.uid, user.email.orEmpty(), user.displayName.orEmpty(), user.isEmailVerified,
            user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID })
    }
    fun clearMessage() { message = null; error = false }
    private fun action(block: suspend () -> String?) {
        if (busy) return
        busy = true; clearMessage()
        viewModelScope.launch {
            try { message = block(); account = snapshot() }
            catch (e: CancellationException) { throw e }
            catch (_: GetCredentialCancellationException) { /* Dismissing Google's sheet is not an error. */ }
            catch (e: Exception) { error = true; message = friendlyAuthError(app, e) }
            finally { busy = false }
        }
    }
    fun emailSignIn(email: String, password: String) = action {
        demand(email.trim().isNotEmpty() && password.isNotEmpty(), R.string.err_account_missing_fields)
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        text(R.string.msg_signed_in)
    }
    fun createAccount(name: String, email: String, password: String) = action {
        demand(name.trim().length in 1..30, R.string.err_account_name)
        demand(password.length >= 8, R.string.err_account_password_short)
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!
        // Account creation is successful even if the optional profile update is interrupted.
        val updated = try { user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await(); true }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { false }
        if (updated) text(R.string.msg_account_created) else text(R.string.msg_account_created_no_name)
    }
    private suspend fun googleCredential(context: Context): AuthCredential {
        val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
        val result = CredentialManager.create(context).getCredential(MutableContextWrapper(context),
            GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = result.credential
        demand(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, R.string.err_google_confirm)
        return GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(credential.data).idToken, null)
    }
    fun googleSignIn(context: Context) = action {
        auth.signInWithCredential(googleCredential(context)).await()
        text(R.string.msg_google_signed_in)
    }
    fun resetPassword(email: String) = action {
        demand(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches(), R.string.err_invalid_email)
        try { auth.sendPasswordResetEmail(email.trim()).await() }
        catch (_: FirebaseAuthInvalidUserException) { /* Same response for unknown accounts. */ }
        text(R.string.msg_reset_sent)
    }
    fun verifyEmail() = action {
        val user = auth.currentUser ?: throw KimiMessage(R.string.err_sign_in_first)
        user.sendEmailVerification().await()
        text(R.string.msg_verification_sent)
    }
    fun refresh() = action {
        try { auth.currentUser?.reload()?.await() }
        catch (e: FirebaseAuthInvalidUserException) { auth.signOut(); throw e }
        if (auth.currentUser?.isEmailVerified == true) text(R.string.msg_email_verified) else text(R.string.msg_account_refreshed)
    }
    fun updateName(name: String) = action {
        demand(name.trim().length in 1..30, R.string.err_account_name_range)
        auth.currentUser?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())?.await()
        text(R.string.msg_name_updated)
    }
    private suspend fun clearCredentials() {
        // Signing out of Firebase must still succeed if the credential provider is unavailable.
        try { CredentialManager.create(getApplication()).clearCredentialState(ClearCredentialStateRequest()) }
        catch (e: CancellationException) { throw e }
        catch (_: androidx.credentials.exceptions.ClearCredentialException) { }
    }
    fun signOut() = action {
        auth.signOut()
        clearCredentials()
        text(R.string.msg_signed_out)
    }
    fun deleteAccount(context: Context, password: String) = action {
        val user = auth.currentUser ?: throw KimiMessage(R.string.err_sign_in_first)
        val owner = user.uid
        val credential = if (user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }) {
            demand(password.isNotEmpty(), R.string.err_password_required)
            EmailAuthProvider.getCredential(user.email!!, password)
        } else googleCredential(context)
        // Reauthentication rejects a different Google account. Never sign in to it here.
        user.reauthenticate(credential).await()
        user.delete().await()
        try {
            HabitStore.get(getApplication(), owner).erase()
            withContext(Dispatchers.IO) {
                check(getApplication<Application>().getSharedPreferences(AccountSession.preferenceName("kimi_drafts", owner), 0).edit().clear().commit())
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            clearCredentials()
            return@action text(R.string.msg_account_deleted_partial)
        }
        clearCredentials()
        text(R.string.msg_account_deleted)
    }
    fun copyGuestSpace() = action {
        val owner = auth.currentUser?.uid ?: throw KimiMessage(R.string.err_sign_in_first)
        val guest = HabitStore.get(getApplication(), "").state.value
        val target = HabitStore.get(getApplication(), owner)
        target.update { current ->
            demand(current.habits.isEmpty() && current.checks.isEmpty() && current.journal.isEmpty(), R.string.err_account_not_empty)
            guest.copy(onboarded = true)
        }
        text(R.string.msg_guest_copied)
    }
}

internal fun friendlyAuthError(context: Context, error: Exception): String = context.getString(when (error) {
    is KimiMessage -> return context.kimiMessage(error)
    is FirebaseNetworkException -> R.string.err_auth_network
    is FirebaseTooManyRequestsException -> R.string.err_auth_too_many
    is NoCredentialException -> R.string.err_auth_no_credential
    is FirebaseAuthWeakPasswordException -> R.string.err_auth_weak_password
    is FirebaseAuthUserCollisionException -> R.string.err_auth_collision
    is FirebaseAuthRecentLoginRequiredException -> R.string.err_auth_recent_login
    is FirebaseAuthInvalidCredentialsException -> R.string.err_auth_invalid_credentials
    is FirebaseAuthInvalidUserException -> R.string.err_auth_invalid_user
    else -> R.string.err_auth_generic
})
