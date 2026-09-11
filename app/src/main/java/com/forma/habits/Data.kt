package com.forma.habits

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

/** Stored daypart keys. They live in backups, so they stay stable English while the UI shows a localized label. */
val Dayparts = listOf("Morning", "Afternoon", "Evening", "Anytime")
const val MAX_DAILY_REMINDERS = 24
const val MIN_REMINDER_INTERVAL_MINUTES = 15

data class ScheduleChange(val from: LocalDate, val weekdays: Boolean)
data class Habit(val id: String = UUID.randomUUID().toString(), val name: String, val goal: String,
    val icon: Int = 0, val color: Int = 0, val time: String = "Morning", val weekdays: Boolean = false,
    val created: LocalDate = LocalDate.now(), val reminderMinutes: Int? = null, val reminderCount: Int = 1,
    val schedule: List<ScheduleChange> = emptyList())
data class Reflection(val date: LocalDate, val mood: Int, val text: String)

/**
 * Per-entry revisions used by multi-device sync. A key lives in exactly one of each
 * updated/deleted pair, so removals and undone check-ins cannot be resurrected by an older device.
 */
data class SyncMetadata(
    val habitUpdates: Map<String, Long> = emptyMap(),
    val habitDeletions: Map<String, Long> = emptyMap(),
    val checkUpdates: Map<String, Long> = emptyMap(),
    val checkDeletions: Map<String, Long> = emptyMap(),
    val reflectionUpdates: Map<String, Long> = emptyMap(),
    val reflectionDeletions: Map<String, Long> = emptyMap()
)

data class HabitState(val habits: List<Habit> = emptyList(), val checks: Map<String, Set<String>> = emptyMap(),
    val journal: List<Reflection> = emptyList(), val name: String = "Alex", val demo: Boolean = false,
    val onboarded: Boolean = true, val sync: SyncMetadata = SyncMetadata())

internal fun checkRevisionKey(date: String, habitId: String) = "$date/$habitId"

fun Habit.isDue(date: LocalDate): Boolean {
    val weekdaysOnly = schedule.lastOrNull { !it.from.isAfter(date) }?.weekdays ?: weekdays
    return !date.isBefore(created) && (!weekdaysOnly || date.dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
}
fun HabitState.due(date: LocalDate) = habits.filter { it.isDue(date) }
fun HabitState.done(id: String, date: LocalDate) = checks[date.toString()]?.contains(id) == true
fun HabitState.count(date: LocalDate) = due(date).count { done(it.id, date) }
fun HabitState.percent(date: LocalDate): Int = if (due(date).isEmpty()) 0 else count(date) * 100 / due(date).size
/** No habits fall on this day, so there is nothing to be behind on. */
fun HabitState.isRestDay(date: LocalDate) = due(date).isEmpty()
fun HabitState.streak(habit: Habit, today: LocalDate = LocalDate.now()): Int {
    var date = if (done(habit.id, today)) today else today.minusDays(1)
    var count = 0
    while (!date.isBefore(habit.created)) {
        if (habit.isDue(date)) { if (!done(habit.id, date)) break; count++ }
        date = date.minusDays(1)
    }
    return count
}

/** Schedule edits take effect today without rewriting the days already tracked. */
fun Habit.editedFrom(previous: Habit, today: LocalDate): Habit {
    val history = if (weekdays == previous.weekdays) previous.schedule else {
        val baseline = previous.schedule.ifEmpty { listOf(ScheduleChange(previous.created, previous.weekdays)) }
        baseline.filter { it.from.isBefore(today) } + ScheduleChange(today, weekdays)
    }
    return copy(created = previous.created, schedule = history)
}

fun HabitState.checked(id: String, date: LocalDate, completed: Boolean): HabitState {
    val habit = habits.find { it.id == id } ?: return this
    if (date.isAfter(LocalDate.now()) || !habit.isDue(date)) return this
    val ids = checks[date.toString()].orEmpty()
    val next = if (completed) ids + id else ids - id
    return copy(checks = if (next.isEmpty()) checks - date.toString() else checks + (date.toString() to next))
}

/**
 * Produces distinct local wall-clock slots from the chosen start through the rest of the day.
 * Keeping at least fifteen minutes between slots avoids bursty reminders and remains friendly to
 * Android's idle-mode alarm batching, even at the user-selected maximum of 24 reminders.
 */
fun reminderStartLimit(count: Int): Int = if (count <= 1) 1439
    else 1440 - count.coerceIn(1, MAX_DAILY_REMINDERS) * MIN_REMINDER_INTERVAL_MINUTES

fun normalizedReminderStart(minutes: Int, count: Int): Int =
    minutes.coerceIn(0, reminderStartLimit(count))

fun dailyReminderMinutes(start: Int?, requestedCount: Int): List<Int> {
    start ?: return emptyList()
    val count = requestedCount.coerceIn(1, MAX_DAILY_REMINDERS)
    val span = 1440 - start
    return List(count) { index -> start + index * span / count }
}

fun Habit.dailyReminderMinutes(): List<Int> = dailyReminderMinutes(reminderMinutes, reminderCount)

/** Local wall-clock times follow timezone changes and DST; completed days are skipped. */
fun nextReminder(habit: Habit, state: HabitState, now: ZonedDateTime): ZonedDateTime? {
    val minutes = habit.dailyReminderMinutes()
    if (minutes.isEmpty()) return null
    for (offset in 0L..8L) {
        val date = now.toLocalDate().plusDays(offset)
        if (!habit.isDue(date) || state.done(habit.id, date)) continue
        minutes.forEach { slot ->
            val trigger = date.atTime(slot / 60, slot % 60).atZone(now.zone)
            if (trigger.isAfter(now)) return trigger
        }
    }
    return null
}

/**
 * Seed habits become the user's own data the moment they are created, so the caller passes in
 * copy from resources; the defaults keep this callable from JVM tests without a Context.
 */
fun starterHabits(
    today: LocalDate = LocalDate.now(),
    names: List<String> = listOf("A little headspace", "Sip, sip, hooray!", "One more chapter", "Get your happy steps", "Less scroll, more soul"),
    goals: List<String> = listOf("Meditate for 10 minutes", "Drink 8 glasses of water", "Read for 20 minutes", "Walk for 30 minutes", "Unplug 30 minutes before bed")
) = listOf(
    Habit("breathe", names[0], goals[0], 0, 0),
    Habit("water", names[1], goals[1], 1, 1, "Anytime"),
    Habit("read", names[2], goals[2], 2, 2, "Evening"),
    Habit("move", names[3], goals[3], 3, 3, "Afternoon"),
    Habit("sleep", names[4], goals[4], 4, 4, "Evening")
).map { it.copy(created = today) }
