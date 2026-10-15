package com.forma.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The garden game is worked out from history alone, so every rule can be checked here exactly. */
class GameTest {
    // Monday 5 to Sunday 11 October 2026 is a full week; Wednesday the 14th is "today".
    private val monday = LocalDate.of(2026, 10, 5)
    private val today = LocalDate.of(2026, 10, 14)
    private fun d(day: Int) = LocalDate.of(2026, 10, day)
    private val stretch = Habit(id = "a", name = "Stretch", goal = "", created = monday)
    private fun state(vararg days: Int, habit: Habit = stretch) =
        HabitState(habits = listOf(habit), checks = days.associate { d(it).toString() to setOf(habit.id) })

    /** Quest petals for a week, given which kinds of quest were finished in it. */
    private fun questPetals(s: HabitState, weekStart: LocalDate, finished: Set<QuestKind>) =
        s.weekQuests(weekStart).count { it.kind in finished } * QuestPetals

    @Test fun levelsNeedMorePetalsEachTime() {
        assertEquals(0, petalsForLevel(1)); assertEquals(40, petalsForLevel(2))
        assertEquals(110, petalsForLevel(3)); assertEquals(210, petalsForLevel(4))
        assertEquals(1, levelFor(0)); assertEquals(1, levelFor(39)); assertEquals(2, levelFor(40))
        assertEquals(2, levelFor(109)); assertEquals(3, levelFor(110)); assertEquals(10, levelFor(petalsForLevel(10)))
        assertEquals(1440, petalsForLevel(10))
    }

    @Test fun aPerfectWeekEarnsAFreezeThatCoversTheNextMiss() {
        // Every day of the first week, then Monday, a miss on Tuesday, and today done.
        val s = state(5, 6, 7, 8, 9, 10, 11, 12, 14)
        val game = s.game(today)
        assertEquals(setOf(d(13)), game.frozenDays)
        assertEquals(0, game.freezes)
        // The streak runs straight through the frozen Tuesday: 7 + Monday + today.
        assertEquals(9, s.habitStats(today, game.frozenDays).bestStreak)
        // Without the freeze it would have been broken.
        assertEquals(1, s.habitStats(today).bestStreak)
        val ticks = 9 * PetalsPerTick
        val perfectDays = 9 * PerfectDayPetals
        val week1 = questPetals(s, monday, setOf(QuestKind.Ticks, QuestKind.PerfectDays, QuestKind.Daypart, QuestKind.Streak))
        val week2 = questPetals(s, d(12), setOf(QuestKind.PerfectDays, QuestKind.Streak))
        assertEquals(ticks + perfectDays + PerfectWeekPetals + StreakPetals.getValue(7) + week1 + week2, game.petals)
        assertEquals(d(11), game.badges[BadgeKind.PerfectWeek])
        assertEquals(d(11), game.badges[BadgeKind.Streak7])
        assertEquals(d(5), game.badges[BadgeKind.FirstTick])
        assertEquals(d(5), game.badges[BadgeKind.FirstPerfectDay])
    }

    @Test fun withoutAFreezeAMissBreaksTheStreakAsUsual() {
        // A miss on the first Wednesday: the week is not perfect, so no freeze is earned.
        val s = state(5, 6, 8, 9, 10, 11, 12, 14)
        val game = s.game(today)
        assertTrue(game.frozenDays.isEmpty())
        assertEquals(0, game.freezes)
        assertFalse(BadgeKind.PerfectWeek in game.badges)
        assertEquals(1, s.habitStats(today, game.frozenDays).bestStreak)
    }

    @Test fun todayIsStillOpenSoItNeverSpendsAFreeze() {
        // Everything through Monday the 12th; nothing yet on Tuesday-as-today.
        val s = state(5, 6, 7, 8, 9, 10, 11, 12)
        val game = s.game(d(13))
        assertTrue(game.frozenDays.isEmpty())
        assertEquals(1, game.freezes)
    }

    @Test fun freezesStopAtThree() {
        val long = stretch.copy(created = LocalDate.of(2026, 8, 31))
        // Five perfect weeks, Monday 31 August to Sunday 4 October.
        val days = generateSequence(LocalDate.of(2026, 8, 31)) { it.plusDays(1) }.takeWhile { !it.isAfter(LocalDate.of(2026, 10, 4)) }
        val s = HabitState(habits = listOf(long), checks = days.associate { it.toString() to setOf("a") })
        assertEquals(MaxFreezes, s.game(LocalDate.of(2026, 10, 5)).freezes)
    }

    @Test fun petalsNeverGoDownForAMissedDay() {
        val s = state(5, 6, 7)
        var last = 0
        for (day in 7..14) {
            val petals = s.game(d(day)).petals
            assertTrue("day $day: $petals after $last", petals >= last)
            last = petals
        }
    }

    @Test fun comingBackAfterAWeekAwayEarnsABadge() {
        val habit = stretch.copy(created = LocalDate.of(2026, 9, 20))
        val s = HabitState(habits = listOf(habit), checks = mapOf("2026-09-20" to setOf("a"), "2026-09-28" to setOf("a")))
        assertEquals(LocalDate.of(2026, 9, 28), s.game(today).badges[BadgeKind.Comeback])
        val soon = HabitState(habits = listOf(habit), checks = mapOf("2026-09-20" to setOf("a"), "2026-09-27" to setOf("a")))
        assertFalse(BadgeKind.Comeback in soon.game(today).badges)
    }

    @Test fun weeklyQuestsAreStableAndWithinReach() {
        val two = HabitState(habits = listOf(stretch, Habit(id = "b", name = "Read", goal = "", time = "Evening", created = monday)))
        val first = two.weekQuests(monday)
        assertEquals(first, two.weekQuests(monday))
        assertEquals(3, first.size)
        assertEquals(first.map { it.kind }.distinct(), first.map { it.kind })
        first.forEach { q ->
            assertTrue("$q", q.target > 0)
            if (q.kind == QuestKind.Daypart) assertTrue(q.daypart in Dayparts)
            if (q.kind == QuestKind.EveryHabit) assertEquals(2, q.target)
        }
        // A week with nothing scheduled has no quests.
        assertTrue(HabitState(habits = listOf(stretch.copy(created = today.plusDays(30)))).weekQuests(monday).isEmpty())
    }

    @Test fun ticksThroughASeasonEarnItsKeepsakes() {
        // One habit every day from 1 September to today, 14 October: 44 autumn ticks.
        val autumn = stretch.copy(created = LocalDate.of(2026, 9, 1))
        val days = generateSequence(LocalDate.of(2026, 9, 1)) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }
        val s = HabitState(habits = listOf(autumn), checks = days.associate { it.toString() to setOf("a") })
        val game = s.game(today)
        assertEquals(SeasonOf(Season.Autumn, 2026), game.season)
        assertEquals(44, game.collected)
        assertEquals(listOf(0, 1), game.keepsakes.map { it.tier })
        assertEquals(LocalDate.of(2026, 9, 15), game.keepsakes[0].earnedOn)
        assertEquals(LocalDate.of(2026, 10, 10), game.keepsakes[1].earnedOn)
        // Each keepsake adds its petals on top of everything else.
        val without = s.game(LocalDate.of(2026, 9, 14)).petals
        val withFirst = s.game(LocalDate.of(2026, 9, 15)).petals
        assertTrue(withFirst - without >= KeepsakePetals + PetalsPerTick)
    }

    @Test fun winterBelongsToTheYearItStarts() {
        assertEquals(SeasonOf(Season.Winter, 2026), seasonOf(LocalDate.of(2026, 12, 20)))
        assertEquals(SeasonOf(Season.Winter, 2026), seasonOf(LocalDate.of(2027, 2, 10)))
        assertEquals(SeasonOf(Season.Spring, 2027), seasonOf(LocalDate.of(2027, 3, 1)))
        assertEquals(SeasonOf(Season.Winter, 2025), seasonOf(LocalDate.of(2026, 1, 5)))
    }

    @Test fun everyLevelFromTwoToElevenUnlocksSomething() {
        (2..9).plus(10).plus(11).forEach { level -> assertTrue("level $level", Unlock.entries.any { it.level == level }) }
    }

    @Test fun anEmptyGardenStartsAtLevelOne() {
        val game = HabitState().game(today)
        assertEquals(0, game.petals); assertEquals(1, game.level); assertTrue(game.badges.isEmpty())
    }
}
