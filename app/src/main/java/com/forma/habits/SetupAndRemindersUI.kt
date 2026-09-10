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
import androidx.compose.ui.res.stringResource
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

@Composable fun WelcomeScreen(busy: Boolean, onStart: (String) -> Unit, onAccount: () -> Unit = {}, onRestore: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize().background(Cream).safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
        .padding(23.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Flower(Modifier.size(40.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.app_name) + stringResource(R.string.app_wordmark_dot), style = MaterialTheme.typography.headlineLarge)
        }
        PlayCard(Lavender) {
            Flower(Modifier.size(135.dp).align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.welcome_body), color = Quiet)
        }
        OutlinedTextField(name, { name = it.take(30) }, label = { Text(stringResource(R.string.settings_name_question)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
        MainButton(stringResource(if (busy) R.string.welcome_busy else R.string.action_lets_grow), enabled = !busy && name.isNotBlank()) { onStart(name) }
        TextButton(onClick = onAccount, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_account_link)) }
        TextButton(onClick = onRestore, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_restore_link)) }
        Text(stringResource(R.string.welcome_privacy), color = Quiet, fontSize = 12.sp)
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
                Text(stringResource(R.string.reminder_toggle_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.reminder_toggle_body), color = Quiet, fontSize = 11.sp)
            }
            val reminderLabel = stringResource(R.string.cd_reminder_switch)
            Switch(checked = minutes != null, onCheckedChange = { enabled ->
                onChange(if (enabled) 9 * 60 else null)
                if (enabled && !Reminders.allowed(context) && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }, modifier = Modifier.semantics { contentDescription = reminderLabel })
        }
        if (minutes != null) {
            val format = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a")
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, hour, minute -> onChange(hour * 60 + minute) }, minutes / 60, minutes % 60, DateFormat.is24HourFormat(context)).show()
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reminder_time_button, LocalTime.of(minutes / 60, minutes % 60).format(format))) }
            Text(stringResource(R.string.reminder_delay_notice), color = Quiet, fontSize = 11.sp)
            if (!allowed && !permissionGranted) {
                Text(stringResource(R.string.reminder_notifications_off), color = Accent, fontSize = 12.sp)
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text(stringResource(R.string.action_open_notification_settings)) }
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
            Text(stringResource(R.string.reminders_card_title), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(if (allowed) R.string.reminders_on_body else R.string.reminders_off_body), color = Quiet, fontSize = 12.sp)
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text(stringResource(R.string.action_notification_settings), fontWeight = FontWeight.Bold) }
        habits.forEach { habit ->
            TextButton(onClick = { editingId = habit.id; minutes = habit.reminderMinutes }, modifier = Modifier.fillMaxWidth()) {
                Text(habit.name, modifier = Modifier.weight(1f), color = Ink)
                Spacer(Modifier.width(12.dp)); Text(habit.reminderMinutes?.let { LocalTime.of(it / 60, it % 60).toString() } ?: stringResource(R.string.reminder_off), color = Accent)
            }
        }
        if (habits.isEmpty()) Text(stringResource(R.string.reminders_empty), color = Quiet, fontSize = 12.sp)
    }
    habits.find { it.id == editingId }?.let { habit ->
        AlertDialog(onDismissRequest = { if (!saving) editingId = null }, title = { Text(habit.name) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { ReminderPicker(minutes) { minutes = it } } },
            confirmButton = { TextButton(enabled = !saving, onClick = {
                saving = true
                onSave(habit.copy(reminderMinutes = minutes)) { saving = false; editingId = null }
            }) { Text(stringResource(R.string.action_save_reminder)) } },
            dismissButton = { TextButton(onClick = { saving = false; editingId = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}
