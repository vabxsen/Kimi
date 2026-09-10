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
            catch (e: Exception) { error = true; message = friendlyAuthError(e) }
            finally { busy = false }
        }
    }
    fun emailSignIn(email: String, password: String) = action {
        require(email.trim().isNotEmpty() && password.isNotEmpty()) { "Enter your email and password." }
        auth.signInWithEmailAndPassword(email.trim(), password).await()
        "Welcome back. Your little space is ready."
    }
    fun createAccount(name: String, email: String, password: String) = action {
        require(name.trim().length in 1..30) { "Add a name, up to 30 characters." }
        require(password.length >= 8) { "Choose a password with at least 8 characters." }
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user!!
        // Account creation is successful even if the optional profile update is interrupted.
        val updated = try { user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await(); true }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { false }
        if (updated) "Your account is ready. You can verify your email below."
        else "Your account is ready. Your name could not be updated; try again below."
    }
    private suspend fun googleCredential(context: Context): AuthCredential {
        val option = GetSignInWithGoogleOption.Builder(context.getString(R.string.default_web_client_id)).build()
        val result = CredentialManager.create(context).getCredential(MutableContextWrapper(context),
            GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = result.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Google could not confirm this account. Please try again."
        }
        return GoogleAuthProvider.getCredential(GoogleIdTokenCredential.createFrom(credential.data).idToken, null)
    }
    fun googleSignIn(context: Context) = action {
        auth.signInWithCredential(googleCredential(context)).await()
        "You’re in. Welcome to Kimi!"
    }
    fun resetPassword(email: String) = action {
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Enter a valid email address first." }
        try { auth.sendPasswordResetEmail(email.trim()).await() }
        catch (_: FirebaseAuthInvalidUserException) { /* Same response for unknown accounts. */ }
        "If this email has a password account, a reset link is on its way. Check your inbox and spam folder."
    }
    fun verifyEmail() = action {
        val user = auth.currentUser ?: error("Sign in first.")
        user.sendEmailVerification().await()
        "Verification email sent. Open the link, then tap Refresh account."
    }
    fun refresh() = action {
        try { auth.currentUser?.reload()?.await() }
        catch (e: FirebaseAuthInvalidUserException) { auth.signOut(); throw e }
        if (auth.currentUser?.isEmailVerified == true) "Your email is verified." else "Account refreshed."
    }
    fun updateName(name: String) = action {
        require(name.trim().length in 1..30) { "Use a name between 1 and 30 characters." }
        auth.currentUser?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())?.await()
        "Your account name is updated."
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
        "Signed out. Your account’s local space is kept for your next sign-in."
    }
    fun deleteAccount(context: Context, password: String) = action {
        val user = auth.currentUser ?: error("Sign in first.")
        val owner = user.uid
        val credential = if (user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }) {
            require(password.isNotEmpty()) { "Enter your password to confirm." }
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
            return@action "Your account was deleted, but local cleanup failed. Clear Kimi’s storage in Android Settings to remove the remaining device data. Export your guest space first."
        }
        clearCredentials()
        "Your account and its local space have been deleted. Guest data is kept."
    }
    fun copyGuestSpace() = action {
        val owner = auth.currentUser?.uid ?: error("Sign in first.")
        val guest = HabitStore.get(getApplication(), "").state.value
        val target = HabitStore.get(getApplication(), owner)
        target.update { current ->
            require(current.habits.isEmpty() && current.checks.isEmpty() && current.journal.isEmpty()) { "This account already has progress. Export and restore a backup to replace it." }
            guest.copy(onboarded = true)
        }
        "Your guest habits and saved reflections were copied. The guest original is still safe."
    }
}

internal fun friendlyAuthError(error: Exception): String = when (error) {
    is FirebaseNetworkException -> "Can’t connect right now. Check your connection and try again."
    is FirebaseTooManyRequestsException -> "A few too many attempts. Please wait a little before trying again."
    is NoCredentialException -> "Add a Google account on this device, then try again, or use email."
    is FirebaseAuthWeakPasswordException -> "Choose a stronger password with at least 8 characters."
    is FirebaseAuthUserCollisionException -> "This email already has an account. Sign in with its original method."
    is FirebaseAuthRecentLoginRequiredException -> "For your security, sign in again and retry this action."
    is FirebaseAuthInvalidCredentialsException -> "Those details couldn’t be verified. Check your email and password, or try Google."
    is FirebaseAuthInvalidUserException -> "This account is unavailable. Sign in again or create an account."
    is IllegalArgumentException -> error.message ?: "Check your details and try again."
    else -> "That didn’t go through. Please try again."
}
