package com.forma.habits

import android.app.Application
import android.content.Intent
import android.app.NotificationManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL
import java.time.LocalDate
import java.util.UUID

/** Real Android UI + SDK requests to an isolated demo Auth backend. No real emails are sent. */
@RunWith(AndroidJUnit4::class)
class AccountFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private lateinit var firebase: FirebaseApp
    private lateinit var auth: FirebaseAuth
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var vm: AccountViewModel
    private val viewModels = ViewModelStore()
    private val email = "kimi-${UUID.randomUUID()}@example.invalid"
    private val password = "Kimi-test-42!"
    @Before fun prepare() {
        val existing = FirebaseApp.getApps(app).find { it.name == "kimi-auth-test" }
        firebase = existing ?: FirebaseApp.initializeApp(app, FirebaseOptions.Builder()
            .setProjectId("demo-kimi-auth").setApplicationId("1:123456789:android:kimitest")
            .setApiKey("fake-api-key-for-auth-emulator-only").build(), "kimi-auth-test")
        auth = FirebaseAuth.getInstance(firebase)
        if (existing == null) auth.useEmulator("10.0.2.2", 9099)
        AccountSession.testAuth = auth
        runBlocking { HabitStore.get(app, "").update(true, true) { HabitState(name = "Guest", habits = listOf(Habit(id = "guest", name = "Guest ritual", goal = "Stay private"))) } }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = ViewModelProvider(viewModels, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[AccountViewModel::class.java]
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun close() {
        if (::scenario.isInitialized) scenario.close()
        if (::auth.isInitialized) { runBlocking { auth.currentUser?.delete()?.await() }; auth.signOut() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModels.clear() }
        AccountSession.testAuth = null
    }
    private fun waitFor(check: () -> Boolean) = compose.waitUntil(20000, check)
    private fun action(block: () -> Unit) {
        compose.runOnIdle(block)
        waitFor { !vm.busy }
        assertFalse(vm.message, vm.error)
    }
    private fun openAccount() {
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Sign in to Kimi"))
        compose.onNodeWithText("Sign in to Kimi").performScrollTo().performClick()
    }

    @Test fun createSignOutSignInAndDeleteThroughUi() {
        openAccount()
        compose.onNodeWithText("New here? Create an account").performScrollTo().performClick()
        compose.onNodeWithText("Account name", substring = false).performScrollTo().performTextInput("Kimi tester")
        compose.onNodeWithText("Email address").performScrollTo().performTextInput(email)
        compose.onNodeWithText("Password", substring = false).performScrollTo().performTextInput(password)
        compose.onNodeWithText("Confirm password", substring = false).performScrollTo().performTextInput(password)
        compose.onNodeWithText("Create my account").performScrollTo().performClick()
        waitFor { auth.currentUser?.displayName == "Kimi tester" }
        waitFor { compose.onAllNodesWithText("Your account is ready. You can verify your email below.").fetchSemanticsNodes().isNotEmpty() }
        val uid = auth.currentUser!!.uid
        assertTrue(HabitStore.get(app, uid).state.value.habits.isEmpty())
        compose.onNodeWithText("Copy guest progress").performScrollTo().performClick()
        compose.onNodeWithText("Copy progress", substring = false).performClick()
        waitFor { HabitStore.get(app, uid).state.value.habits.size == 1 }
        compose.onNodeWithText("Sign out", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Sign out now").performClick()
        waitFor { auth.currentUser == null }
        compose.onNodeWithText("Already have an account? Sign in").performScrollTo().performClick()
        compose.onNodeWithText("Email address").performScrollTo().performTextReplacement(email)
        compose.onNodeWithText("Password", substring = false).performScrollTo().performTextInput(password)
        compose.onNodeWithText("Sign in", substring = false).performScrollTo().performClick()
        waitFor { auth.currentUser?.uid == uid }
        scenario.recreate()
        waitFor { vm.account?.uid == uid }
        assertEquals("Guest ritual", HabitStore.get(app).state.value.habits.single().name)
        compose.onNodeWithText("Delete my account").performScrollTo().performClick()
        compose.onNodeWithText("Confirm with password").performTextInput(password)
        compose.onNodeWithText("Delete permanently").performClick()
        waitFor { auth.currentUser == null && HabitStore.get(app, uid).state.value.habits.isEmpty() }
        assertEquals("Guest ritual", HabitStore.get(app, "").state.value.habits.single().name)
    }

    @Test fun verificationPasswordResetProfileAndInvalidLogin() {
        action { vm.createAccount("Test friend", email, password) }
        action { vm.verifyEmail() }
        val verification = oobCode("VERIFY_EMAIL")
        runBlocking { auth.applyActionCode(verification).await() }
        action { vm.refresh() }
        assertTrue(vm.account!!.verified)
        action { vm.updateName("New name") }
        assertEquals("New name", auth.currentUser!!.displayName)
        action { vm.resetPassword(email) }
        val reset = oobCode("PASSWORD_RESET")
        runBlocking { auth.confirmPasswordReset(reset, "New-kimi-password-42!").await() }
        action { vm.signOut() }
        compose.runOnIdle { vm.emailSignIn(email, "Definitely-wrong-42!") }
        waitFor { !vm.busy }
        assertTrue(vm.error)
        assertNull(auth.currentUser)
        action { vm.emailSignIn(email, "New-kimi-password-42!") }
        assertEquals(email, vm.account!!.email)
        action { vm.deleteAccount(app, "New-kimi-password-42!") }
        assertNull(auth.currentUser)
    }

    @Test fun accountSpacesDraftsAndOldNotificationAreIsolated() {
        action { vm.createAccount("Account A", email, password) }
        val first = auth.currentUser!!.uid
        val habit = Habit(id = "shared-id", name = "Private A", goal = "One step", reminderMinutes = 540)
        runBlocking { HabitStore.get(app).update { HabitState(habits = listOf(habit)) } }
        app.getSharedPreferences(AccountSession.preferenceName("kimi_drafts"), 0).edit().putString("private", "Account A draft").commit()
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${app.packageName} android.permission.POST_NOTIFICATIONS")).use { it.readBytes() }
        Reminders.show(app, habit, LocalDate.now())
        val manager = app.getSystemService(NotificationManager::class.java)
        waitFor { manager.activeNotifications.any { it.tag == habit.id } }
        val staleAction = manager.activeNotifications.first { it.tag == habit.id }.notification.actions.first().actionIntent
        action { vm.signOut() }
        assertEquals("Guest ritual", HabitStore.get(app).state.value.habits.single().name)
        action { vm.createAccount("Account B", "b-$email", password) }
        val second = auth.currentUser!!.uid
        assertNotEquals(first, second)
        assertTrue(HabitStore.get(app).state.value.habits.isEmpty())
        assertFalse(app.getSharedPreferences(AccountSession.preferenceName("kimi_drafts"), 0).contains("private"))
        runBlocking { HabitStore.get(app).update { HabitState(habits = listOf(habit.copy(name = "Private B"))) } }
        staleAction.send()
        // Wait for the asynchronous receiver to drain, then inspect both independent stores.
        compose.waitForIdle()
        Thread.sleep(1000)
        assertFalse(HabitStore.get(app, first).state.value.done(habit.id, LocalDate.now()))
        assertFalse(HabitStore.get(app, second).state.value.done(habit.id, LocalDate.now()))
        action { vm.deleteAccount(app, password) }
        action { vm.emailSignIn(email, password) }
        assertEquals("Private A", HabitStore.get(app).state.value.habits.single().name)
        assertEquals("Account A draft", app.getSharedPreferences(AccountSession.preferenceName("kimi_drafts"), 0).getString("private", null))
        action { vm.deleteAccount(app, password) }
        assertFalse(app.getSharedPreferences(AccountSession.preferenceName("kimi_drafts", first), 0).contains("private"))
    }

    private fun oobCode(type: String): String {
        val result = URL("http://10.0.2.2:9099/emulator/v1/projects/demo-kimi-auth/oobCodes").readText()
        val codes = JSONObject(result).getJSONArray("oobCodes")
        return (0 until codes.length()).map { codes.getJSONObject(it) }
            .last { it.getString("email") == email && it.getString("requestType") == type }.getString("oobCode")
    }
}
