package com.forma.habits

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** The property that matters most: merging must never drop something a person wrote. */
class SyncMergeTest {
    private val monday = LocalDate.of(2026, 9, 7)
    private val reading = Habit("read", "Read", "Ten pages", created = monday)
    private val walking = Habit("walk", "Walk", "Half an hour", created = monday)

    @Test fun habitsFromBothSidesSurvive() {
        val phone = HabitState(listOf(reading))
        val tablet = HabitState(listOf(walking))
        val merged = mergeSpaces(phone, 100, tablet, 200)
        assertEquals(setOf("read", "walk"), merged.habits.map { it.id }.toSet())
    }

    @Test fun aReflectionWrittenOnTheOlderSideIsNotLost() {
        // The exact case that whole-blob last-write-wins would destroy.
        val phone = HabitState(
            habits = listOf(reading),
            journal = listOf(Reflection(monday, 3, "Something I do not want to lose"))
        )
        val tablet = HabitState(
            habits = listOf(reading),
            checks = mapOf(monday.toString() to setOf("read"))
        )
        val merged = mergeSpaces(phone, localAt = 100, remote = tablet, remoteAt = 200)
        assertEquals(1, merged.journal.size)
        assertEquals("Something I do not want to lose", merged.journal.first().text)
        assertEquals(setOf("read"), merged.checks[monday.toString()])
    }

    @Test fun theNewerSideWinsWhenBothEditedTheSameThing() {
        val phone = HabitState(journal = listOf(Reflection(monday, 1, "written first")))
        val tablet = HabitState(journal = listOf(Reflection(monday, 4, "written second")))
        assertEquals("written second", mergeSpaces(phone, 100, tablet, 200).journal.single().text)
        // ...and the direction is symmetric.
        assertEquals("written first", mergeSpaces(phone, 300, tablet, 200).journal.single().text)
    }

    /**
     * The documented cost of having no tombstones, pinned so it cannot change by accident: a
     * device that never saw a removal reintroduces the entry. Erring this way keeps content.
     */
    @Test fun removalsDoNotPropagateToADeviceThatNeverSawThem() {
        val checked = HabitState(listOf(reading), mapOf(monday.toString() to setOf("read")))
        val unchecked = HabitState(listOf(reading))
        assertTrue(mergeSpaces(checked, 100, unchecked, 200).done("read", monday))

        val withHabit = HabitState(listOf(reading, walking))
        val deleted = HabitState(listOf(reading))
        assertEquals(2, mergeSpaces(withHabit, 100, deleted, 200).habits.size)
    }

    @Test fun checkInsForHabitsNeitherSideHasAreDropped() {
        // A merge must never produce a space BackupCodec would reject as inconsistent.
        val orphanA = HabitState(listOf(reading), mapOf(monday.toString() to setOf("read", "ghost")))
        val orphanB = HabitState(listOf(reading), mapOf(monday.toString() to setOf("read")))
        val merged = mergeSpaces(orphanA, 100, orphanB, 200)
        assertEquals(setOf("read"), merged.checks[monday.toString()])
        assertEquals(merged, BackupCodec.decode(BackupCodec.encode(merged)))
    }

    @Test fun mergedSpaceAlwaysSurvivesTheCodec() {
        val phone = HabitState(
            habits = listOf(reading, walking),
            checks = mapOf(monday.toString() to setOf("read"), monday.plusDays(1).toString() to setOf("walk")),
            journal = listOf(Reflection(monday, 2, "one")),
            name = "Vaibhav"
        )
        val tablet = HabitState(
            habits = listOf(reading),
            checks = mapOf(monday.toString() to setOf("read")),
            journal = listOf(Reflection(monday.plusDays(1), 4, "two"))
        )
        val merged = mergeSpaces(phone, 100, tablet, 200)
        assertEquals(2, merged.journal.size)
        assertEquals(merged, BackupCodec.decode(BackupCodec.encode(merged)))
    }

    @Test fun onboardingSticksOnceEitherSideHasIt() {
        val fresh = HabitState(onboarded = false)
        val used = HabitState(listOf(reading), onboarded = true)
        assertTrue(mergeSpaces(fresh, 200, used, 100).onboarded)
        assertTrue(mergeSpaces(used, 200, fresh, 100).onboarded)
    }

    @Test fun mergingWithAnIdenticalCopyChangesNothing() {
        val state = HabitState(
            habits = listOf(reading),
            checks = mapOf(monday.toString() to setOf("read")),
            journal = listOf(Reflection(monday, 3, "same")),
            name = "Vaibhav"
        )
        assertEquals(state, mergeSpaces(state, 100, state, 200))
    }
}
