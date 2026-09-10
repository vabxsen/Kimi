package com.forma.habits

import android.app.Application
import android.net.Uri
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
    private var syncRequested = false
    private var syncJob: Job? = null
    /** Sync exists only for a signed-in space; guests stay entirely local. */
    val syncable get() = store.owner.isNotEmpty()
    val damaged get() = store.damaged

    init {
        loadDraft(today)
        store.recoveryNotice?.let { tell(text(it)) }
        viewModelScope.launch { store.state.drop(1).collect { state = it; syncNow() } }
        viewModelScope.launch(Dispatchers.IO) { Reminders.reschedule(application, state, force = true, owner = store.owner) }
        syncNow()
    }

    /**
     * Pulls the account's copy, merges it with this device's, and writes the result back to both.
     *
     * Merging rather than overwriting is the whole point - see [mergeSpaces]. A failure here is
     * never fatal: the local space is authoritative and usable offline, so a failed sync only sets
     * a flag for the UI and is retried on the next change or resume.
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
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        syncFailed = true
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
    private fun change(message: String, replaceDamaged: Boolean = false, eraseHistory: Boolean = false, onSaved: () -> Unit = {}, transform: (HabitState) -> HabitState) {
        beginTask()
        viewModelScope.launch {
            try {
                state = store.update(replaceDamaged, eraseHistory, transform = transform)
                onSaved()
                tell(message)
                withContext(Dispatchers.IO) { Reminders.reschedule(getApplication(), state, owner = store.owner) }
                syncNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                tell(app.kimiMessage(e))
            } finally { endTask() }
        }
    }
    fun refreshDate() {
        val now = LocalDate.now()
        if (today != now) { today = now; loadDraft(now) }
        viewModelScope.launch(Dispatchers.IO) { Reminders.reschedule(getApplication(), store.state.value, owner = store.owner) }
        syncNow()
    }
    fun toggle(habit: Habit, date: LocalDate) {
        if (date.isAfter(LocalDate.now()) || !habit.isDue(date)) return
        val wasDone = state.done(habit.id, date)
        change(text(if (wasDone) R.string.msg_unchecked else R.string.msg_checked)) {
            it.checked(habit.id, date, !it.done(habit.id, date))
        }
    }
    fun saveHabit(habit: Habit, onSaved: () -> Unit) {
        change(text(R.string.msg_habit_saved), onSaved = onSaved) { current ->
            val old = current.habits.find { it.id == habit.id }
            demand(old != null || current.habits.size < 500, R.string.err_collection_full)
            val saved = if (old == null) habit.copy(created = LocalDate.now()) else habit.editedFrom(old, LocalDate.now())
            BackupCodec.validateHabit(saved)
            current.copy(habits = if (old != null) current.habits.map { if (it.id == saved.id) saved else it } else current.habits + saved)
        }
    }
    fun deleteHabit(id: String, onSaved: () -> Unit = {}) = change(text(R.string.msg_habit_removed), onSaved = onSaved) {
        it.copy(habits = it.habits.filterNot { h -> h.id == id }, checks = it.checks.mapValues { entry -> entry.value - id }.filterValues { ids -> ids.isNotEmpty() })
    }
    fun reflect(mood: Int, text: String) {
        val date = draftDate
        change(text(R.string.msg_reflection_saved), onSaved = {
            if (draftDate == date && draftMood == mood && draftText == text) draftPrefs.edit().remove(date.toString()).apply()
        }) {
            demand(mood in 0..4 && text.isNotBlank() && text.length <= 10000, R.string.err_reflection_invalid)
            it.copy(journal = it.journal.filterNot { e -> e.date == date } + Reflection(date, mood, text.trim()))
        }
    }
    fun deleteReflection(date: LocalDate) = change(text(R.string.msg_reflection_deleted), onSaved = {
        draftPrefs.edit().remove(date.toString()).apply()
        if (date == draftDate) { draftMood = 2; draftText = "" }
    }) { it.copy(journal = it.journal.filterNot { e -> e.date == date }) }
    fun rename(name: String) = change(text(R.string.msg_renamed, name.trim())) {
        demand(name.isNotBlank() && name.trim().length <= 30, R.string.err_name_length)
        it.copy(name = name.trim())
    }
    fun reset() = change(text(R.string.msg_reset), replaceDamaged = true, eraseHistory = true, onSaved = {
        draftPrefs.edit().clear().apply(); loadDraft(today)
    }) { HabitState(name = it.name) }
    fun start(name: String, starters: Boolean) = change(text(R.string.msg_welcome)) {
        demand(name.isNotBlank() && name.trim().length <= 30, R.string.err_name_length)
        val seeds = starterHabits(LocalDate.now(),
            app.resources.getStringArray(R.array.starter_names).toList(),
            app.resources.getStringArray(R.array.starter_goals).toList())
        HabitState(name = name.trim(), habits = if (starters) seeds else emptyList())
    }

    fun updateDraft(mood: Int, text: String) {
        draftMood = mood.coerceIn(0, 4); draftText = text.take(10000)
        draftPrefs.edit().putString(draftDate.toString(), JSONObject().put("mood", draftMood).put("text", draftText).toString()).apply()
    }
    fun loadDraft(date: LocalDate) {
        draftDate = date
        val existing = state.journal.find { it.date == date }
        val draft = draftPrefs.getString(date.toString(), null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        draftMood = (draft?.optInt("mood", 2) ?: existing?.mood ?: 2).coerceIn(0, 4)
        draftText = draft?.optString("text") ?: existing?.text.orEmpty()
    }
    fun draftDates(): List<LocalDate> = draftPrefs.all.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .filter { it != today && !draftPrefs.getString(it.toString(), null).isNullOrBlank() }.sortedDescending()

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(BackupCodec.encode(store.state.value).toByteArray(Charsets.UTF_8))
                    } ?: throw KimiMessage(R.string.err_file_write)
                }
                tell(text(R.string.msg_backup_saved))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                tell(text(R.string.err_backup_save))
            }
        }
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
        change(text(R.string.msg_restored), replaceDamaged = true, onSaved = {
            pendingRestore = null; draftPrefs.edit().clear().apply(); loadDraft(today)
        }) { restored }
    }
}
