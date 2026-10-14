package com.forma.habits

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fourth round of motion: the rules behind each effect, checked without a screen. */
class MotionRoundFourTest {
    @Test fun flowerGetsDizzyOnlyFromAQuickBurstOfTaps() {
        val burst = TapBurst()
        // Slow, separate pokes never add up.
        assertFalse((0 until 6).map { burst.tap(it * 2_000L) }.any { it })
        val quick = TapBurst()
        assertFalse(quick.tap(0)); assertFalse(quick.tap(200)); assertFalse(quick.tap(400))
        assertTrue(quick.tap(600))
        // The count starts afresh after a dizzy spell.
        assertFalse(quick.tap(700))
    }

    private val today = java.time.LocalDate.of(2026, 10, 14)
    private val stretch = Habit(id = "s", name = "Stretch", goal = "", created = today.minusDays(10))
    private val alive = HabitState(habits = listOf(stretch), checks = mapOf(today.minusDays(1).toString() to setOf("s")))

    @Test fun streakIsAtRiskOnlyInTheEveningWithNothingTicked() {
        val streak = alive.habitStats(today).bestStreak
        assertTrue(streak > 0)
        assertFalse("afternoon", alive.streakAtRisk(today, 19, streak))
        assertTrue("evening", alive.streakAtRisk(today, 20, streak))
        assertTrue("late", alive.streakAtRisk(today, 23, streak))
    }

    @Test fun oneTickTonightTakesTheRiskAway() {
        val ticked = alive.copy(checks = alive.checks + (today.toString() to setOf("s")))
        assertFalse(ticked.streakAtRisk(today, 22, ticked.habitStats(today).bestStreak))
    }

    @Test fun noStreakOrNothingScheduledIsNeverAtRisk() {
        val fresh = HabitState(habits = listOf(stretch))
        assertFalse(fresh.streakAtRisk(today, 22, fresh.habitStats(today).bestStreak))
        val restDay = HabitState(habits = listOf(stretch.copy(created = today.plusDays(1))))
        assertFalse(restDay.streakAtRisk(today, 22, 5))
    }

    @Test fun everyMonthBelongsToTheRightSeason() {
        val expected = mapOf(
            Season.Winter to listOf(12, 1, 2), Season.Spring to listOf(3, 4, 5),
            Season.Summer to listOf(6, 7, 8), Season.Autumn to listOf(9, 10, 11))
        expected.forEach { (season, months) -> months.forEach { assertTrue("month $it", seasonFor(it) == season) } }
    }

    @Test fun plantGrowsThroughItsStagesWithAMonthOfDoing() {
        assertTrue(plantStage(0) == 0)
        assertTrue(plantStage(1) == 1 && plantStage(3) == 1)
        assertTrue(plantStage(4) == 2 && plantStage(9) == 2)
        assertTrue(plantStage(10) == 3 && plantStage(19) == 3)
        assertTrue(plantStage(20) == 4 && plantStage(30) == 4)
    }

    @Test fun monthOfDoneCountsOnlyTheLastThirtyDays() {
        val checks = (0L..40L).associate { today.minusDays(it).toString() to setOf("s") }
        val state = HabitState(habits = listOf(stretch.copy(created = today.minusDays(60))), checks = checks)
        assertTrue(state.monthOfDone(state.habits.single(), today) == 30)
    }

    @Test fun plantDroopsAfterAMissUntilDoneAgain() {
        val missed = HabitState(habits = listOf(stretch), checks = mapOf(today.minusDays(2).toString() to setOf("s")))
        assertTrue(missed.wilting(stretch, today))
        val back = missed.copy(checks = missed.checks + (today.toString() to setOf("s")))
        assertFalse(back.wilting(stretch, today))
        // A habit that only started today has nothing to have missed.
        val brandNew = HabitState(habits = listOf(stretch.copy(created = today)))
        assertFalse(brandNew.wilting(brandNew.habits.single(), today))
        // Nor does a weekday habit after a weekend.
        val monday = java.time.LocalDate.of(2026, 10, 12)
        val weekdays = stretch.copy(weekdays = true, created = monday.minusDays(20))
        assertFalse(HabitState(habits = listOf(weekdays)).wilting(weekdays, monday))
    }

    @Test fun weekStoryTellsTheWeekAsItWas() {
        val read = Habit(id = "r", name = "Read", goal = "", created = today.minusDays(30))
        val walk = Habit(id = "w", name = "Walk", goal = "", created = today.minusDays(30))
        // Read every day for twelve days; Walk on three days this week.
        val checks: MutableMap<String, Set<String>> = (0L..11L).associate { today.minusDays(it).toString() to setOf("r") }.toMutableMap()
        listOf(1L, 3L, 5L).forEach { checks[today.minusDays(it).toString()] = checks.getValue(today.minusDays(it).toString()) + "w" }
        // Two weeks ago, outside this story.
        checks[today.minusDays(9).toString()] = setOf("r", "w")
        val story = HabitState(habits = listOf(read, walk), checks = checks).weekStory(today)
        assertTrue(story.days.size == 7 && story.days.last().date == today)
        assertTrue("ticks ${story.ticks}", story.ticks == 7 + 3)
        assertTrue("perfect ${story.perfectDays}", story.perfectDays == 3)
        // The most recent of the perfect days wins a tie.
        assertTrue(story.bestDay?.date == today.minusDays(1) && story.bestDay.percent == 100)
        assertTrue(story.streakHabit == "Read" && story.streak == 12)
        assertTrue(story.topHabit == "Read" && story.topCount == 7)
    }

    @Test fun anEmptyWeekHasNothingToBoastAbout() {
        val story = HabitState(habits = listOf(stretch)).weekStory(today)
        assertTrue(story.ticks == 0 && story.perfectDays == 0)
        assertTrue(story.bestDay == null && story.streakHabit == null && story.topHabit == null)
    }

    @Test fun aPauseInTheMiddleOfABurstStartsItOver() {
        val burst = TapBurst()
        burst.tap(0); burst.tap(300); burst.tap(600)
        assertFalse(burst.tap(2_500))
        assertFalse(burst.tap(2_700)); assertFalse(burst.tap(2_900))
        assertTrue(burst.tap(3_100))
    }
}
