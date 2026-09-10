package com.forma.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Application

@Composable fun KimiRoot(account: AccountViewModel = viewModel()) {
    var showAccount by rememberSaveable { mutableStateOf(false) }
    val uid = account.account?.uid.orEmpty()
    val application = LocalContext.current.applicationContext as Application
    // Clear tab/editor state and old ViewModels when an account changes, including sign-out.
    key(uid) {
        val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        val habits: FormaViewModel = viewModel(viewModelStoreOwner = owner,
            factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application))
        FormaApp(habits, account.account) { account.clearMessage(); showAccount = true }
    }
    // A finished sign-in closes the account screen first, so the confirmation lands over the app
    // the person is actually returning to.
    LaunchedEffect(account.welcome) { if (account.welcome != null) showAccount = false }
    if (showAccount) Dialog(onDismissRequest = { if (!account.busy) showAccount = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        AccountScreen(account) { showAccount = false }
    }
    account.welcome?.let { WelcomeDialog(it, account::dismissWelcome) }
}

/**
 * Confirms a finished sign-in and returns the person to their habits.
 *
 * Deliberately not an [AlertDialog]: a stock one lands as a flat system box in the middle of an app
 * built out of rounded colour cards and a mascot, and this is the first thing a new account sees.
 */
@Composable fun WelcomeDialog(welcome: Welcome, onDismiss: () -> Unit) {
    val created = welcome == Welcome.Created
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Cream).padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Flower(Modifier.size(96.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(if (created) R.string.welcome_created_title else R.string.welcome_back_title),
                style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(if (created) R.string.welcome_created_body else R.string.welcome_back_body),
                style = MaterialTheme.typography.bodyMedium, color = Quiet, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            MainButton(stringResource(R.string.action_welcome_continue), onClick = onDismiss)
        }
    }
}

@Composable fun AccountCard(account: KimiAccount?, onOpen: () -> Unit) {
    PlayCard(Lavender) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BubbleIcon(Icons.Rounded.Person, Overlay)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (account == null) R.string.account_card_title_signed_out else R.string.account_card_title_signed_in), style = MaterialTheme.typography.titleMedium)
                Text(account?.email ?: stringResource(R.string.account_card_body), style = MaterialTheme.typography.bodyMedium, color = Quiet)
            }
        }
        Spacer(Modifier.height(14.dp))
        MainButton(stringResource(if (account == null) R.string.action_sign_in_kimi else R.string.action_manage_account), onClick = onOpen)
    }
}

@Composable private fun PasswordField(value: String, onChange: (String) -> Unit, label: String? = null, enabled: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    val fieldLabel = label ?: stringResource(R.string.field_password)
    OutlinedTextField(value, onChange, label = { Text(fieldLabel) }, singleLine = true, enabled = enabled,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { IconButton(onClick = { visible = !visible }) {
            Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, stringResource(if (visible) R.string.cd_hide_password else R.string.cd_show_password))
        } })
}

@Composable fun AccountScreen(vm: AccountViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val user = vm.account
    var create by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable(user?.uid, user?.name) { mutableStateOf(user?.name.orEmpty()) }
    // Deliberately not rememberSaveable: secrets never go into Android's saved state.
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf(false) }
    var copyGuest by remember { mutableStateOf(false) }
    var signOut by remember { mutableStateOf(false) }
    LaunchedEffect(user?.uid, create) { password = ""; confirmPassword = ""; delete = false }
    Surface(color = Cream, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp, 5.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = !vm.busy, onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back_to_kimi)) }
                Text(stringResource(R.string.account_screen_title), style = MaterialTheme.typography.titleMedium)
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(23.dp, 12.dp, 23.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                PlayCard(Lavender) {
                    Flower(Modifier.size(66.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(if (user != null) R.string.account_hero_signed_in else if (create) R.string.account_hero_create else R.string.account_hero_sign_in), style = MaterialTheme.typography.headlineLarge)
                    Text(if (user != null) user.email else stringResource(R.string.account_hero_body), color = Quiet)
                }
                // Stated before the decision rather than buried under the form: this is what an
                // account actually gets you.
                if (user == null) PlayCard(Mint) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BubbleIcon(Icons.Rounded.CloudSync, Overlay)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.account_no_sync_title), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.account_no_sync_body), style = MaterialTheme.typography.bodyMedium)
                }
                vm.message?.let { message ->
                    PlayCard(if (vm.error) Peach else Mint) { Text(message, style = MaterialTheme.typography.bodyMedium) }
                }
                if (user == null) {
                    PlayCard(Paper) {
                        Text(stringResource(if (create) R.string.account_form_create else R.string.account_form_sign_in), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(onClick = { vm.googleSignIn(context) }, enabled = !vm.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(painterResource(R.drawable.ic_google), null, Modifier.size(20.dp), tint = Color.Unspecified)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.action_google_sign_in))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(R.string.account_or_email), color = Quiet, style = MaterialTheme.typography.bodyMedium)
                        if (create) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(name, { name = it.take(30) }, label = { Text(stringResource(R.string.field_account_name)) }, enabled = !vm.busy,
                                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(email, { email = it.take(254) }, label = { Text(stringResource(R.string.field_email)) }, enabled = !vm.busy,
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                        Spacer(Modifier.height(12.dp))
                        PasswordField(password, { password = it }, enabled = !vm.busy)
                        if (create) {
                            Spacer(Modifier.height(12.dp))
                            PasswordField(confirmPassword, { confirmPassword = it }, stringResource(R.string.field_confirm_password), !vm.busy)
                            Text(stringResource(R.string.account_password_hint), style = MaterialTheme.typography.bodySmall, color = Quiet)
                            if (confirmPassword.isNotEmpty() && confirmPassword != password) Text(stringResource(R.string.account_password_mismatch), color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(18.dp))
                        MainButton(stringResource(if (create) R.string.action_create_account else R.string.action_sign_in), enabled = !vm.busy && email.isNotBlank() && password.isNotEmpty() && (!create || (name.isNotBlank() && password.length >= 8 && password == confirmPassword))) {
                            if (create) vm.createAccount(name, email, password) else vm.emailSignIn(email, password)
                        }
                        if (!create) TextButton(onClick = { vm.resetPassword(email) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_forgot_password)) }
                        TextButton(onClick = { create = !create; vm.clearMessage() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (create) R.string.action_switch_to_sign_in else R.string.action_switch_to_create))
                        }
                    }
                } else {
                    PlayCard(Paper) {
                        Text(stringResource(R.string.account_greeting, user.name.ifBlank { stringResource(R.string.account_greeting_fallback) }), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(14.dp))
                        Text(stringResource(if (user.verified) R.string.account_email_verified else R.string.account_email_unverified), color = if (user.verified) Quiet else Accent)
                        if (!user.verified) TextButton(enabled = !vm.busy, onClick = vm::verifyEmail) { Text(stringResource(R.string.action_send_verification)) }
                        TextButton(enabled = !vm.busy, onClick = vm::refresh) { Text(stringResource(R.string.action_refresh_account)) }
                        OutlinedTextField(name, { name = it.take(30) }, label = { Text(stringResource(R.string.field_account_name)) }, singleLine = true,
                            enabled = !vm.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                        Spacer(Modifier.height(14.dp))
                        MainButton(stringResource(R.string.action_save_account_name), enabled = !vm.busy && name.isNotBlank() && name.trim() != user.name) { vm.updateName(name) }
                        if (user.passwordProvider) TextButton(enabled = !vm.busy, onClick = { vm.resetPassword(user.email) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_reset_password)) }
                    }
                    PlayCard(Yellow) {
                        Text(stringResource(R.string.account_copy_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.account_copy_body), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(enabled = !vm.busy, onClick = { copyGuest = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_copy_guest)) }
                    }
                    OutlinedButton(enabled = !vm.busy, onClick = { signOut = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text(stringResource(R.string.action_sign_out)) }
                    TextButton(enabled = !vm.busy, onClick = { password = ""; delete = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_delete_account), color = Danger) }
                }
                PlayCard(Mint) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Lock, null); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.account_privacy_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    if (signOut) AlertDialog(onDismissRequest = { signOut = false }, title = { Text(stringResource(R.string.dialog_sign_out_title)) },
        text = { Text(stringResource(R.string.dialog_sign_out_body)) },
        confirmButton = { TextButton(onClick = { signOut = false; vm.signOut() }) { Text(stringResource(R.string.action_sign_out_now)) } },
        dismissButton = { TextButton(onClick = { signOut = false }) { Text(stringResource(R.string.action_stay_here)) } })
    if (copyGuest) AlertDialog(onDismissRequest = { copyGuest = false }, title = { Text(stringResource(R.string.dialog_copy_guest_title)) },
        text = { Text(stringResource(R.string.dialog_copy_guest_body)) },
        confirmButton = { TextButton(onClick = { copyGuest = false; vm.copyGuestSpace() }) { Text(stringResource(R.string.action_copy_progress)) } },
        dismissButton = { TextButton(onClick = { copyGuest = false }) { Text(stringResource(R.string.action_not_now)) } })
    if (delete && user != null) AlertDialog(onDismissRequest = { if (!vm.busy) { delete = false; password = "" } },
        title = { Text(stringResource(R.string.dialog_delete_account_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.dialog_delete_account_body))
            if (user.passwordProvider) PasswordField(password, { password = it }, stringResource(R.string.field_confirm_with_password), !vm.busy)
            else Text(stringResource(R.string.dialog_delete_account_google))
            if (vm.error) vm.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !vm.busy && (!user.passwordProvider || password.isNotEmpty()),
            onClick = { vm.deleteAccount(context, password) }) { Text(stringResource(R.string.action_delete_permanently)) } },
        dismissButton = { TextButton(enabled = !vm.busy, onClick = { delete = false; password = ""; vm.clearMessage() }) { Text(stringResource(R.string.action_keep_account)) } })
}
