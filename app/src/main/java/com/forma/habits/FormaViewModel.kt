package com.forma.habits

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import androidx.core.content.edit
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

class FormaViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application get() = getApplication()
    private fun text(resId: Int, vararg args: Any) = app.getString(resId, *args)
    private val store = HabitStore.get(application)
    private val draftPrefs = application.getSharedPreferences(AccountSession.preferenceName("kimi_drafts", store.owner), 0)
    private val messages = Channel<String>(Channel.UNLIMITED)
    val events = messages.receiveAsFlow()
    var state by mutableStateOf(store.state.value)
        private set
    var busy by mutableStateOf(false)
        private set
    private var tasks = 0
    private fun beginTask() { tasks++; busy = true }
    private fun endTask() { tasks--; busy = tasks > 0 }
    var today by mutableStateOf(LocalDate.now())
        private set
    var draftDate by mutableStateOf(today)
        private set
    var draftMood by mutableIntStateOf(2)
        private set
    var draftText by mutableStateOf("")
        private set
    var pendingRestore by mutableStateOf<HabitState?>(null)
        private set
    var syncing by mutableStateOf(false)
        private set
    var syncFailed by mutableStateOf(false)
        private set
    var syncError by mutableStateOf<String?>(null)
        private set
    private var syncRequested = false
    private var syncJob: Job? = null
    private val connectivity = application.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch { if (syncFailed) syncNow() }
        }
    }
    /** Sync exists only for a signed-in space; guests stay entirely local. */
    val syncable get() = store.owner.isNotEmpty()

    /** Earlier days with an unsaved journal draft, newest first. Kept as state so composition never reads prefs. */
    var draftDates by mutableStateOf<List<LocalDate>>(emptyList())
        private set

    init {
        loadDraft(today)
        refreshDraftDates()
        store.takeRecoveryNotice()?.let { tell(text(it)) }
        viewModelScope.launch { store.state.drop(1).collect { state = it; syncNow() } }
        viewModelScope.launch(Dispatchers.IO) { Reminders.reschedule(application, state, force = true, owner = store.owner) }
        if (syncable) runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }
        syncNow()
    }

    override fun onCleared() {
        if (syncable) runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }

    /**
     * Pulls the account's copy, merges it with this device's, and writes the result back to both.
     *
     * Merging rather than overwriting is the whole point - see [mergeSpaces]. A failure here is
     * never fatal: the local space is authoritative and usable offline, so a failed sync only sets
     * a flag for the UI and is retried when connectivity returns, on the next change, or on resume.
     */
    fun syncNow() {
        if (!syncable) return
        syncRequested = true
        if (syncJob?.isActive == true) return
        syncing = true
        syncJob = viewModelScope.launch {
            try {
                do {
                    syncRequested = false
                    try {
                        reconcileOnce()
                        syncFailed = false
                        syncError = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (error: Exception) {
                        syncFailed = true
                        syncError = app.kimiMessage(error, R.string.err_sync_failed)
                    }
                } while (syncRequested)
            } finally {
                syncing = false
                syncJob = null
                // A request can arrive after the loop condition but before cleanup finishes.
                if (syncRequested) syncNow()
            }
        }
    }

    private suspend fun reconcileOnce() {
        val uid = store.owner
        val local = store.snapshot()
        val reconciled = SpaceSync.reconcile(uid, local.state, local.updatedAt)
        if (reconciled.state == local.state && reconciled.updatedAt == local.updatedAt) return
        val applied = store.replaceIfUnchanged(local, reconciled.state, reconciled.updatedAt)
        if (applied == null) {
            // A local edit landed while Firestore was in flight. Never overwrite it; merge again.
            syncRequested = true
            return
        }
        state = applied.state
        if (applied.state != local.state) {
            withContext(Dispatchers.IO) { Reminders.reschedule(getApplication(), applied.state, owner = uid) }
        }
    }

    fun tell(message: String) { messages.trySend(message) }
    /** A null [message] saves quietly; failures are still reported. */
    private fun change(message: String?, replaceDamaged: Boolean = false, eraseHistory: Boolean = false, onComplete: (Boolean) -> Unit = {}, transform: (HabitState) -> HabitState) {
        beginTask()
        viewModelScope.launch {
            try {
                state = store.update(replaceDamaged, eraseHistory, transform = transform)
                onComplete(true)
                message?.let(::tell)
                withContext(Dispatchers.IO) { Reminders.reschedule(getApplication(), state, owner = store.owner) }
                syncNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onComplete(false)
                tell(app.kimiMessage(e))
            } finally { endTask() }
        }
    }
    /**
     * Follows the calendar day. [sync] is for resume; the once-a-minute tick passes false so it only
     * reaches Firestore when the day actually rolls over, since every reconcile downloads the whole space.
     */
    fun refreshDate(sync: Boolean = true) {
        val now = LocalDate.now()
        val rolledOver = today != now
        if (rolledOver) { today = now; loadDraft(now); refreshDraftDates() }
        viewModelScope.launch(Dispatchers.IO) { Reminders.reschedule(getApplication(), store.state.value, owner = store.owner) }
        if (sync || rolledOver) syncNow()
    }
    fun toggle(habit: Habit, date: LocalDate) {
        if (date != LocalDate.now() || !habit.isDue(date)) return
        // The row's own tick is the confirmation, so a tap needs no snackbar.
        change(null) { it.checked(habit.id, date, !it.done(habit.id, date)) }
    }
    fun saveHabit(habit: Habit, onComplete: (Boolean) -> Unit) {
        // The editor closing is the confirmation.
        change(null, onComplete = onComplete) { current ->
            val old = current.habits.find { it.id == habit.id }
            demand(old != null || current.habits.size < 500, R.string.err_collection_full)
            val saved = if (old == null) habit.copy(created = LocalDate.now()) else habit.editedFrom(old, LocalDate.now())
            BackupCodec.validateHabit(saved)
            current.copy(habits = if (old != null) current.habits.map { if (it.id == saved.id) saved else it } else current.habits + saved)
        }
    }
    fun deleteHabit(id: String, onSaved: () -> Unit = {}) = change(text(R.string.msg_habit_removed), onComplete = { if (it) onSaved() }) {
        it.copy(habits = it.habits.filterNot { h -> h.id == id }, checks = it.checks.mapValues { entry -> entry.value - id }.filterValues { ids -> ids.isNotEmpty() })
    }
    fun reflect(mood: Int, text: String) {
        val date = draftDate
        change(text(R.string.msg_reflection_saved), onComplete = { saved ->
            if (saved && draftDate == date && draftMood == mood && draftText == text) {
                draftPrefs.edit { remove(date.toString()) }
                refreshDraftDates()
            }
        }) {
            demand(mood in 0..4 && text.isNotBlank() && text.length <= 10000, R.string.err_reflection_invalid)
            it.copy(journal = it.journal.filterNot { e -> e.date == date } + Reflection(date, mood, text.trim()))
        }
    }
    fun deleteReflection(date: LocalDate) = change(text(R.string.msg_reflection_deleted), onComplete = { saved ->
        if (saved) {
            draftPrefs.edit { remove(date.toString()) }
            refreshDraftDates()
            if (date == draftDate) { draftMood = 2; draftText = "" }
        }
    }) { it.copy(journal = it.journal.filterNot { e -> e.date == date }) }
    fun rename(name: String, onComplete: (Boolean) -> Unit = {}) = change(text(R.string.msg_renamed, name.trim()), onComplete = onComplete) {
        demand(name.isNotBlank() && name.trim().length <= 30, R.string.err_name_length)
        it.copy(name = name.trim())
    }
    fun start(name: String, onStarted: () -> Unit = {}) = change(text(R.string.msg_welcome), onComplete = { if (it) onStarted() }) {
        demand(name.isNotBlank() && name.trim().length <= 30, R.string.err_name_length)
        HabitState(name = name.trim(), onboarded = true)
    }

    fun updateDraft(mood: Int, text: String) {
        draftMood = mood.coerceIn(0, 4); draftText = text.take(10000)
        draftPrefs.edit { putString(draftDate.toString(), JSONObject().put("mood", draftMood).put("text", draftText).toString()) }
        if (draftDate != today && draftDate !in draftDates) draftDates = (draftDates + draftDate).sortedDescending()
    }
    fun loadDraft(date: LocalDate) {
        draftDate = date
        val existing = state.journal.find { it.date == date }
        val draft = draftPrefs.getString(date.toString(), null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        draftMood = (draft?.optInt("mood", 2) ?: existing?.mood ?: 2).coerceIn(0, 4)
        draftText = draft?.optString("text") ?: existing?.text.orEmpty()
    }
    private fun refreshDraftDates() {
        draftDates = draftPrefs.all.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .filter { it != today && !draftPrefs.getString(it.toString(), null).isNullOrBlank() }.sortedDescending()
    }

    fun readBackup(uri: Uri) {
        if (busy) return
        beginTask()
        viewModelScope.launch {
            try {
                val restored = withContext(Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) { val n = input.read(buffer); if (n < 0) break
                            demand(output.size() + n <= BackupCodec.MAX_BYTES, R.string.err_backup_too_large)
                            output.write(buffer, 0, n)
                        }
                        output.toByteArray()
                    } ?: throw KimiMessage(R.string.err_file_read)
                    BackupCodec.decode(bytes.toString(Charsets.UTF_8))
                }
                pendingRestore = restored.copy(onboarded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                tell(text(R.string.err_backup_read))
            } finally {
                endTask()
            }
        }
    }
    fun cancelRestore() { pendingRestore = null }
    fun confirmRestore() {
        val restored = pendingRestore ?: return
        change(text(R.string.msg_restored), replaceDamaged = true, onComplete = { saved ->
            if (saved) { pendingRestore = null; draftPrefs.edit { clear() }; loadDraft(today); refreshDraftDates() }
        }) { restored.adoptingRevisionsOf(it) }
    }
}
