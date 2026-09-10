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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.URL
import java.net.HttpURLConnection
import java.time.LocalDate
import java.util.UUID

/** Real Android UI + SDK requests to isolated Auth and Firestore emulators. No real emails are sent. */
@RunWith(AndroidJUnit4::class)
class AccountFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private lateinit var firebase: FirebaseApp
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
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
        firestore = FirebaseFirestore.getInstance(firebase)
        if (existing == null) {
            auth.useEmulator("10.0.2.2", 9099)
            firestore.useEmulator("10.0.2.2", 8080)
        }
        AccountSession.testAuth = auth
        SpaceSync.testFirebaseAppName = firebase.name
        runBlocking { HabitStore.get(app, "").update(true, true) { HabitState(name = "Guest", habits = listOf(Habit(id = "guest", name = "Guest ritual", goal = "Stay private"))) } }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = ViewModelProvider(viewModels, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[AccountViewModel::class.java]
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun close() {
        if (::scenario.isInitialized) scenario.close()
        if (::auth.isInitialized) {
            runBlocking { auth.currentUser?.let { user -> SpaceSync.deleteSpace(user.uid); user.delete().await() } }
            auth.signOut()
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModels.clear() }
        SpaceSync.testFirebaseAppName = null
        AccountSession.testAuth = null
    }
    private fun waitFor(check: () -> Boolean) = compose.waitUntil(20000, check)
    private fun action(block: () -> Unit) {
        compose.runOnIdle(block)
        waitFor { !vm.busy }
        assertFalse(vm.message, vm.error)
    }
    private fun openAccount(action: String = "Sign in to Kimi") {
        compose.onNodeWithContentDescription("Open settings").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(action))
        compose.onNodeWithText(action).performScrollTo().performClick()
    }

    @Test fun createSignOutSignInAndDeleteThroughUi() {
        openAccount()
        compose.onNodeWithText("New here? Create an account").performScrollTo().performClick()
        compose.onNodeWithText("Account name", substring = false).performScrollTo().performTextInput("Kimi tester")
        compose.onNodeWithText("Email address").performScrollTo().performTextInput(email)
        compose.onNodeWithText("Password", substring = false).performScrollTo().performTextInput(password)
        compose.onNodeWithText("Confirm password", substring = false).performScrollTo().performTextInput(password)
        compose.onNodeWithText("Create my account").performScrollTo().assertIsEnabled()
        compose.waitForIdle()
        compose.onNodeWithText("Create my account").performClick()
        waitFor { auth.currentUser?.displayName == "Kimi tester" }
        waitFor { compose.onAllNodesWithText("Your account is ready. You can verify your email below.").fetchSemanticsNodes().isNotEmpty() }
        val uid = auth.currentUser!!.uid
        val fresh = HabitStore.get(app, uid).state.value
        assertTrue(fresh.onboarded)
        assertTrue(fresh.habits.isEmpty())
        assertTrue(fresh.checks.isEmpty())
        assertTrue(fresh.journal.isEmpty())
        val freshCloud = runBlocking { firestore.collection("spaces").document(uid).get().await() }
        val freshRemote = BackupCodec.decode(freshCloud.getString("state")!!)
        assertTrue(freshRemote.onboarded)
        assertTrue(freshRemote.contentIsEmpty())
        compose.onNodeWithText("Copy guest progress").performScrollTo().performClick()
        compose.onNodeWithText("Copy progress", substring = false).performClick()
        waitFor { HabitStore.get(app, uid).state.value.habits.size == 1 }
        val cloudAfterCopy = runBlocking { firestore.collection("spaces").document(uid).get().await() }
        assertEquals("Guest ritual", BackupCodec.decode(cloudAfterCopy.getString("state")!!).habits.single().name)
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
        compose.onNodeWithText("Let’s go").performClick()
        openAccount("Manage account")
        val ownerToken = runBlocking { auth.currentUser!!.getIdToken(false).await().token!! }
        compose.onNodeWithText("Delete my account").performScrollTo().performClick()
        compose.onNodeWithText("Confirm with password").performTextInput(password)
        compose.onNodeWithText("Delete permanently").performClick()
        waitFor { auth.currentUser == null && HabitStore.get(app, uid).state.value.habits.isEmpty() }
        assertFalse(cloudDocumentExists(uid, ownerToken))
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
        assertTrue(HabitStore.get(app).state.value.onboarded)
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

    @Test fun firestoreReconciliationKeepsIndependentEditsAndPropagatesDeletion() = runBlocking {
        action { vm.createAccount("Sync tester", email, password) }
        val uid = auth.currentUser!!.uid
        val firstStamp = System.currentTimeMillis()
        val reading = Habit(id = "sync-read", name = "Read", goal = "Ten pages")
        val baseline = HabitState(habits = listOf(reading)).recordChangesFrom(HabitState(), firstStamp)
        val seeded = SpaceSync.reconcile(uid, baseline, firstStamp)

        val walking = Habit(id = "sync-walk", name = "Walk", goal = "Twenty minutes")
        val phone = seeded.state.copy(habits = seeded.state.habits + walking)
            .recordChangesFrom(seeded.state, seeded.updatedAt + 1)
        val tablet = seeded.state.copy(journal = listOf(Reflection(LocalDate.now(), 4, "A separate tablet note")))
            .recordChangesFrom(seeded.state, seeded.updatedAt + 2)
        SpaceSync.reconcile(uid, phone, seeded.updatedAt + 1)
        val combined = SpaceSync.reconcile(uid, tablet, seeded.updatedAt + 2)
        assertEquals(setOf("sync-read", "sync-walk"), combined.state.habits.map { it.id }.toSet())
        assertEquals("A separate tablet note", combined.state.journal.single().text)

        val deleted = combined.state.copy(habits = combined.state.habits.filterNot { it.id == walking.id })
            .recordChangesFrom(combined.state, combined.updatedAt + 1)
        val afterDelete = SpaceSync.reconcile(uid, deleted, combined.updatedAt + 1)
        val staleDevice = SpaceSync.reconcile(uid, phone, afterDelete.updatedAt + 1)
        assertNull(staleDevice.state.habits.find { it.id == walking.id })
        assertEquals(afterDelete.state.sync.habitDeletions[walking.id], staleDevice.state.sync.habitDeletions[walking.id])

        val emptied = staleDevice.state.copy(habits = emptyList(), checks = emptyMap(), journal = emptyList())
            .recordChangesFrom(staleDevice.state, staleDevice.updatedAt + 1)
        val emptyRemote = SpaceSync.reconcile(uid, emptied, staleDevice.updatedAt + 1)
        val copiedAgain = SpaceSync.createIfEmpty(
            uid,
            HabitState(habits = listOf(walking), name = "Guest again"),
            emptyRemote.updatedAt + 1
        )!!
        assertEquals("sync-walk", copiedAgain.state.habits.single().id)
        assertFalse(copiedAgain.state.sync.habitDeletions.containsKey("sync-walk"))
        assertTrue(copiedAgain.state.sync.habitDeletions.containsKey("sync-read"))

        action { vm.deleteAccount(app, password) }
    }

    private fun oobCode(type: String): String {
        val result = URL("http://10.0.2.2:9099/emulator/v1/projects/demo-kimi-auth/oobCodes").readText()
        val codes = JSONObject(result).getJSONArray("oobCodes")
        return (0 until codes.length()).map { codes.getJSONObject(it) }
            .last { it.getString("email") == email && it.getString("requestType") == type }.getString("oobCode")
    }

    private fun cloudDocumentExists(uid: String, ownerToken: String): Boolean {
        val connection = URL("http://10.0.2.2:8080/v1/projects/demo-kimi-auth/databases/(default)/documents/spaces/$uid")
            .openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer $ownerToken")
        return when (connection.responseCode) {
            HttpURLConnection.HTTP_OK -> true
            HttpURLConnection.HTTP_NOT_FOUND -> false
            else -> error("Unexpected Firestore response ${connection.responseCode}: ${connection.errorStream?.bufferedReader()?.readText()}")
        }.also { connection.disconnect() }
    }
}
