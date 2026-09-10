package com.forma.habits

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Versioned, portable backup. Decode completely before replacing any live data. */
object BackupCodec {
    const val MAX_BYTES = 8 * 1024 * 1024
    fun encode(state: HabitState): String = JSONObject().apply {
        put("format", "kimi"); put("version", 2)
        put("name", state.name); put("demo", state.demo); put("onboarded", state.onboarded)
        put("habits", JSONArray().apply { state.habits.forEach { h -> put(JSONObject().apply {
            put("id", h.id); put("name", h.name); put("goal", h.goal); put("icon", h.icon); put("color", h.color)
            put("time", h.time); put("weekdays", h.weekdays); put("created", h.created.toString())
            h.reminderMinutes?.let { put("reminderMinutes", it) }
            put("schedule", JSONArray().apply { h.schedule.forEach { change -> put(JSONObject().apply {
                put("from", change.from.toString()); put("weekdays", change.weekdays)
            }) } })
        }) } })
        put("checks", JSONObject().apply { state.checks.forEach { (date, ids) -> put(date, JSONArray(ids.toList().sorted())) } })
        put("journal", JSONArray().apply { state.journal.forEach { entry -> put(JSONObject().apply {
            put("date", entry.date.toString()); put("mood", entry.mood); put("text", entry.text)
        }) } })
    }.toString(2)

    fun decode(raw: String): HabitState {
        demand(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES, R.string.err_backup_too_large)
        val json = JSONObject(raw.removePrefix("\uFEFF"))
        demand(json.optInt("version", 1) in 1..2, R.string.err_backup_version)
        demand(!json.has("format") || json.getString("format") == "kimi", R.string.err_backup_format)
        val habits = json.getJSONArray("habits").objects(500).map { h ->
            Habit(id = h.getString("id"), name = h.getString("name"), goal = h.getString("goal"),
                icon = h.getInt("icon"), color = h.getInt("color"), time = h.getString("time"),
                weekdays = h.getBoolean("weekdays"), created = date(h.getString("created")),
                reminderMinutes = if (h.has("reminderMinutes")) h.getInt("reminderMinutes") else null,
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
        return HabitState(habits, checks, journal, name, json.optBoolean("demo", false), json.optBoolean("onboarded", true))
    }

    fun validateHabit(h: Habit) {
        demand(h.id.matches(Regex("[A-Za-z0-9_-]{1,100}")), R.string.err_habit_id)
        demand(h.name.isNotBlank() && h.name.length <= 70 && h.goal.isNotBlank() && h.goal.length <= 80, R.string.err_habit_name)
        demand(h.icon in 0..7 && h.color in 0..5 && h.time in Dayparts, R.string.err_habit_style)
        demand(h.reminderMinutes == null || h.reminderMinutes in 0..1439, R.string.err_habit_reminder)
        date(h.created.toString())
        demand(h.schedule.size <= 5000 && h.schedule.zipWithNext().all { (a, b) -> a.from < b.from }, R.string.err_habit_schedule)
        demand(h.schedule.all { it.from >= h.created && it.from.year in 1970..2200 }, R.string.err_habit_schedule)
        demand(h.schedule.isEmpty() || (h.schedule.first().from == h.created && h.schedule.last().weekdays == h.weekdays), R.string.err_habit_schedule)
    }
    private fun date(value: String): LocalDate = LocalDate.parse(value).also { demand(it.year in 1970..2200, R.string.err_invalid_date) }
    private fun JSONArray.objects(limit: Int): List<JSONObject> {
        demand(length() <= limit, R.string.err_backup_too_many_records)
        return (0 until length()).map { getJSONObject(it) }
    }
}
