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
    data class Snapshot(val state: HabitState, val updatedAt: Long)

    private val prefs = context.getSharedPreferences(AccountSession.preferenceName("forma", owner), Context.MODE_PRIVATE)
    private val mutex = Mutex()
    var recoveryNotice: Int? = null
        private set
    var damaged = false
        private set
    private val mutableState = MutableStateFlow(load())
    val state = mutableState.asStateFlow()

    /** Wall-clock millis of the last local write. Prefer [snapshot] when state and revision must agree. */
    val updatedAt: Long get() = prefs.getLong("updated_at", 0L)

    private fun load(): HabitState {
        val raw = prefs.getString("state", null) ?: return HabitState(onboarded = false)
        return runCatching { BackupCodec.decode(raw) }.getOrElse {
            val backup = prefs.getString("previous_good", null)?.let { saved -> runCatching { BackupCodec.decode(saved) }.getOrNull() }
            damaged = backup == null
            recoveryNotice = if (backup != null) R.string.notice_recovered else R.string.notice_damaged
            backup ?: HabitState()
        }
    }

    suspend fun snapshot(): Snapshot = withContext(Dispatchers.IO) {
        mutex.withLock { Snapshot(mutableState.value, updatedAt) }
    }

    suspend fun update(replaceDamaged: Boolean = false, eraseHistory: Boolean = false, stamp: Long = System.currentTimeMillis(), transform: (HabitState) -> HabitState): HabitState = withContext(Dispatchers.IO) {
        mutex.withLock {
            demand(!damaged || replaceDamaged, R.string.err_damaged_locked)
            val previousRevision = updatedAt
            val current = mutableState.value.withBaselineRevisions(previousRevision)
            val revision = monotonicStamp(stamp, previousRevision)
            persist(transform(current).recordChangesFrom(current, revision), revision, eraseHistory)
        }
    }

    /** Applies a remote reconciliation only if no local write happened after [expected]. */
    suspend fun replaceIfUnchanged(expected: Snapshot, replacement: HabitState, stamp: Long): Snapshot? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (mutableState.value != expected.state || updatedAt != expected.updatedAt || stamp < expected.updatedAt) return@withLock null
            if (replacement == expected.state && stamp == expected.updatedAt) return@withLock expected
            Snapshot(persist(replacement, stamp, eraseHistory = false), stamp)
        }
    }

    private fun monotonicStamp(requested: Long, current: Long): Long {
        val afterCurrent = if (current == Long.MAX_VALUE) Long.MAX_VALUE else current + 1
        return maxOf(1L, requested, afterCurrent)
    }

    private fun persist(next: HabitState, stamp: Long, eraseHistory: Boolean): HabitState {
        val json = BackupCodec.encode(next)
        demand(json.toByteArray(Charsets.UTF_8).size <= BackupCodec.MAX_BYTES, R.string.err_space_full)
        val previous = prefs.getString("state", null)
        val editor = prefs.edit().putString("state", json).putLong("updated_at", stamp)
        if (eraseHistory) editor.remove("previous_good").remove("damaged_state")
        else if (previous != null) {
            if (runCatching { BackupCodec.decode(previous) }.isSuccess) editor.putString("previous_good", previous)
            else editor.putString("damaged_state", previous)
        }
        if (!editor.commit()) throw KimiMessage(R.string.err_disk_write)
        damaged = false
        mutableState.value = next
        return next
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
