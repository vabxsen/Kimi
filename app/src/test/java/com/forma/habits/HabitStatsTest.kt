package com.forma.habits

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HabitStatsTest {
    private val monday = LocalDate.of(2026, 9, 7)
    @Test fun creationDateDoesNotCountEarlierDaysAsMissed() {
        val habit = Habit(id = "a", name = "Read", goal = "10 minutes", created = monday)
        val state = HabitState(habits = listOf(habit))
        assertTrue(state.due(monday.minusDays(1)).isEmpty())
        assertEquals(1, state.due(monday).size)
    }
    @Test fun weekdayStreakSkipsTheWeekend() {
        val friday = monday.minusDays(3)
        val habit = Habit(id = "a", name = "Read", goal = "10 minutes", weekdays = true, created = friday)
        val state = HabitState(listOf(habit), mapOf(friday.toString() to setOf("a"), monday.toString() to setOf("a")))
        assertEquals(2, state.streak(habit, monday))
        assertFalse(habit.isDue(monday.minusDays(1)))
    }
    @Test fun missedScheduledDayBreaksStreak() {
        val habit = Habit(id = "a", name = "Read", goal = "10 minutes", created = monday)
        val state = HabitState(listOf(habit), mapOf(monday.toString() to setOf("a")))
        assertEquals(0, state.streak(habit, monday.plusDays(2)))
    }
}
