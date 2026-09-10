package com.forma.habits

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One store for both the UI and notification actions. Writes are serialized and durable. */
class HabitStore private constructor(context: Context, val owner: String) {
    private val prefs = context.getSharedPreferences(AccountSession.preferenceName("forma", owner), Context.MODE_PRIVATE)
    private val mutex = Mutex()
    var recoveryNotice: Int? = null
        private set
    var damaged = false
        private set
    private val mutableState = MutableStateFlow(load())
    val state = mutableState.asStateFlow()

    private fun load(): HabitState {
        val raw = prefs.getString("state", null) ?: return HabitState(onboarded = false)
        return runCatching { BackupCodec.decode(raw) }.getOrElse {
            val backup = prefs.getString("previous_good", null)?.let { saved -> runCatching { BackupCodec.decode(saved) }.getOrNull() }
            damaged = backup == null
            recoveryNotice = if (backup != null) R.string.notice_recovered else R.string.notice_damaged
            backup ?: HabitState()
        }
    }

    suspend fun update(replaceDamaged: Boolean = false, eraseHistory: Boolean = false, transform: (HabitState) -> HabitState): HabitState = withContext(Dispatchers.IO) {
        mutex.withLock {
            demand(!damaged || replaceDamaged, R.string.err_damaged_locked)
            val next = transform(mutableState.value)
            val json = BackupCodec.encode(next)
            demand(json.toByteArray().size <= BackupCodec.MAX_BYTES, R.string.err_space_full)
            val previous = prefs.getString("state", null)
            val editor = prefs.edit().putString("state", json)
            if (eraseHistory) editor.remove("previous_good").remove("damaged_state")
            else if (previous != null) {
                if (runCatching { BackupCodec.decode(previous) }.isSuccess) editor.putString("previous_good", previous)
                else editor.putString("damaged_state", previous)
            }
            if (!editor.commit()) throw KimiMessage(R.string.err_disk_write)
            damaged = false
            mutableState.value = next
            next
        }
    }

    suspend fun erase() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!prefs.edit().clear().commit()) throw KimiMessage(R.string.err_erase_failed)
            damaged = false; recoveryNotice = null
            mutableState.value = HabitState(onboarded = false)
        }
    }

    companion object {
        private val instances = mutableMapOf<String, HabitStore>()
        @Synchronized fun get(context: Context, owner: String = AccountSession.owner): HabitStore =
            instances.getOrPut(owner) { HabitStore(context.applicationContext, owner) }
    }
}
