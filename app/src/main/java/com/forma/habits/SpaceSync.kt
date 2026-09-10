package com.forma.habits

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

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

    /** Firestore caps a document at 1 MiB; leave room for field names and overhead. */
    const val MAX_BYTES = 900_000

    private fun document(uid: String) = FirebaseFirestore.getInstance().collection(COLLECTION).document(uid)

    /**
     * Instrumented tests sign in against the isolated auth emulator. Without this guard they would
     * reach straight past it into the real project's Firestore.
     */
    private val disabled get() = AccountSession.testAuth != null

    data class Remote(val state: HabitState, val updatedAt: Long)

    /** Returns null when the account has never synced, or when the stored copy is unreadable. */
    suspend fun pull(uid: String): Remote? {
        if (disabled || uid.isEmpty()) return null
        val snapshot = document(uid).get().await()
        val json = snapshot.getString("state") ?: return null
        val updatedAt = snapshot.getLong("updatedAt") ?: 0L
        // A corrupt remote copy must never take the local space down with it.
        return runCatching { Remote(BackupCodec.decode(json), updatedAt) }.getOrNull()
    }

    suspend fun push(uid: String, state: HabitState, updatedAt: Long) {
        if (disabled || uid.isEmpty()) return
        val json = BackupCodec.encode(state)
        demand(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES, R.string.err_sync_too_large)
        document(uid).set(mapOf("state" to json, "updatedAt" to updatedAt, "format" to "kimi")).await()
    }

    /** Called when an account is deleted, so the cloud copy goes with it. */
    suspend fun deleteSpace(uid: String) {
        if (disabled || uid.isEmpty()) return
        document(uid).delete().await()
    }
}
