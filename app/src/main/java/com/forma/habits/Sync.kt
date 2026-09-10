package com.forma.habits

/**
 * Merging two copies of a space.
 *
 * Kimi stores a whole space as one serialized blob, so the naive sync — upload the blob, newest
 * wins — would silently discard work: write a reflection on a phone that is offline, check a habit
 * on a tablet, let the tablet sync last, and the reflection is gone. Journal entries are not
 * recoverable, so that is not an acceptable failure mode.
 *
 * Instead both sides are merged entry by entry. Habits are keyed by id, check-ins and reflections
 * by date, and where the same key exists on both sides the copy from whichever space was written
 * more recently wins. Nothing a user has written is ever dropped.
 *
 * The cost of keeping this simple is that **removals are not tracked**. A deleted habit, a deleted
 * reflection or an undone check-in can be reintroduced by a device that had not seen the removal,
 * because an absent entry is indistinguishable from one that has not arrived yet. Telling those
 * apart needs tombstones, which is a much larger change. Repeating a deletion is a mild annoyance;
 * losing a journal entry is not, so the merge errs towards keeping things.
 */
fun mergeSpaces(local: HabitState, localAt: Long, remote: HabitState, remoteAt: Long): HabitState {
    val remoteIsNewer = remoteAt >= localAt
    val newer = if (remoteIsNewer) remote else local
    val older = if (remoteIsNewer) local else remote

    // Union by key, with `newer` applied second so it wins any collision.
    val habits = (older.habits.associateBy { it.id } + newer.habits.associateBy { it.id })
        .values.sortedBy { it.created }
    val known = habits.map { it.id }.toSet()

    val checks = (older.checks.keys + newer.checks.keys).mapNotNull { date ->
        // Union, for the same reason as above: a check-in is a positive record of something done,
        // and no sync should erase one. Check-ins referring to a habit neither side still has are
        // dropped, since BackupCodec rejects a space whose check-ins point at unknown habits.
        val ids = (older.checks[date].orEmpty() + newer.checks[date].orEmpty()).filter { it in known }.toSet()
        if (ids.isEmpty()) null else date to ids
    }.toMap()

    val journal = (older.journal.associateBy { it.date } + newer.journal.associateBy { it.date })
        .values.sortedBy { it.date }

    return HabitState(
        habits = habits,
        checks = checks,
        journal = journal,
        name = newer.name,
        demo = newer.demo,
        // Onboarding is one-way: if either side has been through it, the merged space has too.
        onboarded = newer.onboarded || older.onboarded
    )
}
