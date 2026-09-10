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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
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
    if (showAccount) Dialog(onDismissRequest = { if (!account.busy) showAccount = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        AccountScreen(account) { showAccount = false }
    }
}

@Composable fun AccountCard(account: KimiAccount?, onOpen: () -> Unit) {
    PlayCard(Lavender) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BubbleIcon(Icons.Rounded.Person, Color.White.copy(alpha = .65f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (account == null) "A little more connected." else "Your Kimi account", style = MaterialTheme.typography.titleMedium)
                Text(account?.email ?: "Your space, your pace.", style = MaterialTheme.typography.bodyMedium, color = Quiet)
            }
        }
        Spacer(Modifier.height(14.dp))
        MainButton(if (account == null) "Sign in to Kimi" else "Manage account", onClick = onOpen)
    }
}

@Composable private fun PasswordField(value: String, onChange: (String) -> Unit, label: String = "Password", enabled: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { IconButton(onClick = { visible = !visible }) {
            Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (visible) "Hide password" else "Show password")
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
                IconButton(enabled = !vm.busy, onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Kimi") }
                Text("Your little account", style = MaterialTheme.typography.titleMedium)
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Purple)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(23.dp, 12.dp, 23.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                PlayCard(Lavender) {
                    Flower(Modifier.size(66.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(if (user != null) "You belong here." else if (create) "Let’s grow together." else "Hello, you.", style = MaterialTheme.typography.headlineLarge)
                    Text(if (user != null) user.email else "Small steps. A space of your own.", color = Quiet)
                }
                vm.message?.let { message ->
                    PlayCard(if (vm.error) Peach else Mint) { Text(message, style = MaterialTheme.typography.bodyMedium) }
                }
                if (user == null) {
                    PlayCard(Color.White) {
                        Text(if (create) "Create your Kimi account" else "Welcome back to Kimi", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(onClick = { vm.googleSignIn(context) }, enabled = !vm.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(painterResource(R.drawable.ic_google), null, Modifier.size(20.dp), tint = Color.Unspecified)
                            Spacer(Modifier.width(12.dp))
                            Text("Sign in with Google")
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("or continue with email", color = Quiet, style = MaterialTheme.typography.bodyMedium)
                        if (create) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(name, { name = it.take(30) }, label = { Text("Account name") }, enabled = !vm.busy,
                                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(email, { email = it.take(254) }, label = { Text("Email address") }, enabled = !vm.busy,
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                        Spacer(Modifier.height(12.dp))
                        PasswordField(password, { password = it }, enabled = !vm.busy)
                        if (create) {
                            Spacer(Modifier.height(12.dp))
                            PasswordField(confirmPassword, { confirmPassword = it }, "Confirm password", !vm.busy)
                            Text("At least 8 characters. Make it something only you know.", style = MaterialTheme.typography.bodySmall, color = Quiet)
                            if (confirmPassword.isNotEmpty() && confirmPassword != password) Text("Your passwords don’t match yet.", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(18.dp))
                        MainButton(if (create) "Create my account" else "Sign in", enabled = !vm.busy && email.isNotBlank() && password.isNotEmpty() && (!create || (name.isNotBlank() && password.length >= 8 && password == confirmPassword))) {
                            if (create) vm.createAccount(name, email, password) else vm.emailSignIn(email, password)
                        }
                        if (!create) TextButton(onClick = { vm.resetPassword(email) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Forgot password?") }
                        TextButton(onClick = { create = !create; vm.clearMessage() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (create) "Already have an account? Sign in" else "New here? Create an account")
                        }
                    }
                } else {
                    PlayCard(Color.White) {
                        Text("Lovely to see you, ${user.name.ifBlank { "friend" }}.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(14.dp))
                        Text(if (user.verified) "Email verified" else "Email not verified yet", color = if (user.verified) Quiet else Purple)
                        if (!user.verified) TextButton(enabled = !vm.busy, onClick = vm::verifyEmail) { Text("Send verification email") }
                        TextButton(enabled = !vm.busy, onClick = vm::refresh) { Text("Refresh account") }
                        OutlinedTextField(name, { name = it.take(30) }, label = { Text("Account name") }, singleLine = true,
                            enabled = !vm.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                        Spacer(Modifier.height(14.dp))
                        MainButton("Save account name", enabled = !vm.busy && name.isNotBlank() && name.trim() != user.name) { vm.updateName(name) }
                        if (user.passwordProvider) TextButton(enabled = !vm.busy, onClick = { vm.resetPassword(user.email) }, modifier = Modifier.fillMaxWidth()) { Text("Reset my password") }
                    }
                    PlayCard(Yellow) {
                        Text("Bring your little wins along.", style = MaterialTheme.typography.titleMedium)
                        Text("Copy this device’s guest habits and saved reflections into an empty account space. Your guest original stays safe.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(enabled = !vm.busy, onClick = { copyGuest = true }, modifier = Modifier.fillMaxWidth()) { Text("Copy guest progress") }
                    }
                    OutlinedButton(enabled = !vm.busy, onClick = { signOut = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Sign out") }
                    TextButton(enabled = !vm.busy, onClick = { password = ""; delete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete my account", color = Color(0xFF9C463A)) }
                }
                PlayCard(Mint) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Lock, null); Spacer(Modifier.width(10.dp)); Text("Your world stays yours.", style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.height(10.dp))
                    Text("Firebase securely manages your account. Habits and journal entries stay on this device, separately for each account and guest. Cloud sync is not enabled. Export a backup before switching devices or uninstalling Kimi.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    if (signOut) AlertDialog(onDismissRequest = { signOut = false }, title = { Text("See you soon?") },
        text = { Text("Your account’s progress stays on this device for your next sign-in. Kimi will open your separate guest space.") },
        confirmButton = { TextButton(onClick = { signOut = false; vm.signOut() }) { Text("Sign out now") } },
        dismissButton = { TextButton(onClick = { signOut = false }) { Text("Stay here") } })
    if (copyGuest) AlertDialog(onDismissRequest = { copyGuest = false }, title = { Text("Bring in your guest progress?") },
        text = { Text("This copies saved habits, check-ins and reflections only if your account space is empty. Unfinished journal drafts stay in your guest space.") },
        confirmButton = { TextButton(onClick = { copyGuest = false; vm.copyGuestSpace() }) { Text("Copy progress") } },
        dismissButton = { TextButton(onClick = { copyGuest = false }) { Text("Not now") } })
    if (delete && user != null) AlertDialog(onDismissRequest = { if (!vm.busy) { delete = false; password = "" } },
        title = { Text("Let this account go?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("This permanently deletes your Firebase account and its habits, check-ins, journal and drafts on this device. Export a backup first. Guest data is kept.")
            if (user.passwordProvider) PasswordField(password, { password = it }, "Confirm with password", !vm.busy)
            else Text("Confirm your Google account on the next screen.")
            if (vm.error) vm.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !vm.busy && (!user.passwordProvider || password.isNotEmpty()),
            onClick = { vm.deleteAccount(context, password) }) { Text("Delete permanently") } },
        dismissButton = { TextButton(enabled = !vm.busy, onClick = { delete = false; password = ""; vm.clearMessage() }) { Text("Keep my account") } })
}
