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
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup is larger than 8 MB." }
        val json = JSONObject(raw.removePrefix("\uFEFF"))
        require(json.optInt("version", 1) in 1..2) { "This backup needs a newer version of Kimi." }
        require(!json.has("format") || json.getString("format") == "kimi") { "Choose a Kimi backup." }
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
        require(ids.size == habits.size) { "Backup has duplicate habits." }
        val checks = json.getJSONObject("checks").let { j ->
            require(j.length() <= 50000) { "Too many check-in dates." }
            j.keys().asSequence().associateWith { key ->
                date(key)
                val a = j.getJSONArray(key)
                require(a.length() <= 500)
                (0 until a.length()).map { a.getString(it).also { id -> require(id in ids) { "Check-in has an unknown habit." } } }.toSet()
            }
        }
        val journal = json.getJSONArray("journal").objects(50000).map {
            Reflection(date(it.getString("date")), it.getInt("mood"), it.getString("text")).also { e ->
                require(e.mood in 0..4 && e.text.isNotBlank() && e.text.length <= 10000) { "Invalid reflection." }
            }
        }
        require(journal.distinctBy { it.date }.size == journal.size) { "Backup has duplicate journal dates." }
        val name = json.optString("name", "Alex")
        require(name.isNotBlank() && name.length <= 30) { "Name must be 1–30 characters." }
        return HabitState(habits, checks, journal, name, json.optBoolean("demo", false), json.optBoolean("onboarded", true))
    }

    fun validateHabit(h: Habit) {
        require(h.id.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Invalid habit identifier." }
        require(h.name.isNotBlank() && h.name.length <= 70 && h.goal.isNotBlank() && h.goal.length <= 80) { "Give your habit a name and a short goal." }
        require(h.icon in 0..7 && h.color in 0..5 && h.time in listOf("Morning", "Afternoon", "Evening", "Anytime")) { "Invalid habit style." }
        require(h.reminderMinutes == null || h.reminderMinutes in 0..1439) { "Invalid reminder time." }
        date(h.created.toString())
        require(h.schedule.size <= 5000 && h.schedule.zipWithNext().all { (a, b) -> a.from < b.from }) { "Invalid schedule history." }
        require(h.schedule.all { it.from >= h.created && it.from.year in 1970..2200 })
        require(h.schedule.isEmpty() || (h.schedule.first().from == h.created && h.schedule.last().weekdays == h.weekdays))
    }
    private fun date(value: String): LocalDate = LocalDate.parse(value).also { require(it.year in 1970..2200) { "Invalid date." } }
    private fun JSONArray.objects(limit: Int): List<JSONObject> {
        require(length() <= limit) { "Backup contains too many records." }
        return (0 until length()).map { getJSONObject(it) }
    }
}
