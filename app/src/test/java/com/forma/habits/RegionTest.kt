package com.forma.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** Seasons follow the hemisphere and weeks follow the region, without the past ever shifting. */
class RegionTest {
    private fun d(month: Int, day: Int) = LocalDate.of(2026, month, day)

    @Test fun southernTimeZonesAreRecognised() {
        listOf("Australia/Sydney", "Australia/Perth", "Pacific/Auckland", "America/Argentina/Buenos_Aires", "America/Santiago",
            "America/Sao_Paulo", "Africa/Johannesburg", "Antarctica/McMurdo").forEach { assertTrue(it, isSouthern(it)) }
        listOf("Europe/London", "Asia/Kolkata", "America/New_York", "Asia/Tokyo", "UTC", "Etc/GMT+5", "America/Mexico_City")
            .forEach { assertFalse(it, isSouthern(it)) }
    }

    @Test fun southernSeasonsAreHalfAYearRound() {
        assertEquals(Season.Summer, seasonFor(1, south = true))
        assertEquals(Season.Autumn, seasonFor(4, south = true))
        assertEquals(Season.Winter, seasonFor(7, south = true))
        assertEquals(Season.Spring, seasonFor(10, south = true))
        assertEquals(Season.Summer, seasonFor(12, south = true))
        // A southern summer runs over New Year and belongs to the year it began in.
        assertEquals(SeasonOf(Season.Summer, 2026), seasonOf(d(12, 20), south = true))
        assertEquals(SeasonOf(Season.Summer, 2026), seasonOf(LocalDate.of(2027, 2, 10), south = true))
        assertEquals(SeasonOf(Season.Autumn, 2027), seasonOf(LocalDate.of(2027, 3, 1), south = true))
    }

    @Test fun weeksBeforeTheChangeStayMondayToSunday() {
        DayOfWeek.entries.forEach { first ->
            assertEquals(d(10, 12), weekStart(d(10, 14), first))
            assertEquals(d(10, 12), weekStart(d(10, 18), first))
            assertTrue(endsWeek(d(10, 18), first))
            assertFalse(endsWeek(d(10, 17), first))
        }
    }

    @Test fun afterTheChangeWeeksBeginOnTheRegionsFirstDay() {
        // Sunday-first: the week of the change is Monday 19 to Saturday 24, then Sunday to Saturday.
        assertEquals(d(10, 19), weekStart(d(10, 19), DayOfWeek.SUNDAY))
        assertEquals(d(10, 19), weekStart(d(10, 24), DayOfWeek.SUNDAY))
        assertTrue(endsWeek(d(10, 24), DayOfWeek.SUNDAY))
        assertEquals(6, weekOf(d(10, 19), DayOfWeek.SUNDAY).size)
        assertEquals(d(10, 25), weekStart(d(10, 31), DayOfWeek.SUNDAY))
        assertEquals((25..31).map { d(10, it) }, weekOf(d(10, 25), DayOfWeek.SUNDAY))
        // Monday-first carries straight on.
        assertEquals(d(10, 19), weekStart(d(10, 25), DayOfWeek.MONDAY))
        assertTrue(endsWeek(d(10, 25), DayOfWeek.MONDAY))
        assertEquals(7, weekOf(d(10, 19), DayOfWeek.MONDAY).size)
        // Saturday-first: a five-day week at the change, then Saturday to Friday.
        assertEquals(5, weekOf(d(10, 19), DayOfWeek.SATURDAY).size)
        assertEquals(d(10, 24), weekStart(d(10, 30), DayOfWeek.SATURDAY))
    }

    @Test fun aPerfectWeekClosesOnTheRegionsLastDay() {
        val habit = Habit(id = "a", name = "Stretch", goal = "", created = d(10, 25))
        val days = (25..31).map { d(10, it) }
        val s = HabitState(habits = listOf(habit), checks = days.associate { it.toString() to setOf("a") })
        // Sunday 25 to Saturday 31 is a whole week where weeks begin on Sunday...
        assertEquals(d(10, 31), s.game(d(10, 31), first = DayOfWeek.SUNDAY).badges[BadgeKind.PerfectWeek])
        assertEquals(1, s.game(d(11, 1), first = DayOfWeek.SUNDAY).freezes)
        // ...but falls mid-week where they begin on Monday, so the only week closed is the one-day one that ended on the 25th.
        assertEquals(d(10, 25), s.game(d(10, 31), first = DayOfWeek.MONDAY).badges[BadgeKind.PerfectWeek])
    }

    @Test fun daysBeforeAnyHabitAreNotRestDays() {
        val habit = Habit(id = "a", name = "Stretch", goal = "", created = d(10, 12))
        val week = HabitState(habits = listOf(habit)).insightSummary(d(10, 14)).week
        assertEquals(listOf(true, true, true, true, false, false, false), week.map { it.before })
        assertTrue(week.take(4).all { it.rest })
    }
}
