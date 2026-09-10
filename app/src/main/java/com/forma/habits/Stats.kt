package com.forma.habits

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import java.time.LocalDate

/**
 * Derived numbers for the screens.
 *
 * A streak walks back one day at a time to the habit's creation date, so recomputing it inline
 * during composition costs more every day a habit survives. These snapshots are calculated once
 * per (state, today) pair and reused for every row that needs them.
 */
@Immutable
data class HabitStats(val streaks: Map<String, Int>, val bestStreak: Int)

/** One column of the seven-day chart. A rest day is not a zero: nothing was asked of you. */
@Immutable
data class DayScore(val date: LocalDate, val percent: Int, val rest: Boolean)

@Immutable
data class InsightSummary(
    val week: List<DayScore>,
    val wins: Int,
    val percent: Int,
    val consistency: Map<String, Int>
)

fun HabitState.habitStats(today: LocalDate): HabitStats {
    val streaks = habits.associate { it.id to streak(it, today) }
    return HabitStats(streaks, streaks.values.maxOrNull() ?: 0)
}

fun HabitState.insightSummary(today: LocalDate): InsightSummary {
    val dates = (6L downTo 0L).map { today.minusDays(it) }
    val week = dates.map { DayScore(it, percent(it), isRestDay(it)) }
    val possible = dates.sumOf { due(it).size }
    val wins = dates.sumOf { count(it) }
    val month = (0L..29L).map { today.minusDays(it) }
    val consistency = habits.associate { habit ->
        val scheduled = month.filter { habit.isDue(it) }
        habit.id to if (scheduled.isEmpty()) 0 else scheduled.count { done(habit.id, it) } * 100 / scheduled.size
    }
    return InsightSummary(week, wins, if (possible == 0) 0 else wins * 100 / possible, consistency)
}

@Composable fun rememberHabitStats(state: HabitState, today: LocalDate): HabitStats =
    remember(state, today) { state.habitStats(today) }

@Composable fun rememberInsights(state: HabitState, today: LocalDate): InsightSummary =
    remember(state, today) { state.insightSummary(today) }
