package com.forma.habits

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads and writes one Firestore document per signed-in user, holding that user's whole space.
 *
 * The space is stored as the same versioned JSON [BackupCodec] already produces, rather than being
 * exploded into per-habit documents. That keeps the durable local store — which is the careful part
 * of this app — completely untouched, and means anything the codec can validate is exactly what
 * travels. [mergeSpaces] does the reconciling.
 *
 * Security rules in `firestore.rules` allow a user to touch `spaces/{uid}` only when their own uid
 * matches, and deny everything else outright.
 */
object SpaceSync {
    private const val COLLECTION = "spaces"
    private val ownerLocks = ConcurrentHashMap<String, Mutex>()
    private val deletingOwners = ConcurrentHashMap.newKeySet<String>()

    /** Firestore caps a document at 1 MiB; leave room for field names and overhead. */
    const val MAX_BYTES = 900_000

    internal var testFirebaseAppName: String? = null
    private val database get() = testFirebaseAppName?.let { FirebaseFirestore.getInstance(FirebaseApp.getInstance(it)) }
        ?: FirebaseFirestore.getInstance()
    private fun document(uid: String) = database.collection(COLLECTION).document(uid)
    private fun ownerLock(uid: String) = ownerLocks.computeIfAbsent(uid) { Mutex() }

    /**
     * Instrumented tests sign in against the isolated auth emulator. Without this guard they would
     * reach straight past it into the real project's Firestore.
     */
    private val disabled get() = AccountSession.testAuth != null && testFirebaseAppName == null

    data class Remote(val state: HabitState, val updatedAt: Long)

    private fun decodedRemote(raw: String?, updatedAt: Long?): Remote? {
        if (raw == null) return null
        val revision = updatedAt ?: 0L
        if (revision < 0 || revision == Long.MAX_VALUE) return null
        return runCatching { Remote(BackupCodec.decode(raw), revision) }.getOrNull()
    }

    private fun nextRevision(a: Long, b: Long): Long {
        fun after(value: Long) = if (value == Long.MAX_VALUE) Long.MAX_VALUE else value + 1
        return maxOf(System.currentTimeMillis(), after(a), after(b), 1L)
    }

    /**
     * Atomically merges the local snapshot with the latest cloud document. Firestore retries the
     * transaction if another device writes between the read and write, preventing last-writer data
     * loss even when two devices sync at the same time.
     */
    suspend fun reconcile(uid: String, state: HabitState, updatedAt: Long): Remote {
        if (disabled || uid.isEmpty()) return Remote(state, updatedAt)
        return ownerLock(uid).withLock {
            if (uid in deletingOwners) return@withLock Remote(state, updatedAt)
            val ref = document(uid)
            database.runTransaction { transaction ->
                val snapshot = transaction.get(ref)
                val remote = decodedRemote(snapshot.getString("state"), snapshot.getLong("updatedAt"))
                val merged = remote?.let { mergeSpaces(state, updatedAt, it.state, it.updatedAt) } ?: state
                val shouldWrite = remote == null || merged != remote.state || (merged == state && updatedAt > remote.updatedAt)
                if (!shouldWrite) remote else {
                    val revision = nextRevision(updatedAt, remote?.updatedAt ?: 0L)
                    val json = BackupCodec.encode(merged)
                    demand(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES, R.string.err_sync_too_large)
                    transaction.set(ref, mapOf("state" to json, "updatedAt" to revision, "format" to "kimi"))
                    Remote(merged, revision)
                }
            }.await()
        }
    }

    /** Writes a guest copy only while the cloud account still has no meaningful progress. */
    suspend fun createIfEmpty(uid: String, state: HabitState, updatedAt: Long): Remote? {
        if (disabled || uid.isEmpty()) return Remote(state, updatedAt)
        return ownerLock(uid).withLock {
            if (uid in deletingOwners) return@withLock null
            val ref = document(uid)
            database.runTransaction { transaction ->
                val snapshot = transaction.get(ref)
                val raw = snapshot.getString("state")
                val decoded = decodedRemote(raw, snapshot.getLong("updatedAt"))
                val existing = decoded?.state
                if (raw != null && (decoded == null || !decoded.state.contentIsEmpty())) null else {
                    val existingAt = decoded?.updatedAt ?: 0L
                    val revision = nextRevision(updatedAt, existingAt)
                    val metadata = existing?.let { mergeSpaces(state, updatedAt, it, existingAt).sync } ?: state.sync
                    val copied = state.copy(sync = metadata).recordChangesFrom(existing ?: HabitState(), revision)
                    val json = BackupCodec.encode(copied)
                    demand(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES, R.string.err_sync_too_large)
                    transaction.set(ref, mapOf("state" to json, "updatedAt" to revision, "format" to "kimi"))
                    Remote(copied, revision)
                }
            }.await()
        }
    }

    /** Called when an account is deleted, so the cloud copy goes with it. */
    suspend fun deleteSpace(uid: String) {
        if (disabled || uid.isEmpty()) return
        ownerLock(uid).withLock {
            deletingOwners += uid
            try { document(uid).delete().await() }
            catch (error: Exception) { deletingOwners -= uid; throw error }
        }
    }

    /** Account deletion failed after its space was removed; normal sync may safely resume. */
    fun resumeAfterFailedDeletion(uid: String) { deletingOwners -= uid }
}

internal fun HabitState.contentIsEmpty() = habits.isEmpty() && checks.isEmpty() && journal.isEmpty()
