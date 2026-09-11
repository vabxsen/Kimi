package com.forma.habits

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Versioned, portable backup. Decode completely before replacing any live data. */
object BackupCodec {
    const val MAX_BYTES = 8 * 1024 * 1024
    fun encode(state: HabitState): String = JSONObject().apply {
        put("format", "kimi"); put("version", 3)
        put("name", state.name); put("demo", state.demo); put("onboarded", state.onboarded)
        put("habits", JSONArray().apply { state.habits.forEach { h -> put(JSONObject().apply {
            put("id", h.id); put("name", h.name); put("goal", h.goal); put("icon", h.icon); put("color", h.color)
            put("time", h.time); put("weekdays", h.weekdays); put("created", h.created.toString())
            h.reminderMinutes?.let { put("reminderMinutes", it); put("reminderCount", h.reminderCount) }
            put("schedule", JSONArray().apply { h.schedule.forEach { change -> put(JSONObject().apply {
                put("from", change.from.toString()); put("weekdays", change.weekdays)
            }) } })
        }) } })
        put("checks", JSONObject().apply { state.checks.forEach { (date, ids) -> put(date, JSONArray(ids.toList().sorted())) } })
        put("journal", JSONArray().apply { state.journal.forEach { entry -> put(JSONObject().apply {
            put("date", entry.date.toString()); put("mood", entry.mood); put("text", entry.text)
        }) } })
        put("sync", JSONObject().apply {
            put("habitUpdates", state.sync.habitUpdates.timestamps())
            put("habitDeletions", state.sync.habitDeletions.timestamps())
            put("checkUpdates", state.sync.checkUpdates.timestamps())
            put("checkDeletions", state.sync.checkDeletions.timestamps())
            put("reflectionUpdates", state.sync.reflectionUpdates.timestamps())
            put("reflectionDeletions", state.sync.reflectionDeletions.timestamps())
        })
    }.toString(2)

    fun decode(raw: String): HabitState {
        demand(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES, R.string.err_backup_too_large)
        val json = JSONObject(raw.removePrefix("\uFEFF"))
        demand(json.optInt("version", 1) in 1..3, R.string.err_backup_version)
        demand(!json.has("format") || json.getString("format") == "kimi", R.string.err_backup_format)
        val habits = json.getJSONArray("habits").objects(500).map { h ->
            Habit(id = h.getString("id"), name = h.getString("name"), goal = h.getString("goal"),
                icon = h.getInt("icon"), color = h.getInt("color"), time = h.getString("time"),
                weekdays = h.getBoolean("weekdays"), created = date(h.getString("created")),
                reminderMinutes = if (h.has("reminderMinutes")) h.getInt("reminderMinutes") else null,
                reminderCount = h.optInt("reminderCount", 1),
                schedule = h.optJSONArray("schedule")?.objects(5000)?.map {
                    ScheduleChange(date(it.getString("from")), it.getBoolean("weekdays"))
                }.orEmpty()).also(::validateHabit)
        }
        val ids = habits.map { it.id }.toSet()
        demand(ids.size == habits.size, R.string.err_backup_duplicate_habits)
        val checks = json.getJSONObject("checks").let { j ->
            demand(j.length() <= 50000, R.string.err_backup_too_many_dates)
            j.keys().asSequence().associateWith { key ->
                date(key)
                val a = j.getJSONArray(key)
                demand(a.length() <= 500, R.string.err_backup_too_many_records)
                (0 until a.length()).map { a.getString(it).also { id -> demand(id in ids, R.string.err_backup_unknown_habit) } }.toSet()
            }
        }
        val journal = json.getJSONArray("journal").objects(50000).map {
            Reflection(date(it.getString("date")), it.getInt("mood"), it.getString("text")).also { e ->
                demand(e.mood in 0..4 && e.text.isNotBlank() && e.text.length <= 10000, R.string.err_backup_invalid_reflection)
            }
        }
        demand(journal.distinctBy { it.date }.size == journal.size, R.string.err_backup_duplicate_dates)
        val name = json.optString("name", "Alex")
        demand(name.isNotBlank() && name.length <= 30, R.string.err_backup_name)
        val sync = json.optJSONObject("sync")?.let { value ->
            SyncMetadata(
                habitUpdates = value.optJSONObject("habitUpdates").timestampMap(),
                habitDeletions = value.optJSONObject("habitDeletions").timestampMap(),
                checkUpdates = value.optJSONObject("checkUpdates").timestampMap(),
                checkDeletions = value.optJSONObject("checkDeletions").timestampMap(),
                reflectionUpdates = value.optJSONObject("reflectionUpdates").timestampMap(),
                reflectionDeletions = value.optJSONObject("reflectionDeletions").timestampMap()
            )
        } ?: SyncMetadata()
        validateSync(sync, habits, checks, journal)
        return HabitState(habits, checks, journal, name, json.optBoolean("demo", false), json.optBoolean("onboarded", true), sync)
    }

    fun validateHabit(h: Habit) {
        demand(h.id.matches(Regex("[A-Za-z0-9_-]{1,100}")), R.string.err_habit_id)
        demand(h.name.isNotBlank() && h.name.length <= 70 && h.goal.isNotBlank() && h.goal.length <= 80, R.string.err_habit_name)
        demand(h.icon in 0..7 && h.color in 0..5 && h.time in Dayparts, R.string.err_habit_style)
        demand(h.reminderMinutes == null || h.reminderMinutes in 0..1439, R.string.err_habit_reminder)
        demand(h.reminderCount in 1..MAX_DAILY_REMINDERS, R.string.err_habit_reminder_count)
        demand(h.reminderMinutes == null || h.reminderMinutes <= reminderStartLimit(h.reminderCount), R.string.err_habit_reminder_spacing)
        date(h.created.toString())
        demand(h.schedule.size <= 5000 && h.schedule.zipWithNext().all { (a, b) -> a.from < b.from }, R.string.err_habit_schedule)
        demand(h.schedule.all { it.from >= h.created && it.from.year in 1970..2200 }, R.string.err_habit_schedule)
        demand(h.schedule.isEmpty() || (h.schedule.first().from == h.created && h.schedule.last().weekdays == h.weekdays), R.string.err_habit_schedule)
    }
    private fun date(value: String): LocalDate = LocalDate.parse(value).also { demand(it.year in 1970..2200, R.string.err_invalid_date) }
    private fun Map<String, Long>.timestamps() = JSONObject().apply { forEach { (key, value) -> put(key, value) } }
    private fun JSONObject?.timestampMap(): Map<String, Long> {
        if (this == null) return emptyMap()
        demand(length() <= 100000, R.string.err_backup_too_many_records)
        return keys().asSequence().associateWith { key ->
            demand(key.length in 1..220, R.string.err_backup_sync_metadata)
            getLong(key).also { demand(it in 1 until Long.MAX_VALUE, R.string.err_backup_sync_metadata) }
        }
    }
    private fun validateSync(sync: SyncMetadata, habits: List<Habit>, checks: Map<String, Set<String>>, journal: List<Reflection>) {
        val ids = habits.map { it.id }.toSet()
        val checkKeys = checks.flatMap { (day, habitIds) -> habitIds.map { checkRevisionKey(day, it) } }.toSet()
        val reflectionKeys = journal.map { it.date.toString() }.toSet()
        demand(sync.habitUpdates.keys.all { it in ids } && sync.habitDeletions.keys.none { it in ids }, R.string.err_backup_sync_metadata)
        demand(sync.checkUpdates.keys.all { it in checkKeys } && sync.checkDeletions.keys.none { it in checkKeys }, R.string.err_backup_sync_metadata)
        demand(sync.reflectionUpdates.keys.all { it in reflectionKeys } && sync.reflectionDeletions.keys.none { it in reflectionKeys }, R.string.err_backup_sync_metadata)
        demand((sync.habitUpdates.keys + sync.habitDeletions.keys).all { it.matches(Regex("[A-Za-z0-9_-]{1,100}")) }, R.string.err_backup_sync_metadata)
        demand((sync.checkUpdates.keys + sync.checkDeletions.keys).all { key ->
            val separator = key.indexOf('/')
            separator > 0 && runCatching { date(key.substring(0, separator)) }.isSuccess &&
                key.substring(separator + 1).matches(Regex("[A-Za-z0-9_-]{1,100}"))
        }, R.string.err_backup_sync_metadata)
        demand((sync.reflectionUpdates.keys + sync.reflectionDeletions.keys).all { runCatching { date(it) }.isSuccess }, R.string.err_backup_sync_metadata)
    }
    private fun JSONArray.objects(limit: Int): List<JSONObject> {
        demand(length() <= limit, R.string.err_backup_too_many_records)
        return (0 until length()).map { getJSONObject(it) }
    }
}
