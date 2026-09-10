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

    @Test fun removalsPropagateWithoutBeingResurrectedByAStaleDevice() {
        val original = HabitState(
            habits = listOf(reading, walking),
            checks = mapOf(monday.toString() to setOf("read")),
            journal = listOf(Reflection(monday, 3, "old note"))
        ).recordChangesFrom(HabitState(), 100)
        val removed = original.copy(
            habits = listOf(walking),
            checks = emptyMap(),
            journal = emptyList()
        ).recordChangesFrom(original, 200)

        // Even a legacy client with a newer whole-space timestamp cannot disprove explicit deletes.
        val staleLegacy = HabitState(
            habits = listOf(reading, walking),
            checks = mapOf(monday.toString() to setOf("read")),
            journal = listOf(Reflection(monday, 3, "old note"))
        )
        val merged = mergeSpaces(removed, 200, staleLegacy, 300)
        assertNull(merged.habits.find { it.id == "read" })
        assertFalse(merged.done("read", monday))
        assertTrue(merged.journal.isEmpty())
        assertEquals(200L, merged.sync.habitDeletions["read"])
        assertEquals(200L, merged.sync.checkDeletions[checkRevisionKey(monday.toString(), "read")])
        assertEquals(200L, merged.sync.reflectionDeletions[monday.toString()])
    }

    @Test fun aLaterExplicitRecreationWinsAnOlderDeletion() {
        val original = HabitState(listOf(reading)).recordChangesFrom(HabitState(), 100)
        val deleted = HabitState(sync = original.sync).recordChangesFrom(original, 200)
        val recreatedHabit = reading.copy(name = "Read again")
        val recreated = deleted.copy(habits = listOf(recreatedHabit)).recordChangesFrom(deleted, 300)

        assertEquals("Read again", mergeSpaces(deleted, 200, recreated, 300).habits.single().name)
        assertFalse(mergeSpaces(deleted, 200, recreated, 300).sync.habitDeletions.containsKey("read"))
    }

    @Test fun unrelatedEditsDoNotMakeLegacyItemsLookNew() {
        val legacy = HabitState(listOf(reading))
        val seeded = legacy.withBaselineRevisions(100)
        val renamed = seeded.copy(name = "New name").recordChangesFrom(seeded, 300)
        assertEquals(100L, renamed.sync.habitUpdates["read"])
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
