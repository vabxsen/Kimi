package com.forma.habits

import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class KimiFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val store get() = HabitStore.get(context)
    @Before fun prepare() {
        runBlocking { store.update(replaceDamaged = true, eraseHistory = true) { HabitState(name = "Alex") } }
        context.getSharedPreferences("kimi_drafts", 0).edit().clear().commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }
    @After fun close() { scenario.close() }
    private fun waitFor(check: () -> Boolean) { compose.waitUntil(10000, check) }

    @Test fun createCompleteEditAndDeleteHabit() {
        compose.onNodeWithText("+ New habit").performClick()
        compose.onNodeWithText("Habit name").performTextInput("Practice guitar")
        compose.onNodeWithText("A small, specific goal").performTextInput("Ten minutes")
        compose.onNodeWithText("Let’s make it a habit").performScrollTo().performClick()
        waitFor { store.state.value.habits.size == 1 }
        compose.waitForIdle()
        waitFor { compose.onAllNodesWithText("Your habit is saved. Make it work for you.").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Complete Practice guitar").performScrollTo().performClick()
        waitFor { store.state.value.count(LocalDate.now()) == 1 }
        compose.onNodeWithText("Habits", substring = false).performClick()
        compose.onNodeWithContentDescription("Edit Practice guitar").performClick()
        compose.onNodeWithText("Habit name").performTextReplacement("Play guitar")
        compose.onNodeWithText("Weekdays", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Save my changes").performScrollTo().performClick()
        waitFor { store.state.value.habits.first().name == "Play guitar" }
        assertTrue(store.state.value.habits.first().weekdays)
        assertEquals(1, store.state.value.count(LocalDate.now()))
        scenario.recreate()
        compose.onNodeWithText("Habits", substring = false).performClick()
        compose.onNodeWithContentDescription("Edit Play guitar").performClick()
        compose.onNodeWithText("Delete this habit").performScrollTo().performClick()
        compose.onNodeWithText("Delete habit", substring = false).performClick()
        waitFor { store.state.value.habits.isEmpty() }
        assertTrue(store.state.value.checks.isEmpty())
    }

    @Test fun journalDraftSurvivesNavigationAndActivityRecreation() {
        compose.onNodeWithText("Journal", substring = false).performClick()
        compose.onNodeWithText("A thought worth keeping").performScrollTo().performTextInput("Found a tiny happy moment")
        compose.onNodeWithText("Today", substring = false).performClick()
        compose.onNodeWithText("Journal", substring = false).performClick()
        compose.onNodeWithText("Found a tiny happy moment").assertExists()
        scenario.recreate()
        compose.onNodeWithText("Journal", substring = false).performClick()
        compose.onNodeWithText("Found a tiny happy moment").assertExists()
        compose.onNodeWithText("Keep this little moment").performScrollTo().performClick()
        waitFor { store.state.value.journal.size == 1 }
        assertEquals("Found a tiny happy moment", store.state.value.journal.single().text)
    }

    @Test fun backupAndRestoreRoundTripInRealAndroidStorage() = runBlocking {
        val h = Habit(name = "Read", goal = "Ten pages")
        val expected = HabitState(listOf(h), mapOf(LocalDate.now().toString() to setOf(h.id)), listOf(Reflection(LocalDate.now(), 3, "A real win")))
        store.update { expected }
        val file = java.io.File(context.cacheDir, "roundtrip.json")
        file.writeText(BackupCodec.encode(store.state.value))
        store.update { HabitState() }
        store.update { BackupCodec.decode(file.readText()) }
        assertEquals(expected, store.state.value)
        val persisted = context.getSharedPreferences("forma", 0).getString("state", null)!!
        assertEquals(expected, BackupCodec.decode(persisted))
        file.delete()
        Unit
    }

    @Test fun notificationActionMarksCompleteOnce() {
        val today = LocalDate.now()
        val h = Habit(name = "Notification ritual", goal = "One tiny step", reminderMinutes = 540)
        runBlocking { store.update { HabitState(listOf(h)) } }
        Reminders.createChannel(context)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")).use { it.readBytes() }
        waitFor { Reminders.allowed(context) }
        Reminders.show(context, h, today)
        val manager = context.getSystemService(NotificationManager::class.java)
        waitFor { manager.activeNotifications.any { it.tag == h.id } }
        val notification = manager.activeNotifications.first { it.tag == h.id }.notification
        notification.actions.first().actionIntent.send()
        waitFor { store.state.value.done(h.id, today) }
        notification.actions.first().actionIntent.send()
        waitFor { manager.activeNotifications.none { it.tag == h.id } }
        assertEquals(1, store.state.value.count(today))
    }

    @Test fun firstRunCreatesOnlyRealStartingData() {
        runBlocking { store.update { HabitState(onboarded = false) } }
        compose.onNodeWithText("What should we call you?").performTextInput("Vaibhav")
        compose.onNodeWithText("What should we call you?").performImeAction()
        compose.onNodeWithText("Let’s grow together").performScrollTo().performClick()
        waitFor { store.state.value.onboarded }
        assertEquals("Vaibhav", store.state.value.name)
        assertEquals(5, store.state.value.habits.size)
        assertTrue(store.state.value.checks.isEmpty())
        assertTrue(store.state.value.journal.isEmpty())
    }

    @Test fun scheduledReminderDeliversThroughAndroidAlarmManager() {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")).use { it.readBytes() }
        waitFor { Reminders.allowed(context) }
        val time = java.time.LocalTime.now().plusMinutes(1)
        val h = Habit(name = "Scheduled tiny win", goal = "A real Android alarm", reminderMinutes = time.hour * 60 + time.minute)
        runBlocking { store.update { HabitState(listOf(h)) } }
        Reminders.reschedule(context, store.state.value, force = true)
        val manager = context.getSystemService(NotificationManager::class.java)
        compose.waitUntil(180000) { manager.activeNotifications.any { it.tag == h.id } }
        val notification = manager.activeNotifications.first { it.tag == h.id }.notification
        notification.actions.first().actionIntent.send()
        waitFor { store.state.value.done(h.id, LocalDate.now()) }
    }
}
