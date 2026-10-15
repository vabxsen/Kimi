package com.forma.habits

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Kimi's garden game, worked out entirely from the habit history: nothing here is stored or synced,
 * so every device agrees, past ticks count from day one, and there is nothing to cheat or to lose.
 *
 * Petals are earned, never taken away for a missed day: a petal per tick, a bonus for every day
 * with everything done, for each perfect week, for streaks reaching 7, 30 and 100, and for each
 * weekly quest finished. Petals fill garden levels, which unlock purely cosmetic rewards. Every
 * perfect week also earns a streak freeze (up to [MaxFreezes] held), which is spent by itself on
 * a missed day that would otherwise have broken a streak; that day then neither counts nor breaks.
 */

const val MaxFreezes = 3
const val PetalsPerTick = 1
const val PerfectDayPetals = 3
const val PerfectWeekPetals = 10
const val QuestPetals = 20
val StreakPetals = mapOf(7 to 15, 30 to 50, 100 to 150)

enum class BadgeKind {
    FirstTick, FirstPerfectDay, PerfectWeek, Streak7, Streak30, Streak100, Ticks100, Ticks500,
    WeekendWarrior, Comeback, MorningPerson, JournalKeeper, Collector, QuestMaster
}

/** A cosmetic reward and the garden level that unlocks it. */
enum class Unlock(val level: Int) {
    PeachPetals(2), ShootingStars(3), PinkPetals(4), Rainbow(5), SkyPetals(6), MintPetals(7), LilacPetals(8), RainbowPetals(10)
}

enum class QuestKind { Ticks, PerfectDays, Journal, Daypart, EveryHabit, Streak }

/** One of the week's quests: what it asks, how far along it is, and (for [QuestKind.Daypart]) which part of the day. */
@Immutable data class Quest(val kind: QuestKind, val target: Int, val progress: Int, val daypart: String? = null) {
    val done: Boolean get() = progress >= target
}

@Immutable data class GameReport(
    val petals: Int,
    val level: Int,
    val levelStart: Int,
    val nextLevelAt: Int,
    val badges: Map<BadgeKind, LocalDate>,
    val freezes: Int,
    val frozenDays: Set<LocalDate>,
    val quests: List<Quest>
) {
    /** How far through the current level, 0 to 1. */
    val levelProgress: Float get() = ((petals - levelStart).toFloat() / (nextLevelAt - levelStart)).coerceIn(0f, 1f)
    fun unlocked(unlock: Unlock) = level >= unlock.level
}

/** The garden game's report for the space on screen. Worked out even while the game is hidden, for its freezes. */
val LocalGame = compositionLocalOf<GameReport?> { null }

/**
 * Petals needed to reach [level]: 0 for level 1, then each level takes 30 more than the last (40, 70,
 * 100, ...). Paced so a steady week reaches level 2, rainbows come in about three weeks, and the
 * later levels take months: the rewards stay something to look forward to.
 */
fun petalsForLevel(level: Int): Int = if (level <= 1) 0 else (2..level).sumOf { 40 + 30 * (it - 2) }

fun levelFor(petals: Int): Int {
    var level = 1
    while (petalsForLevel(level + 1) <= petals) level++
    return level
}

private fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

fun HabitState.game(today: LocalDate): GameReport {
    if (habits.isEmpty()) return GameReport(0, 1, 0, petalsForLevel(2), emptyMap(), 0, emptySet(), emptyList())
    val start = habits.minOf { it.created }.coerceAtMost(today)
    var petals = 0
    var freezes = 0
    val frozen = mutableSetOf<LocalDate>()
    val badges = mutableMapOf<BadgeKind, LocalDate>()
    fun earn(kind: BadgeKind, on: LocalDate) { badges.putIfAbsent(kind, on) }
    // Streak bookkeeping per habit: the current run, and whether its last due day was done.
    val run = mutableMapOf<String, Int>()
    val alive = mutableMapOf<String, Boolean>()
    var ticks = 0
    var morningTicks = 0
    var weekends = 0
    var lastTick: LocalDate? = null
    var quests = emptyList<Quest>()
    var date = start
    // This week's running totals, for perfect weeks and quests.
    var week = weekStart(start)
    var weekTicks = 0
    var weekPerfect = 0
    var weekDue = 0
    var weekAllPerfect = true
    var weekMaxRun = 0
    val weekDaypart = mutableMapOf<String, Int>()
    val weekHabits = mutableSetOf<String>()
    var perfectSaturday = false

    fun closeWeek(through: LocalDate, ended: Boolean) {
        val days = (0L..6L).map { week.plusDays(it) }
        val questList = weekQuests(week, days)
        val journal = journal.count { it.date in week..week.plusDays(6) && !it.date.isAfter(through) }
        val due = days.flatMap { d -> due(d).map { it.id } }.toSet()
        quests = questList.map { q ->
            val progress = when (q.kind) {
                QuestKind.Ticks -> weekTicks
                QuestKind.PerfectDays -> weekPerfect
                QuestKind.Journal -> journal
                QuestKind.Daypart -> weekDaypart[q.daypart] ?: 0
                QuestKind.EveryHabit -> weekHabits.count { it in due }
                QuestKind.Streak -> weekMaxRun
            }
            q.copy(progress = progress.coerceAtMost(q.target))
        }
        val finished = quests.count { it.done }
        petals += finished * QuestPetals
        if (finished == quests.size && quests.isNotEmpty()) earn(BadgeKind.QuestMaster, through)
        if (ended && weekDue > 0 && weekAllPerfect) {
            petals += PerfectWeekPetals
            freezes = (freezes + 1).coerceAtMost(MaxFreezes)
            earn(BadgeKind.PerfectWeek, through)
        }
    }

    while (!date.isAfter(today)) {
        if (weekStart(date) != week) {
            week = weekStart(date)
            weekTicks = 0; weekPerfect = 0; weekDue = 0; weekAllPerfect = true; weekMaxRun = 0
            weekDaypart.clear(); weekHabits.clear()
        }
        val due = due(date)
        val doneHabits = habits.filter { done(it.id, date) }
        val past = date.isBefore(today)
        // A missed day that would break a live streak is covered by a freeze, if one is held.
        val breaks = past && due.any { !done(it.id, date) && alive[it.id] == true }
        if (breaks && freezes > 0) {
            freezes--
            frozen += date
        } else {
            for (habit in due) {
                if (done(habit.id, date)) {
                    val now = (run[habit.id] ?: 0) + 1
                    run[habit.id] = now; alive[habit.id] = true
                    weekMaxRun = maxOf(weekMaxRun, now)
                    StreakPetals[now]?.let { petals += it }
                    when (now) { 7 -> earn(BadgeKind.Streak7, date); 30 -> earn(BadgeKind.Streak30, date); 100 -> earn(BadgeKind.Streak100, date) }
                } else if (past) { run[habit.id] = 0; alive[habit.id] = false }
            }
        }
        if (doneHabits.isNotEmpty()) {
            lastTick?.let { if (ChronoUnit.DAYS.between(it, date) >= 8) earn(BadgeKind.Comeback, date) }
            lastTick = date
            earn(BadgeKind.FirstTick, date)
        }
        val before = ticks
        ticks += doneHabits.size
        petals += doneHabits.size * PetalsPerTick
        if (before < 100 && ticks >= 100) earn(BadgeKind.Ticks100, date)
        if (before < 500 && ticks >= 500) earn(BadgeKind.Ticks500, date)
        val morningBefore = morningTicks
        morningTicks += doneHabits.count { it.time == "Morning" }
        if (morningBefore < 25 && morningTicks >= 25) earn(BadgeKind.MorningPerson, date)
        weekTicks += doneHabits.size
        doneHabits.forEach { weekDaypart[it.time] = (weekDaypart[it.time] ?: 0) + 1; weekHabits += it.id }
        val perfect = due.isNotEmpty() && due.all { done(it.id, date) }
        if (due.isNotEmpty() && date !in frozen) {
            weekDue++
            // Today is still open: not perfect yet is not a failed week yet.
            if (!perfect && past) weekAllPerfect = false
        }
        if (perfect) {
            petals += PerfectDayPetals
            weekPerfect++
            earn(BadgeKind.FirstPerfectDay, date)
        }
        if (date.dayOfWeek == DayOfWeek.SATURDAY) perfectSaturday = perfect
        if (date.dayOfWeek == DayOfWeek.SUNDAY && perfect && perfectSaturday) {
            weekends++
            if (weekends == 4) earn(BadgeKind.WeekendWarrior, date)
        }
        val weekOver = date.dayOfWeek == DayOfWeek.SUNDAY
        if (weekOver && (past || perfect)) closeWeek(date, ended = true)
        else if (date == today) closeWeek(date, ended = false)
        date = date.plusDays(1)
    }
    journal.map { it.date }.sorted().getOrNull(9)?.let { earn(BadgeKind.JournalKeeper, it) }
    habits.map { it.created }.sorted().getOrNull(4)?.let { earn(BadgeKind.Collector, it) }
    val level = levelFor(petals)
    return GameReport(petals, level, petalsForLevel(level), petalsForLevel(level + 1), badges, freezes, frozen, quests)
}

/**
 * The three quests for the week starting [week]. They come from the week itself (a seed from its
 * date, and targets from what is scheduled in it), so they are the same on every device and every
 * time they are worked out, and always within reach of the habits being kept that week.
 */
internal fun HabitState.weekQuests(week: LocalDate, days: List<LocalDate> = (0L..6L).map { week.plusDays(it) }): List<Quest> {
    val slots = days.sumOf { due(it).size }
    if (slots == 0) return emptyList()
    val activeDays = days.count { due(it).isNotEmpty() }
    val byPart = Dayparts.associateWith { part -> days.sumOf { d -> due(d).count { it.time == part } } }
    val topPart = Dayparts.maxByOrNull { byPart.getValue(it) }!!
    val weekHabits = days.flatMap { d -> due(d).map { it.id } }.toSet().size
    val candidates = buildList {
        add(Quest(QuestKind.Ticks, (slots * .6f).roundToInt().coerceIn(3, 40), 0))
        if (activeDays >= 3) add(Quest(QuestKind.PerfectDays, 2, 0))
        add(Quest(QuestKind.Journal, 2, 0))
        if (byPart.getValue(topPart) >= 3) add(Quest(QuestKind.Daypart, (byPart.getValue(topPart) * .6f).roundToInt().coerceIn(2, 10), 0, topPart))
        if (weekHabits >= 2) add(Quest(QuestKind.EveryHabit, weekHabits, 0))
        if (activeDays >= 5) add(Quest(QuestKind.Streak, 5, 0))
    }
    return candidates.shuffled(Random(week.toEpochDay())).take(3).sortedBy { it.kind.ordinal }
}
