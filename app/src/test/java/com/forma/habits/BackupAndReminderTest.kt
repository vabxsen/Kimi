package com.forma.habits

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class BackupAndReminderTest {
    private val monday = LocalDate.of(2026, 9, 7)
    private val habit = Habit("reading", "Read", "Ten pages", created = monday)
    @Test fun backupRoundTripRetainsAllDataAndHistory() {
        val edited = habit.copy(icon = 8, weekdays = true, reminderMinutes = 540, reminderCount = 8).editedFrom(habit, monday.plusDays(2))
        val state = HabitState(listOf(edited), mapOf(monday.toString() to setOf(habit.id)),
            listOf(Reflection(monday, 4, "A lovely day 🌻\nSecond line")), "Vaibhav")
        assertEquals(state, BackupCodec.decode(BackupCodec.encode(state)))
    }
    @Test fun legacyPreviewBackupsRemainReadable() {
        val raw = """{"name":"Alex","demo":true,"habits":[],"checks":{},"journal":[]}"""
        assertTrue(BackupCodec.decode(raw).demo)
        assertTrue(BackupCodec.decode(raw).onboarded)
    }
    @Test fun malformedBackupIsRejected() {
        listOf("{}", "garbage", BackupCodec.encode(HabitState(listOf(habit, habit))),
            BackupCodec.encode(HabitState(listOf(habit.copy(color = 100)))) ,
            BackupCodec.encode(HabitState(listOf(habit.copy(icon = 9)))),
            BackupCodec.encode(HabitState(listOf(habit), mapOf(monday.toString() to setOf("unknown")))),
            BackupCodec.encode(HabitState(listOf(habit))).replace("\"version\": 3", "\"version\": 99"),
            BackupCodec.encode(HabitState(journal = listOf(Reflection(monday, 9, "Invalid")))),
            BackupCodec.encode(HabitState(listOf(habit.copy(reminderMinutes = 540, reminderCount = 25)))),
            BackupCodec.encode(HabitState(listOf(habit.copy(reminderMinutes = 1439, reminderCount = 24)))),
            BackupCodec.encode(HabitState()).replace("\"habitUpdates\": {}", "\"habitUpdates\": {\"ghost\": 1}")
        ).forEach { assertTrue("Accepted invalid input: $it", runCatching { BackupCodec.decode(it) }.isFailure) }
    }
    @Test fun backupRoundTripRetainsSyncTombstones() {
        val original = HabitState(listOf(habit), journal = listOf(Reflection(monday, 2, "Keep me")))
            .recordChangesFrom(HabitState(), 100)
        val removed = original.copy(habits = emptyList(), journal = emptyList()).recordChangesFrom(original, 200)
        assertEquals(removed, BackupCodec.decode(BackupCodec.encode(removed)))
        assertEquals(200L, removed.sync.habitDeletions[habit.id])
        assertEquals(200L, removed.sync.reflectionDeletions[monday.toString()])
    }
    @Test fun editingSchedulePreservesEarlierWeekends() {
        val friday = monday.minusDays(3)
        val old = habit.copy(created = friday)
        val changed = old.copy(weekdays = true).editedFrom(old, monday)
        assertTrue(changed.isDue(monday.minusDays(1)))
        assertFalse(changed.isDue(monday.plusDays(5)))
        assertEquals(friday, changed.created)
    }
    @Test fun repeatedSameDayScheduleEditsHaveOneRevision() {
        val first = habit.copy(weekdays = true).editedFrom(habit, monday.plusDays(1))
        val second = first.copy(weekdays = false).editedFrom(first, monday.plusDays(1))
        assertEquals(2, second.schedule.size)
        BackupCodec.validateHabit(second)
    }
    @Test fun starterHabitsHaveNoInventedHistory() {
        val state = HabitState(starterHabits(monday))
        assertEquals(0, state.count(monday))
        assertEquals(0, state.percent(monday))
        assertEquals(0, state.due(monday.minusDays(1)).size)
    }
    @Test fun reminderSkipsCompletedDayAndWeekend() {
        val h = habit.copy(weekdays = true, reminderMinutes = 540)
        val friday = monday.plusDays(4)
        val state = HabitState(listOf(h), mapOf(friday.toString() to setOf(h.id)))
        val now = friday.atTime(8, 0).atZone(ZoneId.of("Asia/Kolkata"))
        assertEquals(friday.plusDays(3).atTime(9, 0).atZone(now.zone), nextReminder(h, state, now))
    }
    @Test fun reminderAfterChosenTimeMovesToTomorrow() {
        val h = habit.copy(reminderMinutes = 540)
        val now = monday.atTime(9, 0).atZone(ZoneId.of("Asia/Kolkata"))
        assertEquals(monday.plusDays(1), nextReminder(h, HabitState(listOf(h)), now)?.toLocalDate())
        assertNull(nextReminder(h.copy(reminderMinutes = null), HabitState(), now))
    }
    @Test fun multipleDailyRemindersAreDistinctAndAdvanceWithinTheSameDay() {
        val h = habit.copy(reminderMinutes = 540, reminderCount = 3)
        assertEquals(listOf(540, 840, 1140), h.dailyReminderMinutes())
        val state = HabitState(listOf(h))
        val zone = ZoneId.of("Asia/Kolkata")
        assertEquals(monday.atTime(14, 0).atZone(zone), nextReminder(h, state, monday.atTime(10, 0).atZone(zone)))
        assertEquals(monday.plusDays(1).atTime(9, 0).atZone(zone), nextReminder(h, state, monday.atTime(20, 0).atZone(zone)))
    }
    @Test fun maximumReminderCountKeepsSafeSpacingAndNormalizesLateStarts() {
        val start = normalizedReminderStart(23 * 60 + 59, MAX_DAILY_REMINDERS)
        val slots = dailyReminderMinutes(start, MAX_DAILY_REMINDERS)
        assertEquals(18 * 60, start)
        assertEquals(MAX_DAILY_REMINDERS, slots.size)
        assertEquals(slots.size, slots.distinct().size)
        assertTrue(slots.zipWithNext().all { (a, b) -> b - a >= MIN_REMINDER_INTERVAL_MINUTES })
        assertTrue(slots.last() <= 1439)
    }
    @Test fun daylightSavingGapResolvesToValidLocalTime() {
        val h = habit.copy(created = LocalDate.of(2026, 1, 1), reminderMinutes = 150)
        val now = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, ZoneId.of("America/New_York"))
        val reminder = nextReminder(h, HabitState(listOf(h)), now)!!
        assertTrue(reminder.isAfter(now))
        assertEquals(3, reminder.hour)
    }
    @Test fun notificationCompletionIsIdempotentAndRejectsFutureDays() {
        val date = LocalDate.now()
        val h = habit.copy(created = date)
        val state = HabitState(listOf(h))
        val checked = state.checked(h.id, date, true)
        assertEquals(checked, checked.checked(h.id, date, true))
        assertEquals(state, state.checked(h.id, date.plusDays(1), true))
        assertEquals(state, state.checked("missing", date, true))
        assertEquals(state, checked.checked(h.id, date, false))
    }
}
