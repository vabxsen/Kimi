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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
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
            // The app icon draws itself beside the name: the first thing a new person sees.
            DotKLogo(Modifier.size(40.dp)); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.app_name) + stringResource(R.string.app_wordmark_dot), style = MaterialTheme.typography.headlineLarge)
        }
        PlayCard(Lavender) {
            // First impression: the mascot opens petal by petal, then settles into its idle sway.
            // Starts after Android's own window fade-in, which would otherwise hide the first petals.
            val bloom by rememberGrow(6f, 1400, 400)
            // It brightens as a name takes shape, and gives a little nod at every letter.
            val reduced = reducedMotion()
            val nod = remember { Animatable(0f) }
            LaunchedEffect(name) {
                if (name.isEmpty() || reduced) return@LaunchedEffect
                nod.snapTo(1f)
                nod.animateTo(0f, spring(dampingRatio = .35f, stiffness = Spring.StiffnessMedium))
            }
            val mood = when (name.trim().length) { 0 -> 2; in 1..2 -> 3; else -> 4 }
            Flower(Modifier.size(135.dp).align(Alignment.CenterHorizontally).graphicsLayer {
                rotationZ = nod.value * 7f
                translationY = -nod.value * 5.dp.toPx()
            }, bloom = bloom, alive = true, mood = mood)
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

@Composable private fun localTimeFormatter(): DateTimeFormatter {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(is24Hour) { DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a") }
}

@Composable fun ReminderPicker(minutes: Int?, count: Int, onChange: (Int?, Int) -> Unit) {
    val context = LocalContext.current
    val allowed = notificationAccess()
    val safeCount = count.coerceIn(1, MAX_DAILY_REMINDERS)
    var permissionGranted by remember { mutableStateOf(allowed) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }
    val format = localTimeFormatter()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.reminder_toggle_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.reminder_toggle_body), color = Quiet, fontSize = 11.sp)
            }
            val reminderLabel = stringResource(R.string.cd_reminder_switch)
            Switch(checked = minutes != null, onCheckedChange = { enabled ->
                onChange(if (enabled) normalizedReminderStart(9 * 60, safeCount) else null, safeCount)
                if (enabled && !Reminders.allowed(context) && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }, modifier = Modifier.semantics { contentDescription = reminderLabel })
        }
        if (minutes != null) {
            OutlinedButton(onClick = {
                TimePickerDialog(context, { _, hour, minute ->
                    onChange(normalizedReminderStart(hour * 60 + minute, safeCount), safeCount)
                }, minutes / 60, minutes % 60, DateFormat.is24HourFormat(context)).show()
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reminder_time_button, LocalTime.of(minutes / 60, minutes % 60).format(format))) }
            Text(stringResource(R.string.reminder_count_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.reminder_count_body), color = Quiet, fontSize = 11.sp)
            val countList = remember { (1..MAX_DAILY_REMINDERS).toList() }
            val countState = rememberLazyListState()
            LaunchedEffect(safeCount) { countState.scrollToItem((safeCount - 3).coerceAtLeast(0)) }
            LazyRow(
                state = countState,
                modifier = Modifier.fillMaxWidth().testTag("reminder_count_picker"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(countList, key = { it }) { option ->
                    val optionLabel = pluralStringResource(R.plurals.reminder_count_option, option, option)
                    FilterChip(
                        selected = option == safeCount,
                        onClick = { onChange(normalizedReminderStart(minutes, option), option) },
                        label = { Text(option.toString()) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("reminder_count_$option")
                            .semantics { contentDescription = optionLabel }
                    )
                }
            }
            val slots = remember(minutes, safeCount) { dailyReminderMinutes(minutes, safeCount) }
            val first = LocalTime.of(slots.first() / 60, slots.first() % 60).format(format)
            val last = LocalTime.of(slots.last() / 60, slots.last() % 60).format(format)
            Text(
                if (safeCount == 1) stringResource(R.string.reminder_schedule_once, first)
                else pluralStringResource(R.plurals.reminder_schedule_many, safeCount, safeCount, first, last),
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(stringResource(R.string.reminder_delay_notice), color = Quiet, fontSize = 11.sp)
            if (!allowed && !permissionGranted) {
                Text(stringResource(R.string.reminder_notifications_off), color = Accent, fontSize = 12.sp)
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text(stringResource(R.string.action_open_notification_settings)) }
            }
        }
    }
}

/**
 * One habit's reminder at a glance: its own icon and colour, its name, when it nudges in plain words,
 * and a switch. Tapping the row opens the time picker; the switch turns the nudge on or off.
 */
@Composable private fun ReminderRow(habit: Habit, format: DateTimeFormatter, busy: Boolean, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    fun clock(minute: Int) = LocalTime.of(minute / 60, minute % 60).format(format)
    val slots = habit.dailyReminderMinutes()
    val summary = when (slots.size) {
        0 -> stringResource(R.string.reminder_row_off)
        1 -> stringResource(R.string.reminder_row_once, clock(slots.first()))
        // Two times fit on the line as they are; more than that, the count and the first time.
        2 -> stringResource(R.string.reminder_row_twice, clock(slots.first()), clock(slots.last()))
        else -> pluralStringResource(R.plurals.reminder_row_many, slots.size, slots.size, clock(slots.first()))
    }
    val press = rememberSquish(.98f)
    val switchLabel = stringResource(R.string.cd_reminder_for, habit.name)
    Row(Modifier.fillMaxWidth().squish(press).clip(RoundedCornerShape(18.dp)).background(Overlay)
        .clickable(press.interaction, LocalIndication.current, role = Role.Button, onClick = onOpen)
        .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        BubbleIcon(HabitSymbols[habit.icon.coerceIn(HabitSymbols.indices)], TileColors[habit.color.coerceIn(0, 5)], size = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(habit.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary, fontSize = 12.sp, lineHeight = 16.sp, color = if (slots.isEmpty()) Quiet else Accent,
                fontWeight = if (slots.isEmpty()) FontWeight.Normal else FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = slots.isNotEmpty(), onCheckedChange = onToggle, enabled = !busy,
            modifier = Modifier.semantics { contentDescription = switchLabel })
    }
}

@Composable fun NotificationSettings(habits: List<Habit>, onSave: (Habit, (Boolean) -> Unit) -> Unit) {
    val context = LocalContext.current
    val allowed = notificationAccess()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var minutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var count by rememberSaveable { mutableIntStateOf(1) }
    var saving by remember { mutableStateOf(false) }
    // Which habit's switch is mid-save, so a second tap cannot race the first.
    var switching by remember { mutableStateOf<String?>(null) }
    val format = localTimeFormatter()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun openSystemSettings() = context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    PlayCard(Yellow) {
        // One line of guidance; the gear opens Android's own settings for sounds and channels.
        Row(verticalAlignment = Alignment.CenterVertically) {
            BubbleIcon(Icons.Rounded.NotificationsActive, Overlay)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.reminders_card_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.reminders_card_body), color = Quiet, fontSize = 12.sp, lineHeight = 16.sp)
            }
            IconButton(onClick = ::openSystemSettings) {
                Icon(Icons.Rounded.Tune, stringResource(R.string.action_notification_settings), tint = Ink)
            }
        }
        // Only when Android is blocking Kimi: say so plainly, with the one button that fixes it.
        if (!allowed) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Overlay).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsOff, null, Modifier.size(20.dp), tint = Ink)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.reminders_blocked), Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = ::openSystemSettings) { Text(stringResource(R.string.action_turn_on), fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            habits.forEach { habit ->
                ReminderRow(habit, format, busy = switching == habit.id,
                    onOpen = { editingId = habit.id; minutes = habit.reminderMinutes; count = habit.reminderCount }) { on ->
                    if (on && !Reminders.allowed(context) && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    switching = habit.id
                    // Switching on starts at 9 am, the same default the editor uses; tap the row to change it.
                    onSave(habit.copy(reminderMinutes = if (on) normalizedReminderStart(9 * 60, habit.reminderCount) else null)) { switching = null }
                }
            }
        }
        if (habits.isEmpty()) Text(stringResource(R.string.reminders_empty), color = Quiet, fontSize = 12.sp)
        else {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.reminders_quiet_note), color = Quiet, fontSize = 11.sp)
        }
    }
    habits.find { it.id == editingId }?.let { habit ->
        AlertDialog(onDismissRequest = { if (!saving) editingId = null }, title = { Text(habit.name) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { ReminderPicker(minutes, count) { nextMinutes, nextCount -> minutes = nextMinutes; count = nextCount } } },
            confirmButton = { TextButton(enabled = !saving, onClick = {
                saving = true
                onSave(habit.copy(reminderMinutes = minutes, reminderCount = count)) { saved ->
                    saving = false
                    if (saved) editingId = null
                }
            }) { Text(stringResource(R.string.action_save_reminder)) } },
            dismissButton = { TextButton(onClick = { saving = false; editingId = null }) { Text(stringResource(R.string.action_cancel)) } })
    }
}
