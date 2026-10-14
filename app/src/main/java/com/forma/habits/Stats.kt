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

/**
 * The numbers behind the weekly story, over the seven days up to and including [today]: every tick,
 * the days with everything done, the best day, the longest streak still going and the habit done
 * most often. [bestDay], [streakHabit] and [topHabit] are null when there is nothing to show.
 */
@Immutable
data class WeekStory(
    val days: List<DayScore>,
    val ticks: Int,
    val perfectDays: Int,
    val bestDay: DayScore?,
    val streakHabit: String?,
    val streak: Int,
    val topHabit: String?,
    val topCount: Int
)

fun HabitState.weekStory(today: LocalDate): WeekStory {
    val dates = (6L downTo 0L).map { today.minusDays(it) }
    val days = dates.map { DayScore(it, percent(it), isRestDay(it)) }
    val best = days.filter { !it.rest && it.percent > 0 }.maxWithOrNull(compareBy<DayScore>({ it.percent }, { it.date }))
    val streaks = habitStats(today).streaks
    val longest = habits.filter { (streaks[it.id] ?: 0) > 0 }.maxByOrNull { streaks[it.id] ?: 0 }
    val counts = habits.associateWith { h -> dates.count { done(h.id, it) } }
    val top = counts.filterValues { it > 0 }.maxByOrNull { it.value }
    return WeekStory(
        days = days,
        ticks = dates.sumOf { date -> habits.count { done(it.id, date) } },
        perfectDays = days.count { !it.rest && it.percent == 100 },
        bestDay = best,
        streakHabit = longest?.name,
        streak = longest?.let { streaks[it.id] } ?: 0,
        topHabit = top?.key?.name,
        topCount = top?.value ?: 0
    )
}

/** How many times [habit] was done in the 30 days up to and including [today]: what its plant grows on. */
fun HabitState.monthOfDone(habit: Habit, today: LocalDate): Int = (0L..29L).count { done(habit.id, today.minusDays(it)) }

/** A habit's plant: 0 seed, 1 sprout, 2 seedling, 3 bud, 4 in bloom (20 or more times in a month). */
fun plantStage(doneInMonth: Int): Int = when {
    doneInMonth <= 0 -> 0
    doneInMonth <= 3 -> 1
    doneInMonth <= 9 -> 2
    doneInMonth <= 19 -> 3
    else -> 4
}

/** Missed yesterday and not done yet today: its plant droops until it is done again. */
fun HabitState.wilting(habit: Habit, today: LocalDate): Boolean {
    val yesterday = today.minusDays(1)
    return habit.isDue(yesterday) && !done(habit.id, yesterday) && !done(habit.id, today)
}

/** From this hour on, a streak with nothing ticked yet today is at risk. */
const val StreakRiskHour = 20

/**
 * True on an evening when a streak is alive, today has something scheduled and nothing is ticked
 * yet: the moment Today's flame runs low to ask for a tick before midnight.
 */
fun HabitState.streakAtRisk(today: LocalDate, hour: Int, bestStreak: Int): Boolean =
    hour >= StreakRiskHour && bestStreak > 0 && !isRestDay(today) && count(today) == 0

fun HabitState.habitStats(today: LocalDate): HabitStats {
    val streaks = habits.associate { it.id to streak(it, today) }
    return HabitStats(streaks, streaks.values.maxOrNull() ?: 0)
}

/**
 * Whether [habit] on [date] belongs in a consistency total. Today is still open: a habit not yet
 * ticked is not a miss, so it only counts once done - otherwise every score dips each morning.
 */
private fun HabitState.counted(habit: Habit, date: LocalDate, today: LocalDate) =
    habit.isDue(date) && (date != today || done(habit.id, date))

fun HabitState.insightSummary(today: LocalDate): InsightSummary {
    val dates = (6L downTo 0L).map { today.minusDays(it) }
    val week = dates.map { DayScore(it, percent(it), isRestDay(it)) }
    val possible = dates.sumOf { date -> habits.count { counted(it, date, today) } }
    val wins = dates.sumOf { count(it) }
    val month = (0L..29L).map { today.minusDays(it) }
    val consistency = habits.associate { habit ->
        val scheduled = month.filter { counted(habit, it, today) }
        habit.id to if (scheduled.isEmpty()) 0 else scheduled.count { done(habit.id, it) } * 100 / scheduled.size
    }
    return InsightSummary(week, wins, if (possible == 0) 0 else wins * 100 / possible, consistency)
}

@Composable fun rememberHabitStats(state: HabitState, today: LocalDate): HabitStats =
    remember(state, today) { state.habitStats(today) }

@Composable fun rememberInsights(state: HabitState, today: LocalDate): InsightSummary =
    remember(state, today) { state.insightSummary(today) }
