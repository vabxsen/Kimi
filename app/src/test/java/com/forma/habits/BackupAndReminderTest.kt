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
        val edited = habit.copy(weekdays = true, reminderMinutes = 540).editedFrom(habit, monday.plusDays(2))
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
            BackupCodec.encode(HabitState(listOf(habit), mapOf(monday.toString() to setOf("unknown")))),
            BackupCodec.encode(HabitState(listOf(habit))).replace("\"version\": 2", "\"version\": 99"),
            BackupCodec.encode(HabitState(journal = listOf(Reflection(monday, 9, "Invalid"))))
        ).forEach { assertTrue("Accepted invalid input: $it", runCatching { BackupCodec.decode(it) }.isFailure) }
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
