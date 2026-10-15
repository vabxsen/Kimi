package com.forma.habits

import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.geometry.Offset
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
        AccountSession.testOwner = null
        runBlocking { store.update(replaceDamaged = true, eraseHistory = true) { HabitState(name = "Alex") } }
        context.getSharedPreferences("kimi_drafts", 0).edit().clear().commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }
    @After fun close() {
        scenario.close()
        AccountSession.testOwner?.let { owner ->
            context.getSharedPreferences(AccountSession.preferenceName("forma", owner), 0).edit().clear().commit()
        }
        AccountSession.testOwner = null
    }
    private fun waitFor(check: () -> Boolean) { compose.waitUntil(10000, check) }

    @Test fun createCompleteEditAndDeleteHabit() {
        compose.onNodeWithText("+ New habit").performClick()
        compose.onNodeWithText("Habit name").performTextInput("Practice guitar")
        compose.onNodeWithText("A small, specific goal (optional)").performTextInput("Ten minutes")
        compose.onNodeWithText("Let’s make it a habit").performScrollTo().performClick()
        waitFor { store.state.value.habits.size == 1 }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Complete Practice guitar").performScrollTo().performClick()
        waitFor { store.state.value.count(LocalDate.now()) == 1 }
        assertTrue(store.state.value.done(store.state.value.habits.single().id, LocalDate.now()))
        compose.onNodeWithText("Habits", substring = false).performClick()
        compose.onNodeWithContentDescription("Edit Practice guitar").performClick()
        compose.onNodeWithText("Habit name").performTextReplacement("Play guitar")
        compose.onNodeWithText("Habit name").performImeAction()
        compose.onNodeWithText("A small, specific goal (optional)").performImeAction()
        compose.onNodeWithText("Weekdays", substring = false).performScrollTo().performClick()
        waitFor { runCatching { compose.onNodeWithText("Weekdays", substring = false).assertIsSelected() }.isSuccess }
        compose.onNodeWithText("Save my changes").performScrollTo().performClick()
        waitFor { store.state.value.habits.first().name == "Play guitar" }
        assertTrue(store.state.value.habits.first().weekdays)
        assertEquals(1, store.state.value.count(LocalDate.now()))
        scenario.recreate()
        waitFor { store.state.value.count(LocalDate.now()) == 1 }
        compose.onNodeWithText("Habits", substring = false).performClick()
        compose.onNodeWithContentDescription("Edit Play guitar").performClick()
        compose.onNodeWithText("Delete this habit").performScrollTo().performClick()
        compose.onNodeWithText("Delete habit", substring = false).performClick()
        waitFor { store.state.value.habits.isEmpty() }
        assertTrue(store.state.value.checks.isEmpty())
    }

    @Test fun habitCanBeCreatedWithoutAGoalAndANewIcon() {
        compose.onNodeWithText("+ New habit").performClick()
        compose.onNodeWithText("Habit name").performTextInput("Cook dinner")
        compose.onNodeWithText("Habit name").performImeAction()
        compose.onNodeWithContentDescription("Cooking").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Cooking").assertIsSelected()
        compose.onNodeWithText("Let’s make it a habit").performScrollTo().assertIsEnabled().performClick()
        waitFor { store.state.value.habits.size == 1 }
        val saved = store.state.value.habits.single()
        assertEquals("Cook dinner", saved.name)
        assertEquals("", saved.goal)
        assertEquals("Cooking", context.resources.getStringArray(R.array.habit_icon_names)[saved.icon])
    }

    @Test fun habitCanBeCompletedAndUndoneBySwiping() {
        runBlocking { store.update { HabitState(habits = listOf(Habit(name = "Stretch", goal = "Five minutes")), name = "Alex") } }
        waitFor { store.state.value.habits.size == 1 }
        val id = store.state.value.habits.single().id
        // A short drag springs back without ticking anything.
        compose.onNodeWithContentDescription("Complete Stretch").performScrollTo().performTouchInput {
            swipe(centerLeft + Offset(20f, 0f), centerLeft + Offset(width * .2f, 0f), 300)
        }
        compose.waitForIdle()
        assertEquals(0, store.state.value.count(LocalDate.now()))
        compose.onNodeWithContentDescription("Complete Stretch").performTouchInput {
            swipe(centerLeft + Offset(20f, 0f), centerRight - Offset(20f, 0f), 300)
        }
        waitFor { store.state.value.done(id, LocalDate.now()) }
        compose.onNodeWithContentDescription("Undo Stretch").performTouchInput {
            swipe(centerLeft + Offset(20f, 0f), centerRight - Offset(20f, 0f), 300)
        }
        waitFor { !store.state.value.done(id, LocalDate.now()) }
    }

    @Test fun habitCanBeEditedStraightFromToday() {
        runBlocking { store.update { HabitState(habits = listOf(Habit(name = "Read", goal = "Ten pages")), name = "Alex") } }
        waitFor { store.state.value.habits.size == 1 }
        compose.onNodeWithContentDescription("Edit Read").performScrollTo().performClick()
        compose.onNodeWithText("Save my changes").assertExists()
        compose.onNodeWithText("Habit name").performTextReplacement("Read a chapter")
        compose.onNodeWithText("Save my changes").performScrollTo().performClick()
        waitFor { store.state.value.habits.single().name == "Read a chapter" }
        // Opening the editor from the row must not have ticked the habit on the way.
        assertEquals(0, store.state.value.count(LocalDate.now()))
        compose.onNodeWithContentDescription("Edit Read a chapter").assertExists()
    }

    @Test fun gardenOpensFromTodayAndCanBeSwitchedOff() {
        val today = LocalDate.now()
        runBlocking {
            store.update {
                HabitState(habits = listOf(Habit(id = "g", name = "Water", goal = "", created = today.minusDays(3))),
                    checks = mapOf(today.minusDays(1).toString() to setOf("g")), name = "Alex")
            }
        }
        try {
            compose.runOnIdle { GameSetting.setEnabled(context, true) }
            waitFor { store.state.value.habits.size == 1 }
            compose.onNodeWithTag("garden_bar").performScrollTo().performClick()
            compose.onNodeWithText("Your garden").assertExists()
            compose.onNodeWithText("Streak freezes").performScrollTo().assertExists()
            compose.onNodeWithContentDescription("Close your garden").performClick()
            compose.onNodeWithContentDescription("Open settings").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("game_switch"))
            compose.onNodeWithTag("game_switch").assertIsOn().performClick()
            compose.runOnIdle { assertFalse(GameSetting.enabled) }
            compose.onNodeWithText("Today", substring = false).performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("garden_bar").assertDoesNotExist()
            // Still a habit tracker without the game.
            compose.onNodeWithContentDescription("Complete Water").assertExists()
        } finally {
            GameSetting.setEnabled(context, true)
        }
    }

    @Test fun colorThemeCanBeChosenInSettings() {
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("palette_sunset"))
        compose.onNodeWithTag("palette_kimi").assertIsSelected()
        compose.onNodeWithTag("palette_ocean").performClick()
        compose.runOnIdle { assertEquals(KimiTheme.Ocean, ThemeSetting.theme) }
        compose.onNodeWithTag("palette_ocean").assertIsSelected()
        compose.onNodeWithTag("palette_monochrome").performClick()
        compose.runOnIdle { assertEquals(KimiTheme.Monochrome, ThemeSetting.theme) }
        // Leave the device on the default theme for the tests that follow.
        compose.onNodeWithTag("palette_kimi").performClick()
        compose.runOnIdle { assertEquals(KimiTheme.Kimi, ThemeSetting.theme) }
    }

    @Test fun reminderSwitchTurnsANudgeOnAndOff() {
        runBlocking { store.update { HabitState(habits = listOf(Habit(name = "Stretch", goal = "")), name = "Alex") } }
        waitFor { store.state.value.habits.size == 1 }
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Reminder for Stretch"))
        compose.onNodeWithText("No reminder", substring = false).assertExists()
        compose.onNodeWithContentDescription("Reminder for Stretch").performClick()
        waitFor { store.state.value.habits.single().reminderMinutes == 9 * 60 }
        compose.onNodeWithContentDescription("Reminder for Stretch").assertIsOn()
        compose.onNodeWithContentDescription("Reminder for Stretch").performClick()
        waitFor { store.state.value.habits.single().reminderMinutes == null }
    }

    @Test fun habitEditorSavesMultipleDailyNotifications() {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS"
            )
        ).use { it.readBytes() }
        compose.onNodeWithText("+ New habit").performClick()
        compose.onNodeWithText("Habit name").performTextInput("Drink water")
        compose.onNodeWithText("A small, specific goal (optional)").performTextInput("Stay hydrated")
        compose.onNodeWithText("A small, specific goal (optional)").performImeAction()
        compose.onNodeWithContentDescription("Habit reminder").performScrollTo().performClick()
        compose.onNodeWithText("Notifications per day").performScrollTo()
        compose.onNodeWithTag("reminder_count_picker").performScrollTo()
        compose.onNodeWithTag("reminder_count_picker").performScrollToIndex(5)
        compose.onNodeWithTag("reminder_count_6").performClick()
        compose.onNodeWithTag("reminder_count_6").assertIsSelected()
        compose.onNodeWithText("Let’s make it a habit").performScrollTo().performClick()
        waitFor { store.state.value.habits.size == 1 }
        val saved = store.state.value.habits.single()
        assertEquals(6, saved.reminderCount)
        assertEquals(9 * 60, saved.reminderMinutes)
        assertEquals(6, saved.dailyReminderMinutes().size)
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

    @Test fun navigationCalendarAndEditorCancelControlsWork() {
        val habit = Habit(name = "Read", goal = "Ten pages")
        runBlocking { store.update { HabitState(habits = listOf(habit), name = "Alex") } }
        waitFor { store.state.value.habits.size == 1 }

        compose.onNodeWithText("Calendar", substring = false).performClick()
        compose.onNodeWithText("Look at you grow.", substring = false).assertExists()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithContentDescription("Previous month").assertIsEnabled()
        compose.onAllNodesWithText("Today", substring = false)[0].performScrollTo().performClick()

        compose.onNodeWithText("Insights", substring = false).performClick()
        compose.onNodeWithText("You’re on a roll!", substring = false).assertExists()

        compose.onNodeWithText("Habits", substring = false).performClick()
        compose.onNodeWithContentDescription("Edit Read").performClick()
        compose.onNodeWithContentDescription("Close habit editor").performClick()
        compose.onNodeWithText("Habit name", substring = false).assertDoesNotExist()

        compose.onNodeWithContentDescription("Edit Read").performClick()
        compose.onNodeWithText("Delete this habit", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Keep it", substring = false).performClick()
        assertEquals("Read", store.state.value.habits.single().name)
        compose.onNodeWithContentDescription("Close habit editor").performClick()
    }

    @Test fun journalMoodEditAndDeleteControlsWork() {
        val today = LocalDate.now()
        runBlocking { store.update { HabitState(journal = listOf(Reflection(today, 2, "Original note"))) } }
        waitFor { store.state.value.journal.size == 1 }

        compose.onNodeWithText("Journal", substring = false).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Original note", substring = false))
        compose.onNodeWithText("Edit", substring = false).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Amazing").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Amazing").assertIsSelected()
        compose.onNodeWithText("A thought worth keeping").performTextReplacement("Updated note")
        compose.onNodeWithText("Keep this little moment", substring = false).performScrollTo().performClick()
        waitFor { store.state.value.journal.singleOrNull()?.text == "Updated note" }
        assertEquals(4, store.state.value.journal.single().mood)

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Delete", substring = false))
        compose.onNodeWithText("Delete", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Keep it", substring = false).performClick()
        assertEquals(1, store.state.value.journal.size)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Delete", substring = false))
        compose.onNodeWithText("Delete", substring = false).performScrollTo().performClick()
        compose.onAllNodesWithText("Delete", substring = false)[1].performClick()
        waitFor { store.state.value.journal.isEmpty() }
    }

    @Test fun settingsNameThemeReminderAndCreditsControlsWork() {
        val habit = Habit(name = "Stretch", goal = "Five minutes")
        runBlocking { store.update { HabitState(habits = listOf(habit), name = "Alex") } }
        waitFor { store.state.value.habits.size == 1 }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        }

        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Dark", substring = false))
        compose.onNodeWithTag("theme_dark").performClick()
        compose.runOnIdle { assertEquals(ThemeMode.Dark, ThemeSetting.mode) }
        compose.onNodeWithTag("theme_light").performClick()
        compose.runOnIdle { assertEquals(ThemeMode.Light, ThemeSetting.mode) }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Stretch", substring = false))
        compose.onNodeWithText("Stretch", substring = false).performClick()
        compose.onNodeWithContentDescription("Habit reminder").performClick()
        compose.onNodeWithText("Save reminder", substring = false).performClick()
        waitFor { store.state.value.habits.single().reminderMinutes == 9 * 60 }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Credits", substring = false))
        compose.onNodeWithText("Credits", substring = false).performClick()
        compose.onNodeWithText("Open-source licenses", substring = false).performClick()
        compose.onNodeWithText("Open-source notices", substring = false).assertExists()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK")
        ).use { it.readBytes() }
        waitFor { compose.onAllNodesWithText("Open-source notices", substring = false).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Close", substring = false).performClick()
        compose.onNodeWithText("Designed & developed by Vaibhav Sen", substring = false).assertDoesNotExist()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Automatic", substring = false))
        compose.onNodeWithTag("theme_system").performClick()
        compose.runOnIdle { assertEquals(ThemeMode.System, ThemeSetting.mode) }

        // Keep the rename last: its software keyboard resizes the settings list while it closes.
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Edit your name"))
        compose.onNodeWithContentDescription("Edit your name").performClick()
        compose.onNodeWithText("Your name", substring = false).performTextReplacement("Taylor")
        compose.onNodeWithContentDescription("Save your name").performClick()
        waitFor { store.state.value.name == "Taylor" }
    }

    @Test fun backupAndRestoreRoundTripInRealAndroidStorage() = runBlocking {
        val h = Habit(name = "Read", goal = "Ten pages")
        val content = HabitState(listOf(h), mapOf(LocalDate.now().toString() to setOf(h.id)), listOf(Reflection(LocalDate.now(), 3, "A real win")))
        val expected = store.update { content }
        val file = java.io.File(context.cacheDir, "roundtrip.json")
        file.writeText(BackupCodec.encode(store.state.value))
        assertEquals(expected, BackupCodec.decode(file.readText()))
        store.update { HabitState() }
        store.update { BackupCodec.decode(file.readText()) }
        val restored = store.state.value
        assertEquals(expected.copy(sync = SyncMetadata()), restored.copy(sync = SyncMetadata()))
        assertTrue(restored.sync.habitUpdates.getValue(h.id) > expected.sync.habitUpdates.getValue(h.id))
        val persisted = context.getSharedPreferences("forma", 0).getString("state", null)!!
        assertEquals(restored, BackupCodec.decode(persisted))
        file.delete()
        Unit
    }

    @Test fun staleSyncSnapshotCannotOverwriteANewerLocalWrite() = runBlocking {
        val old = store.snapshot()
        store.update(stamp = Long.MAX_VALUE - 1) { it.copy(name = "Newest local name") }
        val current = store.snapshot()
        assertEquals(old.updatedAt + 1, current.updatedAt)

        val staleApply = store.replaceIfUnchanged(old, old.state.copy(name = "Stale cloud name"), current.updatedAt + 1)
        assertNull(staleApply)
        assertEquals("Newest local name", store.state.value.name)
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
        compose.onNodeWithText("Start tracking my habits").performScrollTo().performClick()
        waitFor { store.state.value.onboarded }
        assertEquals("Vaibhav", store.state.value.name)
        assertTrue(store.state.value.habits.isEmpty())
        assertTrue(store.state.value.checks.isEmpty())
        assertTrue(store.state.value.journal.isEmpty())
        compose.onNodeWithText("Habit name").assertExists().performTextInput("Morning walk")
        compose.onNodeWithText("A small, specific goal (optional)").performTextInput("Walk for ten minutes")
        compose.onNodeWithText("Let’s make it a habit").performScrollTo().performClick()
        waitFor { store.state.value.habits.size == 1 }
        assertEquals("Morning walk", store.state.value.habits.single().name)
        assertTrue(store.state.value.checks.isEmpty())
    }

    @Test fun scheduledReminderDeliversThroughAndroidAlarmManager() {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")).use { it.readBytes() }
        waitFor { Reminders.allowed(context) }
        val time = java.time.LocalTime.now().plusMinutes(1)
        val start = time.hour * 60 + time.minute
        val count = if (start <= reminderStartLimit(2)) 2 else 1
        val h = Habit(name = "Scheduled tiny win", goal = "A real Android alarm", reminderMinutes = start, reminderCount = count)
        runBlocking { store.update { HabitState(listOf(h)) } }
        Reminders.reschedule(context, store.state.value, force = true)
        val alarmPreferences = context.getSharedPreferences("kimi_alarms", 0)
        val firstPlan = alarmPreferences.getString("planned:${h.id}", null)
        val manager = context.getSystemService(NotificationManager::class.java)
        compose.waitUntil(180000) { manager.activeNotifications.any { it.tag == h.id } }
        if (count > 1) {
            compose.waitUntil(10000) { alarmPreferences.getString("planned:${h.id}", null) != firstPlan }
            val nextPlan = java.time.ZonedDateTime.parse(alarmPreferences.getString("planned:${h.id}", null))
            assertEquals(h.dailyReminderMinutes()[1], nextPlan.hour * 60 + nextPlan.minute)
        }
        val notification = manager.activeNotifications.first { it.tag == h.id }.notification
        notification.actions.first().actionIntent.send()
        waitFor { store.state.value.done(h.id, LocalDate.now()) }
    }
}
