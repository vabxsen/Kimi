package com.forma.habits

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

class FormaViewModel(application: Application) : AndroidViewModel(application) {
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
    val damaged get() = store.damaged

    init {
        loadDraft(today)
        store.recoveryNotice?.let(::tell)
        viewModelScope.launch { store.state.collect { state = it } }
        viewModelScope.launch(Dispatchers.IO) { Reminders.reschedule(application, state, force = true, owner = store.owner) }
    }

    fun tell(message: String) { messages.trySend(message) }
    private fun change(message: String, replaceDamaged: Boolean = false, eraseHistory: Boolean = false, onSaved: () -> Unit = {}, transform: (HabitState) -> HabitState) {
        beginTask()
        viewModelScope.launch {
            try {
                state = store.update(replaceDamaged, eraseHistory, transform)
                onSaved()
                tell(message)
                withContext(Dispatchers.IO) { Reminders.reschedule(getApplication(), state, owner = store.owner) }
            } catch (e: Exception) {
                tell(e.message ?: "That change could not be saved. Please try again.")
            } finally { endTask() }
        }
    }
    fun refreshDate() {
        val now = LocalDate.now()
        if (today != now) { today = now; loadDraft(now) }
        viewModelScope.launch(Dispatchers.IO) { Reminders.reschedule(getApplication(), store.state.value, owner = store.owner) }
    }
    fun toggle(habit: Habit, date: LocalDate) {
        if (date.isAfter(LocalDate.now()) || !habit.isDue(date)) return
        val wasDone = state.done(habit.id, date)
        change(if (wasDone) "Check-in undone. Fresh starts are allowed." else "Tiny win. Big high-five!") {
            it.checked(habit.id, date, !it.done(habit.id, date))
        }
    }
    fun saveHabit(habit: Habit, onSaved: () -> Unit) {
        change("Your habit is saved. Make it work for you.", onSaved = onSaved) { current ->
            val old = current.habits.find { it.id == habit.id }
            require(old != null || current.habits.size < 500) { "Your collection is full. Remove a habit before adding another." }
            val saved = if (old == null) habit.copy(created = LocalDate.now()) else habit.editedFrom(old, LocalDate.now())
            BackupCodec.validateHabit(saved)
            current.copy(habits = if (old != null) current.habits.map { if (it.id == saved.id) saved else it } else current.habits + saved)
        }
    }
    fun deleteHabit(id: String, onSaved: () -> Unit = {}) = change("Habit removed.", onSaved = onSaved) {
        it.copy(habits = it.habits.filterNot { h -> h.id == id }, checks = it.checks.mapValues { entry -> entry.value - id }.filterValues { ids -> ids.isNotEmpty() })
    }
    fun reflect(mood: Int, text: String) {
        val date = draftDate
        change("Your little moment is saved.", onSaved = {
            if (draftDate == date && draftMood == mood && draftText == text) draftPrefs.edit().remove(date.toString()).apply()
        }) {
            require(mood in 0..4 && text.isNotBlank() && text.length <= 10000)
            it.copy(journal = it.journal.filterNot { e -> e.date == date } + Reflection(date, mood, text.trim()))
        }
    }
    fun deleteReflection(date: LocalDate) = change("Reflection deleted.", onSaved = {
        draftPrefs.edit().remove(date.toString()).apply()
        if (date == draftDate) { draftMood = 2; draftText = "" }
    }) { it.copy(journal = it.journal.filterNot { e -> e.date == date }) }
    fun rename(name: String) = change("Looking good, ${name.trim()}!") {
        require(name.isNotBlank() && name.trim().length <= 30)
        it.copy(name = name.trim())
    }
    fun reset() = change("A blank page. A brand-new beginning.", replaceDamaged = true, eraseHistory = true, onSaved = {
        draftPrefs.edit().clear().apply(); loadDraft(today)
    }) { HabitState(name = it.name) }
    fun start(name: String, starters: Boolean) = change("Welcome to your little happy place!") {
        require(name.isNotBlank() && name.trim().length <= 30)
        HabitState(name = name.trim(), habits = if (starters) starterHabits() else emptyList())
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
            runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(BackupCodec.encode(store.state.value).toByteArray(Charsets.UTF_8))
                    } ?: error("The chosen file cannot be written.")
                }
            }.onSuccess { tell("Your Kimi backup is saved.") }.onFailure { tell("Backup could not be saved. Try another location.") }
        }
    }
    fun readBackup(uri: Uri) {
        if (busy) return
        beginTask()
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) { val n = input.read(buffer); if (n < 0) break
                            require(output.size() + n <= BackupCodec.MAX_BYTES) { "Backup is larger than 8 MB." }
                            output.write(buffer, 0, n)
                        }
                        output.toByteArray()
                    } ?: error("Cannot read this file.")
                    BackupCodec.decode(bytes.toString(Charsets.UTF_8))
                }
            }.onSuccess { pendingRestore = it.copy(onboarded = true) }
                .onFailure { tell("This backup could not be read. Choose a valid Kimi JSON backup (up to 8 MB). Your current data is unchanged.") }
            endTask()
        }
    }
    fun cancelRestore() { pendingRestore = null }
    fun confirmRestore() {
        val restored = pendingRestore ?: return
        change("Your space is restored. Welcome back!", replaceDamaged = true, onSaved = {
            pendingRestore = null; draftPrefs.edit().clear().apply(); loadDraft(today)
        }) { restored }
    }
}
