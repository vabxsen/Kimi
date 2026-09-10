package com.forma.habits

/** Records every content change at the store's durable revision. */
internal fun HabitState.recordChangesFrom(previous: HabitState, at: Long): HabitState {
    require(at in 1 until Long.MAX_VALUE)
    val metadata = sync
    val habitUpdates = metadata.habitUpdates.toMutableMap()
    val habitDeletions = metadata.habitDeletions.toMutableMap()
    val checkUpdates = metadata.checkUpdates.toMutableMap()
    val checkDeletions = metadata.checkDeletions.toMutableMap()
    val reflectionUpdates = metadata.reflectionUpdates.toMutableMap()
    val reflectionDeletions = metadata.reflectionDeletions.toMutableMap()

    fun <T> record(
        before: Map<String, T>,
        after: Map<String, T>,
        updates: MutableMap<String, Long>,
        deletions: MutableMap<String, Long>
    ) {
        (before.keys + after.keys).forEach { key ->
            if (before[key] == after[key]) return@forEach
            if (after.containsKey(key)) {
                updates[key] = at
                deletions.remove(key)
            } else {
                deletions[key] = at
                updates.remove(key)
            }
        }
    }

    record(previous.habits.associateBy { it.id }, habits.associateBy { it.id }, habitUpdates, habitDeletions)
    record(previous.checkEntries(), checkEntries(), checkUpdates, checkDeletions)
    record(previous.journal.associateBy { it.date.toString() }, journal.associateBy { it.date.toString() }, reflectionUpdates, reflectionDeletions)

    return copy(sync = SyncMetadata(
        habitUpdates, habitDeletions,
        checkUpdates, checkDeletions,
        reflectionUpdates, reflectionDeletions
    ))
}

/** Gives legacy present entries their pre-edit revision before a new local write advances the space. */
internal fun HabitState.withBaselineRevisions(at: Long): HabitState {
    if (at <= 0) return this
    val habits = sync.habitUpdates.toMutableMap()
    this.habits.forEach { if (it.id !in sync.habitDeletions) habits.putIfAbsent(it.id, at) }
    val checks = sync.checkUpdates.toMutableMap()
    checkEntries().keys.forEach { if (it !in sync.checkDeletions) checks.putIfAbsent(it, at) }
    val reflections = sync.reflectionUpdates.toMutableMap()
    journal.forEach { if (it.date.toString() !in sync.reflectionDeletions) reflections.putIfAbsent(it.date.toString(), at) }
    return copy(sync = sync.copy(habitUpdates = habits, checkUpdates = checks, reflectionUpdates = reflections))
}

private fun HabitState.checkEntries(): Map<String, Boolean> = checks.flatMap { (date, ids) ->
    ids.map { id -> checkRevisionKey(date, id) to true }
}.toMap()

private data class EntryEvent<T>(val value: T?, val at: Long, val explicit: Boolean)
private data class MergedEntries<T>(
    val values: Map<String, T>,
    val updates: Map<String, Long>,
    val deletions: Map<String, Long>
)

private fun <T> event(value: T?, updatedAt: Long?, deletedAt: Long?, fallbackAt: Long): EntryEvent<T>? {
    val presentAt = if (value != null) updatedAt ?: fallbackAt else Long.MIN_VALUE
    val removedAt = deletedAt ?: Long.MIN_VALUE
    if (presentAt == Long.MIN_VALUE && removedAt == Long.MIN_VALUE) return null
    return if (removedAt >= presentAt) EntryEvent(null, removedAt, deletedAt != null)
    else EntryEvent(value, presentAt, updatedAt != null)
}

private fun <T> choose(local: EntryEvent<T>?, remote: EntryEvent<T>?, remoteWinsTie: Boolean): EntryEvent<T> = when {
    local == null -> requireNotNull(remote)
    remote == null -> local
    // A legacy/implicit presence cannot prove a re-creation after a recorded deletion.
    local.value == null && local.explicit && remote.value != null && !remote.explicit -> local
    remote.value == null && remote.explicit && local.value != null && !local.explicit -> remote
    local.at > remote.at -> local
    remote.at > local.at -> remote
    local.value == null && remote.value != null -> local
    remote.value == null && local.value != null -> remote
    remoteWinsTie -> remote
    else -> local
}

private fun <T> mergeEntries(
    local: Map<String, T>,
    localUpdates: Map<String, Long>,
    localDeletions: Map<String, Long>,
    localAt: Long,
    remote: Map<String, T>,
    remoteUpdates: Map<String, Long>,
    remoteDeletions: Map<String, Long>,
    remoteAt: Long,
    remoteWinsTie: Boolean
): MergedEntries<T> {
    val values = mutableMapOf<String, T>()
    val updates = mutableMapOf<String, Long>()
    val deletions = mutableMapOf<String, Long>()
    val keys = local.keys + remote.keys + localUpdates.keys + remoteUpdates.keys + localDeletions.keys + remoteDeletions.keys
    keys.forEach { key ->
        val winner = choose(
            event(local[key], localUpdates[key], localDeletions[key], localAt),
            event(remote[key], remoteUpdates[key], remoteDeletions[key], remoteAt),
            remoteWinsTie
        )
        winner.value?.let { values[key] = it }
        if (winner.explicit) {
            if (winner.value == null) deletions[key] = winner.at else updates[key] = winner.at
        }
    }
    return MergedEntries(values, updates, deletions)
}

/**
 * Merges two offline-capable spaces entry by entry. Version 3 backups carry per-entry revisions and
 * tombstones, while entries from older backups safely fall back to their whole-space revision.
 */
fun mergeSpaces(local: HabitState, localAt: Long, remote: HabitState, remoteAt: Long): HabitState {
    val remoteIsNewer = remoteAt >= localAt
    val newer = if (remoteIsNewer) remote else local
    val older = if (remoteIsNewer) local else remote

    val habits = mergeEntries(
        local.habits.associateBy { it.id }, local.sync.habitUpdates, local.sync.habitDeletions, localAt,
        remote.habits.associateBy { it.id }, remote.sync.habitUpdates, remote.sync.habitDeletions, remoteAt,
        remoteIsNewer
    )
    val known = habits.values.keys

    val checks = mergeEntries(
        local.checkEntries(), local.sync.checkUpdates, local.sync.checkDeletions, localAt,
        remote.checkEntries(), remote.sync.checkUpdates, remote.sync.checkDeletions, remoteAt,
        remoteIsNewer
    )
    val checkValues = checks.values.filterKeys { key -> key.substringAfter('/', "") in known }
    val checkUpdates = checks.updates.filterKeys { it in checkValues }
    val checkDeletions = checks.deletions.toMutableMap()
    (checks.values.keys - checkValues.keys).forEach { key ->
        checkDeletions[key] = maxOf(checks.updates[key] ?: 0L, localAt, remoteAt)
    }
    val groupedChecks = checkValues.keys.groupBy({ it.substringBefore('/') }, { it.substringAfter('/') })
        .mapValues { it.value.toSet() }

    val reflections = mergeEntries(
        local.journal.associateBy { it.date.toString() }, local.sync.reflectionUpdates, local.sync.reflectionDeletions, localAt,
        remote.journal.associateBy { it.date.toString() }, remote.sync.reflectionUpdates, remote.sync.reflectionDeletions, remoteAt,
        remoteIsNewer
    )
    val habitOrder = (newer.habits + older.habits).map { it.id }.distinct().withIndex()
        .associate { (index, id) -> id to index }

    return HabitState(
        habits = habits.values.values.sortedBy { habitOrder[it.id] ?: Int.MAX_VALUE },
        checks = groupedChecks,
        journal = reflections.values.values.sortedBy { it.date },
        name = newer.name,
        demo = newer.demo,
        onboarded = newer.onboarded || older.onboarded,
        sync = SyncMetadata(
            habitUpdates = habits.updates,
            habitDeletions = habits.deletions,
            checkUpdates = checkUpdates,
            checkDeletions = checkDeletions,
            reflectionUpdates = reflections.updates,
            reflectionDeletions = reflections.deletions
        )
    )
}
