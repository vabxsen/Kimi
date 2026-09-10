package com.forma.habits

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * The screens read streaks and consistency from a snapshot instead of recomputing them per row.
 * These tests pin the snapshot to the same answers the direct calculations give.
 */
class DerivedStatsTest {
    private val monday = LocalDate.of(2026, 9, 7)
    private val wednesday = monday.plusDays(2)

    private fun sample(): HabitState {
        val daily = Habit("daily", "Read", "Ten pages", created = monday)
        val weekday = Habit("weekday", "Stretch", "Five minutes", weekdays = true, created = monday)
        return HabitState(
            habits = listOf(daily, weekday),
            checks = mapOf(
                monday.toString() to setOf("daily", "weekday"),
                monday.plusDays(1).toString() to setOf("daily"),
                wednesday.toString() to setOf("daily", "weekday")
            )
        )
    }

    @Test fun snapshotStreaksMatchDirectCalculation() {
        val state = sample()
        val stats = state.habitStats(wednesday)
        state.habits.forEach { habit ->
            assertEquals("streak for ${habit.id}", state.streak(habit, wednesday), stats.streaks[habit.id])
        }
        assertEquals(state.habits.maxOf { state.streak(it, wednesday) }, stats.bestStreak)
    }

    @Test fun bestStreakIsZeroWithoutHabits() {
        val stats = HabitState().habitStats(wednesday)
        assertEquals(0, stats.bestStreak)
        assertTrue(stats.streaks.isEmpty())
    }

    @Test fun insightWeekCoversSevenDaysEndingToday() {
        val summary = sample().insightSummary(wednesday)
        assertEquals(7, summary.week.size)
        assertEquals(wednesday.minusDays(6), summary.week.first().first)
        assertEquals(wednesday, summary.week.last().first)
        summary.week.forEach { (date, percent) -> assertEquals(sample().percent(date), percent) }
    }

    @Test fun insightConsistencyIgnoresUnscheduledDays() {
        val state = sample()
        val summary = state.insightSummary(wednesday)
        // The weekday habit is due Mon-Wed only, and was completed on two of those three days.
        assertEquals(66, summary.consistency["weekday"])
        assertEquals(100, summary.consistency["daily"])
    }

    @Test fun weekPercentIgnoresDaysBeforeTheHabitExisted() {
        val summary = sample().insightSummary(wednesday)
        // Both habits were created on Monday, so only Mon-Wed count: six possible check-ins.
        // Five were made - Tuesday's weekday habit was left undone - which is 83%, not 100%.
        assertEquals(5, summary.wins)
        assertEquals(83, summary.percent)
        assertEquals(0, summary.week.first().second)
    }

    @Test fun restDayIsNotAFailedDay() {
        val weekday = Habit("weekday", "Stretch", "Five minutes", weekdays = true, created = monday)
        val state = HabitState(listOf(weekday))
        val saturday = monday.plusDays(5)
        assertTrue(state.isRestDay(saturday))
        assertEquals(0, state.percent(saturday))
        assertFalse(state.isRestDay(monday))
    }
}
