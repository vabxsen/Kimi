package com.forma.habits

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

object Reminders {
    const val CHANNEL = "little_rituals"
    const val FIRE = "com.forma.habits.REMIND"
    const val DONE = "com.forma.habits.DONE"
    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_description)
            })
    }
    fun allowed(context: Context): Boolean {
        createChannel(context)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL).importance != NotificationManager.IMPORTANCE_NONE
    }
    private fun pending(context: Context, id: String, action: String, date: String = "", owner: String = AccountSession.owner, planned: String = ""): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java).apply {
            this.action = action; data = Uri.parse("kimi://habit/$id/${if (action == DONE) "done/$date" else "remind"}").buildUpon().appendQueryParameter("owner", owner).build()
            putExtra("habitId", id); putExtra("date", date); putExtra("owner", owner); putExtra("planned", planned)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @Synchronized fun reschedule(context: Context, state: HabitState, force: Boolean = false, owner: String = AccountSession.owner) {
        if (owner != AccountSession.owner) return
        createChannel(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences("kimi_alarms", 0)
        val old = prefs.getStringSet("ids", emptySet()).orEmpty().toSet()
        val previousOwner = prefs.getString("owner", "").orEmpty()
        val switched = previousOwner != owner
        if (switched) {
            old.forEach { alarms.cancel(pending(context, it, FIRE, owner = previousOwner)) }
            NotificationManagerCompat.from(context).cancelAll()
            prefs.edit().clear().apply()
        }
        val active = state.habits.filter { it.dailyReminderMinutes().isNotEmpty() }.map { it.id }.toSet()
        val editor = prefs.edit().putStringSet("ids", active).putString("owner", owner)
        val now = ZonedDateTime.now()
        val enabled = allowed(context)
        (old + active).forEach { id ->
            val habit = state.habits.find { it.id == id }
            val previous = prefs.getString("planned:$id", null)?.let { runCatching { ZonedDateTime.parse(it) }.getOrNull() }
            var next = habit?.let { nextReminder(it, state, now) }
            // An inexact alarm may still be waiting after its requested time. Do not cancel
            // that pending delivery just because the user opened the app in the meantime.
            if (habit != null && previous != null && previous.toLocalDate() == now.toLocalDate() &&
                previous.zone == now.zone && prefs.getInt("minute:$id", -1) == habit.reminderMinutes &&
                prefs.getInt("count:$id", 1) == habit.reminderCount &&
                habit.isDue(now.toLocalDate()) && !state.done(id, now.toLocalDate()) &&
                prefs.getString("delivered:$id", null) != previous.toString()) next = previous
            if (!enabled || id !in active) next = null
            if (force || switched || next != previous) {
                alarms.cancel(pending(context, id, FIRE, owner = owner))
                if (next != null) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending(context, id, FIRE, next.toLocalDate().toString(), owner, next.toString()))
            }
            if (next == null) editor.remove("planned:$id") else editor.putString("planned:$id", next.toString())
            editor.putInt("minute:$id", habit?.reminderMinutes ?: -1)
            editor.putInt("count:$id", habit?.reminderCount ?: 1)
            if (id !in active || state.done(id, now.toLocalDate())) NotificationManagerCompat.from(context).cancel(id, 1)
            if (id !in active) editor.remove("delivered:$id").remove("minute:$id").remove("count:$id")
        }
        editor.apply()
    }

    @Suppress("MissingPermission")
    fun show(context: Context, habit: Habit, date: LocalDate, owner: String = AccountSession.owner) {
        if (owner != AccountSession.owner || !allowed(context)) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(habit.name)
            .setContentText(habit.goal).setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_body, habit.goal)))
            .setContentIntent(open).setAutoCancel(true).setColor(0xFF7255D9.toInt())
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(R.drawable.ic_notification, context.getString(R.string.action_mark_complete), pending(context, habit.id, DONE, date.toString(), owner))
            .build()
        NotificationManagerCompat.from(context).notify(habit.id, 1, notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val owner = AccountSession.owner
                if (intent.action in listOf(Reminders.FIRE, Reminders.DONE) && intent.getStringExtra("owner").orEmpty() != owner) return@launch
                val store = HabitStore.get(context, owner)
                val id = intent.getStringExtra("habitId")
                val date = intent.getStringExtra("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val planned = intent.getStringExtra("planned")?.takeIf { it.isNotEmpty() }
                    ?.let { runCatching { ZonedDateTime.parse(it) }.getOrNull() }
                when (intent.action) {
                    Reminders.DONE -> if (id != null && date != null) {
                        store.update { it.checked(id, date, true) }
                        NotificationManagerCompat.from(context).cancel(id, 1)
                    }
                    Reminders.FIRE -> {
                        if (id != null && date != null) context.getSharedPreferences("kimi_alarms", 0).edit()
                            .putString("delivered:$id", planned?.toString() ?: date.toString()).apply()
                        val state = store.state.value
                        val habit = state.habits.find { it.id == id }
                        val plannedMinute = planned?.let { it.hour * 60 + it.minute }
                        if (habit != null && date == LocalDate.now() && habit.reminderMinutes != null &&
                            (plannedMinute == null || plannedMinute in habit.dailyReminderMinutes()) &&
                            habit.isDue(date) && !state.done(habit.id, date)) {
                            Reminders.show(context, habit, date, owner)
                        }
                    }
                }
                Reminders.reschedule(context, store.state.value, force = intent.action !in listOf(Reminders.FIRE, Reminders.DONE), owner = owner)
            } catch (e: Exception) {
                android.util.Log.e("KimiReminders", "Reminder could not be processed", e)
            } finally { result.finish() }
        }
    }
}
