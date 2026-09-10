package com.forma.habits

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable fun WelcomeScreen(busy: Boolean, onStart: (String, Boolean) -> Unit, onAccount: () -> Unit = {}, onRestore: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var starters by rememberSaveable { mutableStateOf(true) }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize().background(Cream).safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
        .padding(23.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Flower(Modifier.size(40.dp)); Spacer(Modifier.width(8.dp)); Text("Kimi.", style = MaterialTheme.typography.headlineLarge)
        }
        PlayCard(Lavender) {
            Flower(Modifier.size(135.dp).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))
            Text("Small steps.\nYour kind of happy.", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp)); Text("A little space for habits that feel good, and a life that feels more like you.", color = Quiet)
        }
        OutlinedTextField(name, { name = it.take(30) }, label = { Text("What should we call you?") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
        PlayCard(Mint) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("A little help getting started", style = MaterialTheme.typography.titleMedium)
                    Text("Five gentle rituals to make your own. Your progress starts today.", color = Quiet, fontSize = 12.sp)
                }
                Switch(starters, { starters = it }, Modifier.semantics { contentDescription = "Start with five suggested habits" })
            }
        }
        MainButton(if (busy) "Making room for you…" else "Let’s grow together", enabled = !busy && name.isNotBlank()) { onStart(name, starters) }
        TextButton(onClick = onAccount, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Your Kimi account · Sign in or manage") }
        TextButton(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Already have a Kimi backup?") }
        Text("No account needed. Your habits and journal stay on this device.", color = Quiet, fontSize = 12.sp)
    }
}

@Composable private fun notificationAccess(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var allowed by remember { mutableStateOf(Reminders.allowed(context)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) allowed = Reminders.allowed(context) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return allowed
}

@Composable fun ReminderPicker(minutes: Int?, onChange: (Int?) -> Unit) {
    val context = LocalContext.current
    val allowed = notificationAccess()
    var permissionGranted by remember { mutableStateOf(allowed) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("A gentle nudge", style = MaterialTheme.typography.titleMedium)
                Text("Only on scheduled days, if it’s still undone.", color = Quiet, fontSize = 11.sp)
            }
            Switch(checked = minutes != null, onCheckedChange = { enabled ->
                onChange(if (enabled) 9 * 60 else null)
                if (enabled && !Reminders.allowed(context) && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }, modifier = Modifier.semantics { contentDescription = "Habit reminder" })
        }
        if (minutes != null) {
            val format = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, hour, minute -> onChange(hour * 60 + minute) }, minutes / 60, minutes % 60, DateFormat.is24HourFormat(context)).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("Remind me around ${LocalTime.of(minutes / 60, minutes % 60).format(format)}") }
            Text("Android may delay a reminder to save battery.", color = Quiet, fontSize = 11.sp)
            if (!allowed && !permissionGranted) {
                Text("Notifications are off. Enable them to receive your nudges.", color = Purple, fontSize = 12.sp)
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Open notification settings") }
            }
        }
    }
}

@Composable fun NotificationSettings(habits: List<Habit>, onSave: (Habit, () -> Unit) -> Unit) {
    val context = LocalContext.current
    val allowed = notificationAccess()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var minutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    PlayCard(Yellow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsActive, null); Spacer(Modifier.width(12.dp))
            Text("A friendly little nudge", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text(if (allowed) "Notifications are on. Choose a ritual below to set its time. Completed habits won’t nudge you again that day."
            else "Notifications are off. Allow them here, then choose a ritual below to set its time.", color = Quiet, fontSize = 12.sp)
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Notification settings", fontWeight = FontWeight.Bold) }
        habits.forEach { habit ->
            TextButton(onClick = { editingId = habit.id; minutes = habit.reminderMinutes }, modifier = Modifier.fillMaxWidth()) {
                Text(habit.name, modifier = Modifier.weight(1f), color = Ink)
                Spacer(Modifier.width(12.dp)); Text(habit.reminderMinutes?.let { LocalTime.of(it / 60, it % 60).toString() } ?: "Off", color = Purple)
            }
        }
        if (habits.isEmpty()) Text("Plant your first habit to add a reminder.", color = Quiet, fontSize = 12.sp)
    }
    habits.find { it.id == editingId }?.let { habit ->
        AlertDialog(onDismissRequest = { if (!saving) editingId = null }, title = { Text(habit.name) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { ReminderPicker(minutes) { minutes = it } } },
            confirmButton = { TextButton(enabled = !saving, onClick = {
                saving = true
                onSave(habit.copy(reminderMinutes = minutes)) { saving = false; editingId = null }
            }) { Text("Save reminder") } },
            dismissButton = { TextButton(onClick = { saving = false; editingId = null }) { Text("Cancel") } })
    }
}
