package com.forma.habits

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Brightness2
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Fence
import androidx.compose.material.icons.rounded.FlutterDash
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.House
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Looks
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The garden game's screens: the bar on Today, the garden itself, and the moments when a level or
 * a badge is reached. The rules live in Game.kt and are worked out from history; the only things
 * kept here are preferences: whether the game is on, which petals the flower wears, and which
 * levels and badges have already been celebrated on this device.
 */

/** Whether the game is on and which petal colour was chosen. Device-wide, like the theme. */
object GameSetting {
    private const val PREFS = "kimi_game"
    var enabled by mutableStateOf(true)
        private set
    var petals by mutableStateOf<Unlock?>(null)
        private set
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean("enabled", true)
        petals = prefs.getString("petals", null)?.let { name -> Unlock.entries.firstOrNull { it.name == name } }
    }
    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean("enabled", value) }
    }
    fun setPetals(context: Context, value: Unlock?) {
        petals = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { if (value == null) remove("petals") else putString("petals", value.name) }
    }
}

/**
 * Which level and badges have been celebrated for one space on this device, so each moment plays
 * once. A space seen for the first time is remembered quietly, with no burst of old celebrations.
 */
class GameMemory(context: Context, owner: String) {
    private val prefs = context.getSharedPreferences(AccountSession.preferenceName("kimi_game_seen", owner), Context.MODE_PRIVATE)
    var level: Int
        get() = prefs.getInt("level", -1)
        set(value) = prefs.edit { putInt("level", value) }
    var badges: Set<String>
        get() = prefs.getStringSet("badges", emptySet()).orEmpty()
        set(value) = prefs.edit { putStringSet("badges", value) }
    /** Null until keepsakes are first remembered, so an update does not celebrate every past season at once. */
    var keepsakes: Set<String>?
        get() = if (prefs.contains("keepsakes")) prefs.getStringSet("keepsakes", emptySet()).orEmpty() else null
        set(value) = prefs.edit { putStringSet("keepsakes", value.orEmpty()) }
}

private fun Keepsake.key() = of.season.name + "-" + of.year + "-" + tier

/** The colour each petal unlock gives the flower. */
internal val PetalColours = linkedMapOf(
    Unlock.PeachPetals to Color(0xFFFFC3A0), Unlock.PinkPetals to Color(0xFFF9B8CF), Unlock.SkyPetals to Color(0xFFA9D8F5),
    Unlock.MintPetals to Color(0xFFB9E6CC), Unlock.LilacPetals to Color(0xFFD5C4F5)
)
internal val RainbowPetals = listOf(Color(0xFFFF9AA2), Color(0xFFFFC38A), Color(0xFFFFE696), Color(0xFFB5E8B0), Color(0xFFA9D8F5), Color(0xFFD5C4F5))
private val PetalUnlocks = PetalColours.keys + Unlock.RainbowPetals

/** The petals Today's flower wears: the chosen colour, if the game is on and it has been unlocked. */
@Composable fun chosenPetals(report: GameReport?): Pair<Color, List<Color>?> {
    val choice = GameSetting.petals
    if (report == null || !GameSetting.enabled || choice == null || !report.unlocked(choice) || LocalKimiPalette.current.mono) return Highlight to null
    return if (choice == Unlock.RainbowPetals) Highlight to RainbowPetals else (PetalColours[choice] ?: Highlight) to null
}

private class BadgeLook(val icon: ImageVector, val title: Int, val hint: Int)

private val BadgeLooks = mapOf(
    BadgeKind.FirstTick to BadgeLook(Icons.Rounded.Spa, R.string.badge_first_tick, R.string.badge_first_tick_hint),
    BadgeKind.FirstPerfectDay to BadgeLook(Icons.Rounded.Stars, R.string.badge_first_perfect, R.string.badge_first_perfect_hint),
    BadgeKind.PerfectWeek to BadgeLook(Icons.Rounded.EmojiEvents, R.string.badge_perfect_week, R.string.badge_perfect_week_hint),
    BadgeKind.Streak7 to BadgeLook(Icons.Rounded.LocalFireDepartment, R.string.badge_streak7, R.string.badge_streak7_hint),
    BadgeKind.Streak30 to BadgeLook(Icons.Rounded.Whatshot, R.string.badge_streak30, R.string.badge_streak30_hint),
    BadgeKind.Streak100 to BadgeLook(Icons.Rounded.MilitaryTech, R.string.badge_streak100, R.string.badge_streak100_hint),
    BadgeKind.Ticks100 to BadgeLook(Icons.Rounded.Bolt, R.string.badge_ticks100, R.string.badge_ticks100_hint),
    BadgeKind.Ticks500 to BadgeLook(Icons.Rounded.ElectricBolt, R.string.badge_ticks500, R.string.badge_ticks500_hint),
    BadgeKind.WeekendWarrior to BadgeLook(Icons.Rounded.Weekend, R.string.badge_weekend, R.string.badge_weekend_hint),
    BadgeKind.Comeback to BadgeLook(Icons.Rounded.Replay, R.string.badge_comeback, R.string.badge_comeback_hint),
    BadgeKind.MorningPerson to BadgeLook(Icons.Rounded.WbTwilight, R.string.badge_morning, R.string.badge_morning_hint),
    BadgeKind.JournalKeeper to BadgeLook(Icons.Rounded.AutoStories, R.string.badge_journal, R.string.badge_journal_hint),
    BadgeKind.Collector to BadgeLook(Icons.Rounded.LocalFlorist, R.string.badge_collector, R.string.badge_collector_hint),
    BadgeKind.QuestMaster to BadgeLook(Icons.Rounded.Flag, R.string.badge_quests, R.string.badge_quests_hint)
)

private fun unlockName(unlock: Unlock) = when (unlock) {
    Unlock.PeachPetals -> R.string.unlock_peach
    Unlock.ShootingStars -> R.string.unlock_stars
    Unlock.PinkPetals -> R.string.unlock_pink
    Unlock.Rainbow -> R.string.unlock_rainbow
    Unlock.SkyPetals -> R.string.unlock_sky
    Unlock.MintPetals -> R.string.unlock_mint
    Unlock.LilacPetals -> R.string.unlock_lilac
    Unlock.RainbowPetals -> R.string.unlock_rainbow_petals
    Unlock.Wildflowers -> R.string.unlock_wildflowers
    Unlock.Butterflies -> R.string.unlock_butterflies
    Unlock.Fence -> R.string.unlock_fence
    Unlock.Pond -> R.string.unlock_pond
    Unlock.Tree -> R.string.unlock_tree
    Unlock.StonePath -> R.string.unlock_path
    Unlock.Birdhouse -> R.string.unlock_birdhouse
    Unlock.Lanterns -> R.string.unlock_lanterns
    Unlock.Meadow -> R.string.unlock_meadow
}

private fun unlockBody(unlock: Unlock) = when (unlock) {
    Unlock.ShootingStars -> R.string.unlock_stars_body
    Unlock.Rainbow -> R.string.unlock_rainbow_body
    Unlock.Wildflowers -> R.string.unlock_wildflowers_body
    Unlock.Butterflies -> R.string.unlock_butterflies_body
    Unlock.Fence -> R.string.unlock_fence_body
    Unlock.Pond -> R.string.unlock_pond_body
    Unlock.Tree -> R.string.unlock_tree_body
    Unlock.StonePath -> R.string.unlock_path_body
    Unlock.Birdhouse -> R.string.unlock_birdhouse_body
    Unlock.Lanterns -> R.string.unlock_lanterns_body
    Unlock.Meadow -> R.string.unlock_meadow_body
    else -> R.string.unlock_petals_body
}

private fun unlockIcon(unlock: Unlock): ImageVector = when (unlock) {
    Unlock.ShootingStars -> Icons.Rounded.AutoAwesome
    Unlock.Rainbow -> Icons.Rounded.Looks
    Unlock.Wildflowers, Unlock.Meadow -> Icons.Rounded.Grass
    Unlock.Butterflies -> Icons.Rounded.FlutterDash
    Unlock.Fence -> Icons.Rounded.Fence
    Unlock.Pond -> Icons.Rounded.Water
    Unlock.Tree -> Icons.Rounded.Park
    Unlock.StonePath -> Icons.Rounded.Hiking
    Unlock.Birdhouse -> Icons.Rounded.House
    Unlock.Lanterns -> Icons.Rounded.Lightbulb
    else -> Icons.Rounded.LocalFlorist
}

/** What a season is called, and how its keepsakes look and are named. */
@Composable private fun seasonName(season: Season): String = stringResource(when (season) {
    Season.Spring -> R.string.season_spring; Season.Summer -> R.string.season_summer
    Season.Autumn -> R.string.season_autumn; Season.Winter -> R.string.season_winter
})

private val KeepsakeIcons = mapOf(
    Season.Spring to listOf(Icons.Rounded.LocalFlorist, Icons.Rounded.Spa, Icons.Rounded.Looks),
    Season.Summer to listOf(Icons.Rounded.WbSunny, Icons.Rounded.BeachAccess, Icons.Rounded.Nightlight),
    Season.Autumn to listOf(Icons.Rounded.Eco, Icons.Rounded.Park, Icons.Rounded.Brightness2),
    Season.Winter to listOf(Icons.Rounded.AcUnit, Icons.Rounded.LocalCafe, Icons.Rounded.Star)
)

private fun keepsakeName(season: Season, tier: Int) = when (season) {
    Season.Spring -> listOf(R.string.keepsake_spring_0, R.string.keepsake_spring_1, R.string.keepsake_spring_2)
    Season.Summer -> listOf(R.string.keepsake_summer_0, R.string.keepsake_summer_1, R.string.keepsake_summer_2)
    Season.Autumn -> listOf(R.string.keepsake_autumn_0, R.string.keepsake_autumn_1, R.string.keepsake_autumn_2)
    Season.Winter -> listOf(R.string.keepsake_winter_0, R.string.keepsake_winter_1, R.string.keepsake_winter_2)
}[tier]

private fun collectionCount(season: Season) = when (season) {
    Season.Spring -> R.plurals.collection_spring; Season.Summer -> R.plurals.collection_summer
    Season.Autumn -> R.plurals.collection_autumn; Season.Winter -> R.plurals.collection_winter
}

@Composable fun levelName(level: Int): String {
    val names = stringArrayResource(R.array.garden_level_names)
    return names[(level - 1).coerceIn(0, names.size - 1)]
}

@Composable private fun questText(quest: Quest): String = when (quest.kind) {
    QuestKind.Ticks -> pluralStringResource(R.plurals.quest_ticks, quest.target, quest.target)
    QuestKind.PerfectDays -> pluralStringResource(R.plurals.quest_perfect, quest.target, quest.target)
    QuestKind.Journal -> pluralStringResource(R.plurals.quest_journal, quest.target, quest.target)
    QuestKind.Daypart -> pluralStringResource(when (quest.daypart) {
        "Morning" -> R.plurals.quest_morning; "Afternoon" -> R.plurals.quest_afternoon
        "Evening" -> R.plurals.quest_evening; else -> R.plurals.quest_anytime
    }, quest.target, quest.target)
    QuestKind.EveryHabit -> pluralStringResource(R.plurals.quest_every, quest.target, quest.target)
    QuestKind.Streak -> pluralStringResource(R.plurals.quest_streak, quest.target, quest.target)
}

/** A round badge with the level number in it. */
/** The rosette's petals change colour every two levels, so a new tier looks new. */
// No yellow among them: the rosette sits on the yellow garden bar.
private val RosetteTiers = listOf(Color(0xFFD5C4F5), Color(0xFFFFC3A0), Color(0xFFB9E6CC), Color(0xFFA9D8F5), Color(0xFFF9B8CF), Color(0xFFFFAB91))
private val RosetteGold = Color(0xFFFFC94D)

/**
 * The level, worn like a prize rosette: a ring of petals in the tier's colour turning slowly, a gold
 * ring filling with the level's progress, and a glossy disc with the number in it. Decoration only:
 * whatever shows it also says the level in words.
 */
@Composable private fun LevelBadge(level: Int, progress: Float, size: Int = 54) {
    val reduced = reducedMotion()
    val turn = if (!reduced) rememberInfiniteTransition(label = "rosette")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "rosette turn") else null
    val fill by rememberGrow(progress.coerceIn(0f, 1f), 900, 150)
    val mono = LocalKimiPalette.current.mono
    val petal = if (mono) Lavender else RosetteTiers[((level - 1) / 2) % RosetteTiers.size]
    val gold = if (mono) Ink else RosetteGold
    val disc = Purple
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val u = this.size.minDimension
            val centre = Offset(this.size.width / 2, this.size.height / 2)
            // Ten petals round the edge, the outer scallop of the rosette, with a faint shadow under them.
            rotate(turn?.value ?: 0f, centre) {
                for (i in 0 until 10) {
                    val a = i * 36f * PI.toFloat() / 180f
                    val at = centre + Offset(cos(a), sin(a)) * (u * .33f)
                    drawCircle(Color.Black.copy(alpha = .08f), u * .145f, at + Offset(0f, u * .02f))
                    drawCircle(petal, u * .14f, at)
                    // A glint on the outer edge of each petal, where the light catches it.
                    drawCircle(Color.White.copy(alpha = .45f), u * .035f, at + Offset(cos(a), sin(a)) * (u * .06f))
                }
            }
            // The progress ring: a soft track, then gold for how far through the level.
            val ring = u * .3f
            val stroke = u * .055f
            drawCircle(Color.White.copy(alpha = .75f), ring, centre, style = Stroke(stroke))
            if (fill > 0f) drawArc(gold, -90f, 360f * fill, false, centre - Offset(ring, ring), Size(ring * 2, ring * 2),
                style = Stroke(stroke, cap = StrokeCap.Round))
            // The glossy centre.
            val r = u * .25f
            drawCircle(Brush.radialGradient(listOf(lerp(disc, Color.White, .3f), disc, lerp(disc, Color.Black, .18f)),
                centre - Offset(r * .35f, r * .4f), r * 1.6f), r, centre)
            drawOval(Color.White.copy(alpha = .28f), centre - Offset(r * .55f, r * .78f), Size(r * 1.1f, r * .55f))
        }
        Text("$level", color = OnAccent, fontWeight = FontWeight.ExtraBold, fontSize = (size * (if (level >= 10) .24f else .3f)).sp,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .25f), Offset(0f, 2f), 3f)))
    }
}

/** A petal-fill bar: the level's progress, filling in when it first appears. */
@Composable private fun LevelBar(progress: Float, modifier: Modifier = Modifier) {
    val fill by rememberGrow(progress, 900, 150)
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Overlay)) {
        Box(Modifier.fillMaxWidth(fill.coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(Ink))
    }
}

/** Today's window onto the garden: level, progress, this week's quests and freezes held. */
@Composable fun GardenBar(report: GameReport, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val name = levelName(report.level)
    val questsDone = report.quests.count { it.done }
    val label = stringResource(R.string.cd_garden_bar, report.level, name,
        pluralStringResource(R.plurals.cd_garden_quests, report.quests.size, questsDone, report.quests.size),
        pluralStringResource(R.plurals.cd_garden_freezes, report.freezes, report.freezes))
    PlayCard(Yellow, modifier.semantics(mergeDescendants = true) { contentDescription = label }.testTag("garden_bar"), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelBadge(report.level, report.levelProgress)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.garden_level, report.level, name), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(7.dp))
                LevelBar(report.levelProgress)
                Spacer(Modifier.height(5.dp))
                val toGo = report.nextLevelAt - report.petals
                Text(pluralStringResource(R.plurals.garden_petals_to_go, toGo, toGo, report.level + 1), fontSize = 11.sp, lineHeight = 14.sp, color = Quiet)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (report.quests.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Flag, null, Modifier.size(16.dp), tint = Ink); Spacer(Modifier.width(3.dp))
                    Text(stringResource(R.string.garden_bar_quests, questsDone, report.quests.size), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AcUnit, null, Modifier.size(16.dp), tint = Ink); Spacer(Modifier.width(3.dp))
                    Text("${report.freezes}", fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Ink)
        }
    }
}

/** The garden: everything about the game, opened from Today's bar. */
@Composable fun GardenScreen(state: HabitState, report: GameReport, onClose: () -> Unit) {
    val today = LocalToday.current
    val plants = remember(state, today, report.frozenDays) {
        state.habits.map { ScenePlant(plantStage(state.monthOfDone(it, today)), state.wilting(it, today, report.frozenDays)) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = Cream, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp, 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_close_garden)) }
                    Text(stringResource(R.string.garden_title), style = MaterialTheme.typography.titleMedium)
                }
                val cascade = rememberCascade()
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(23.dp, 8.dp, 23.dp, 28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    GardenScene(plants, report, Modifier.fillMaxWidth().height(250.dp).cascaded(cascade.entrance(0)).clip(RoundedCornerShape(27.dp)))
                    GardenHero(report, Modifier.cascaded(cascade.entrance(0)))
                    QuestsCard(report, Modifier.cascaded(cascade.entrance(1)))
                    CollectionCard(report, Modifier.cascaded(cascade.entrance(2)))
                    FreezesCard(report, Modifier.cascaded(cascade.entrance(2)))
                    BadgesCard(report, Modifier.cascaded(cascade.entrance(3)))
                    UnlocksCard(report, Modifier.cascaded(cascade.entrance(4)))
                    Column(Modifier.cascaded(cascade.entrance(5))) {
                        Text(stringResource(R.string.garden_how_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.garden_how_body), fontSize = 12.sp, color = Quiet, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun GardenHero(report: GameReport, modifier: Modifier) {
    val (petal, petals) = chosenPetals(report)
    PlayCard(Lavender, modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.garden_level, report.level, levelName(report.level)), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                val shown by rememberGrow(report.petals.toFloat(), 1100)
                Text(pluralStringResource(R.plurals.garden_petals_total, report.petals, shown.toInt()), color = Quiet, fontSize = 13.sp, lineHeight = 17.sp)
            }
            Flower(Modifier.size(92.dp), petal = petal, petals = petals, alive = true)
        }
        Spacer(Modifier.height(14.dp))
        LevelBar(report.levelProgress)
        Spacer(Modifier.height(6.dp))
        val toGo = report.nextLevelAt - report.petals
        Text(pluralStringResource(R.plurals.garden_petals_to_go, toGo, toGo, report.level + 1), fontSize = 12.sp, lineHeight = 16.sp, color = Quiet)
    }
}

@Composable private fun QuestsCard(report: GameReport, modifier: Modifier) {
    PlayCard(Paper, modifier) {
        Text(stringResource(R.string.garden_quests_title), style = MaterialTheme.typography.titleMedium)
        Text(pluralStringResource(R.plurals.garden_quests_body, QuestPetals, QuestPetals,
            Region.firstDay.getDisplayName(java.time.format.TextStyle.FULL, LocalConfiguration.current.locales[0])), fontSize = 12.sp, lineHeight = 16.sp, color = Quiet)
        Spacer(Modifier.height(12.dp))
        if (report.quests.isEmpty()) Text(stringResource(R.string.garden_quests_none), fontSize = 13.sp, lineHeight = 17.sp)
        report.quests.forEach { quest ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(if (quest.done) Mint else Overlay)
                    .then(if (quest.done) Modifier else Modifier.border(1.5.dp, Quiet.copy(alpha = .35f), CircleShape)), contentAlignment = Alignment.Center) {
                    Icon(if (quest.done) Icons.Rounded.Check else Icons.Rounded.Flag, if (quest.done) stringResource(R.string.cd_quest_done) else null,
                        Modifier.size(18.dp), tint = Ink)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(questText(quest), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    LevelBar(quest.progress.toFloat() / quest.target)
                    Spacer(Modifier.height(3.dp))
                    Text(stringResource(R.string.quest_progress, quest.progress, quest.target), fontSize = 11.sp, lineHeight = 14.sp, color = Quiet)
                }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.quest_reward, QuestPetals), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, lineHeight = 17.sp,
                    color = if (quest.done) Ink else Quiet)
            }
        }
    }
}

/** This season's collection: items so far, the way to the next keepsake, and keepsakes kept from earlier seasons. */
@Composable private fun CollectionCard(report: GameReport, modifier: Modifier) {
    val season = report.season.season
    val name = seasonName(season)
    val earned = report.keepsakes.filter { it.of == report.season }.map { it.tier }.toSet()
    PlayCard(Pink, modifier) {
        Text(stringResource(R.string.collection_title, name), style = MaterialTheme.typography.titleMedium)
        Text(pluralStringResource(R.plurals.collection_body, KeepsakePetals, KeepsakePetals), fontSize = 12.sp, color = Quiet, lineHeight = 17.sp)
        Spacer(Modifier.height(12.dp))
        val shown by rememberGrow(report.collected.toFloat(), 900)
        Text(pluralStringResource(collectionCount(season), report.collected, shown.toInt()), fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(8.dp))
        val next = KeepsakeAt.indexOfFirst { report.collected < it }
        if (next >= 0) {
            val from = if (next == 0) 0 else KeepsakeAt[next - 1]
            LevelBar((report.collected - from).toFloat() / (KeepsakeAt[next] - from))
            Spacer(Modifier.height(5.dp))
            val left = KeepsakeAt[next] - report.collected
            Text(pluralStringResource(R.plurals.collection_to_next, left, left, stringResource(keepsakeName(season, next))), fontSize = 12.sp, lineHeight = 16.sp, color = Quiet)
        } else Text(stringResource(R.string.collection_complete), fontSize = 12.sp, lineHeight = 16.sp, color = Quiet)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeepsakeAt.indices.forEach { tier ->
                val got = tier in earned
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(50.dp).clip(CircleShape).background(if (got) Yellow else Overlay)
                        .border(if (got) 2.dp else 1.5.dp, if (got) Ink else Quiet.copy(alpha = .35f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(if (got) KeepsakeIcons.getValue(season)[tier] else Icons.Rounded.Lock, null, Modifier.size(if (got) 24.dp else 18.dp),
                            tint = if (got) Ink else Quiet.copy(alpha = .6f))
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(stringResource(keepsakeName(season, tier)), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        color = if (got) Ink else Quiet)
                    Text("${KeepsakeAt[tier]}", fontSize = 10.sp, lineHeight = 13.sp, color = Quiet)
                }
            }
        }
        // Keepsakes from earlier seasons, newest first.
        val past = report.keepsakes.filter { it.of != report.season }.reversed()
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.collection_keepsakes_title), style = MaterialTheme.typography.titleMedium, fontSize = 15.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(6.dp))
        if (past.isEmpty()) Text(stringResource(R.string.collection_none_yet), fontSize = 12.sp, lineHeight = 16.sp, color = Quiet)
        past.forEach { keepsake ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Yellow), contentAlignment = Alignment.Center) {
                    Icon(KeepsakeIcons.getValue(keepsake.of.season)[keepsake.tier], null, Modifier.size(16.dp), tint = Ink)
                }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(keepsakeName(keepsake.of.season, keepsake.tier)), fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.collection_keepsake_season, seasonName(keepsake.of.season), keepsake.of.year), fontSize = 12.sp, lineHeight = 16.sp, color = Quiet)
            }
        }
    }
}

@Composable private fun FreezesCard(report: GameReport, modifier: Modifier) {
    PlayCard(Blue, modifier) {
        Text(stringResource(R.string.garden_freezes_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(MaxFreezes) { i ->
                val held = i < report.freezes
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (held) Paper else Overlay), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.AcUnit, null, Modifier.size(22.dp), tint = if (held) Ink else Quiet.copy(alpha = .5f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(if (report.freezes > 0) pluralStringResource(R.plurals.garden_freezes_count, report.freezes, report.freezes)
            else stringResource(R.string.garden_freezes_none), fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.garden_freezes_body), fontSize = 12.sp, color = Quiet, lineHeight = 17.sp)
    }
}

@Composable private fun BadgesCard(report: GameReport, modifier: Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(locale) { DateTimeFormatter.ofPattern("MMM d", locale) }
    PlayCard(Paper, modifier) {
        Text(stringResource(R.string.garden_badges_title), style = MaterialTheme.typography.titleMedium)
        Text(pluralStringResource(R.plurals.garden_badges_count, BadgeKind.entries.size, report.badges.size, BadgeKind.entries.size), fontSize = 12.sp, lineHeight = 16.sp, color = Quiet)
        Spacer(Modifier.height(12.dp))
        BadgeKind.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { kind ->
                    val look = BadgeLooks.getValue(kind)
                    val earned = report.badges[kind]
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(54.dp).clip(CircleShape).background(if (earned != null) Yellow else Overlay)
                            .border(if (earned != null) 2.dp else 1.5.dp, if (earned != null) Ink else Quiet.copy(alpha = .35f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(if (earned != null) look.icon else Icons.Rounded.Lock, null, Modifier.size(if (earned != null) 26.dp else 20.dp),
                                tint = if (earned != null) Ink else Quiet.copy(alpha = .6f))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(look.title), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            color = if (earned != null) Ink else Quiet)
                        Text(if (earned != null) stringResource(R.string.badge_earned_on, earned.format(date)) else stringResource(look.hint),
                            fontSize = 10.sp, color = Quiet, textAlign = TextAlign.Center, lineHeight = 13.sp)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable private fun UnlocksCard(report: GameReport, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    PlayCard(Paper, modifier) {
        Text(stringResource(R.string.garden_unlocks_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.garden_unlocks_body), fontSize = 12.sp, lineHeight = 16.sp, color = Quiet)
        Spacer(Modifier.height(10.dp))
        Unlock.entries.sortedBy { it.level }.forEach { unlock ->
            val open = report.unlocked(unlock)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                val tint = PetalColours[unlock]
                Box(Modifier.size(36.dp).clip(CircleShape).background(if (open) (tint ?: Lavender) else Overlay)
                    .then(if (open) Modifier else Modifier.border(1.5.dp, Quiet.copy(alpha = .35f), CircleShape)), contentAlignment = Alignment.Center) {
                    Icon(unlockIcon(unlock), null, Modifier.size(19.dp), tint = if (open) Color(0xFF33283E) else Quiet.copy(alpha = .6f))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(unlockName(unlock)), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = if (open) Ink else Quiet)
                    Text(stringResource(unlockBody(unlock)), fontSize = 11.sp, lineHeight = 14.sp, color = Quiet)
                }
                Text(stringResource(R.string.unlock_level, unlock.level), fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = Quiet)
                Spacer(Modifier.width(6.dp))
                Icon(if (open) Icons.Rounded.Check else Icons.Rounded.Lock,
                    stringResource(if (open) R.string.cd_unlock_open else R.string.cd_unlock_locked), Modifier.size(16.dp), tint = if (open) Ink else Quiet)
            }
        }
        // The flower's petals, from whichever colours are unlocked so far.
        val choices = listOf<Unlock?>(null) + PetalUnlocks.filter { report.unlocked(it) }
        if (choices.size > 1) {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.garden_petal_choice), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                choices.forEach { choice ->
                    val selected = GameSetting.petals == choice || (choice == null && GameSetting.petals?.let { !report.unlocked(it) } != false)
                    val name = stringResource(choice?.let { unlockName(it) } ?: R.string.petal_sunshine)
                    Box(Modifier.size(48.dp).clip(CircleShape)
                        .then(if (selected) Modifier.border(3.dp, Ink, CircleShape) else Modifier)
                        .semantics { contentDescription = name; this.selected = selected }
                        .clickable(role = Role.RadioButton) { GameSetting.setPetals(context, choice) }
                        .padding(5.dp), contentAlignment = Alignment.Center) {
                        Flower(Modifier.size(38.dp), petal = choice?.let { PetalColours[it] } ?: Color(0xFFFFE696),
                            petals = if (choice == Unlock.RainbowPetals) RainbowPetals else null)
                    }
                }
            }
        }
    }
}

/** A level or badge to celebrate. */
private sealed class GameMoment {
    data class LevelUp(val level: Int, val unlocks: List<Unlock>) : GameMoment()
    data class NewBadge(val kind: BadgeKind) : GameMoment()
    data class NewKeepsake(val keepsake: Keepsake) : GameMoment()
}

/**
 * Plays each new level and badge once, a beat after the tick that earned it so it does not land on
 * top of the check-in pop. Like the other moments, it takes no touches.
 */
@Composable fun GameMoments(report: GameReport, memory: GameMemory, quiet: Boolean = false) {
    var shown by remember { mutableStateOf<GameMoment?>(null) }
    val haptics = rememberHaptics()
    val today = LocalToday.current
    LaunchedEffect(report.level, report.badges.keys, report.keepsakes, quiet) {
        shown = null
        val keys = report.keepsakes.map { it.key() }
        val keptBefore = memory.keepsakes
        if (keptBefore == null) memory.keepsakes = keys.toSet()
        if (memory.level < 0 || quiet) {
            memory.level = maxOf(memory.level, report.level)
            memory.badges = memory.badges + report.badges.keys.map { it.name }
            memory.keepsakes = memory.keepsakes.orEmpty() + keys
            return@LaunchedEffect
        }
        val queue = mutableListOf<GameMoment>()
        if (report.level > memory.level) {
            queue += GameMoment.LevelUp(report.level, Unlock.entries.filter { it.level in (memory.level + 1)..report.level })
        }
        val seen = memory.badges
        report.badges.keys.filter { it.name !in seen }.forEach { queue += GameMoment.NewBadge(it) }
        // Only a keepsake earned just now is celebrated; one that a past season's recount turns up joins quietly.
        if (keptBefore != null) report.keepsakes.filter { it.key() !in keptBefore && !it.earnedOn.isBefore(today.minusDays(1)) }
            .forEach { queue += GameMoment.NewKeepsake(it) }
        memory.level = maxOf(memory.level, report.level)
        memory.badges = seen + report.badges.keys.map { it.name }
        memory.keepsakes = memory.keepsakes.orEmpty() + keys
        if (queue.isEmpty()) return@LaunchedEffect
        delay(1300)
        for (moment in queue) {
            shown = moment
            if (moment is GameMoment.LevelUp) haptics.pattern(*MilestoneBeats) else haptics.play(Feel.Confirm)
            delay(if (moment is GameMoment.LevelUp) 3000 else 2400)
            shown = null
            delay(250)
        }
    }
    when (val moment = shown) {
        is GameMoment.LevelUp -> LevelUpView(moment)
        is GameMoment.NewBadge -> BadgeLooks.getValue(moment.kind).let { look ->
            ToastView(moment, look.icon, stringResource(R.string.garden_new_badge), stringResource(look.title))
        }
        is GameMoment.NewKeepsake -> ToastView(moment, KeepsakeIcons.getValue(moment.keepsake.of.season)[moment.keepsake.tier],
            stringResource(R.string.garden_new_keepsake), stringResource(keepsakeName(moment.keepsake.of.season, moment.keepsake.tier)))
        null -> Unit
    }
}

@Composable private fun LevelUpView(moment: GameMoment.LevelUp) {
    val reduced = reducedMotion()
    val progress = remember(moment) { Animatable(0f) }
    LaunchedEffect(moment) { progress.animateTo(1f, tween(3000, easing = LinearEasing)) }
    val sparkleColors = confettiColors()
    val sparkles = remember(sparkleColors) { confetti(20, seed = 17, colors = sparkleColors) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!reduced) Canvas(Modifier.fillMaxSize()) {
            val p = progress.value
            val burst = easeOutCubic((p / .4f).coerceIn(0f, 1f))
            val fade = if (p < .8f) 1f else 1f - (p - .8f) / .2f
            sparkles.forEach { s ->
                val reach = (70.dp.toPx() + 130.dp.toPx() * s.speed) * burst
                val at = center + Offset(cos(s.angle) * reach * 1.1f, sin(s.angle) * reach * 1.3f)
                val twinkle = .6f + .4f * sin((p * 6f + s.drift) * 2f * PI.toFloat())
                drawSparkle(s.color.copy(alpha = fade), at, 9.dp.toPx() * s.size * twinkle)
            }
        }
        Column(
            Modifier.widthIn(max = 280.dp).padding(horizontal = 24.dp)
                .graphicsLayer {
                    val p = progress.value
                    val pop = if (reduced) 1f else easeOutBack((p / .18f).coerceIn(0f, 1f))
                    scaleX = .6f + .4f * pop; scaleY = scaleX
                    alpha = (p / .06f).coerceAtMost(1f) * if (p > .85f) 1f - (p - .85f) / .15f else 1f
                }
                .clip(RoundedCornerShape(28.dp)).background(Purple)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val (petal, petals) = chosenPetals(LocalGame.current)
            Flower(Modifier.size(72.dp), petal = petal, petals = petals, bloom = progress.value * 18f)
            Text(stringResource(R.string.garden_level, moment.level, levelName(moment.level)), color = Highlight,
                fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 31.sp, textAlign = TextAlign.Center)
            Text(stringResource(R.string.garden_level_up, levelName(moment.level)), color = OnAccent, fontSize = 14.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
            moment.unlocks.forEach { unlock ->
                Text(stringResource(R.string.garden_new_unlock, stringResource(unlockName(unlock))), color = OnAccent.copy(alpha = .9f),
                    fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
}

/** A small card that drops in from the top to announce a new badge or keepsake. */
@Composable private fun ToastView(key: Any, icon: ImageVector, label: String, title: String) {
    val reduced = reducedMotion()
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(2400, easing = LinearEasing)) }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 70.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.graphicsLayer {
                val p = progress.value
                val enter = if (reduced) 1f else easeOutBack((p / .2f).coerceIn(0f, 1f))
                translationY = (1f - enter) * -80.dp.toPx()
                alpha = (p / .08f).coerceAtMost(1f) * if (p > .85f) 1f - (p - .85f) / .15f else 1f
            }
                .clip(RoundedCornerShape(22.dp)).background(Ink)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .padding(14.dp, 10.dp, 20.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Yellow), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(22.dp).graphicsLayer {
                    val s = if (reduced) 1f else easeOutBack(((progress.value - .1f) / .25f).coerceIn(0f, 1f)); scaleX = s; scaleY = s
                }, tint = Color(0xFF33283E))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, color = OnInk.copy(alpha = .75f), fontSize = 11.sp, lineHeight = 14.sp)
                Text(title, color = OnInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
            }
        }
    }
}

/** Settings: the whole game can be switched off for a plain, quiet tracker. */
@Composable fun GameSettingCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    PlayCard(Mint) {
        Row(Modifier.fillMaxWidth().toggleable(GameSetting.enabled, role = Role.Switch) { GameSetting.setEnabled(context, it) }
            .testTag("game_switch"), verticalAlignment = Alignment.CenterVertically) {
            BubbleIcon(Icons.Rounded.EmojiEvents, Overlay); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_game_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_game_body), color = Quiet, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Switch(checked = GameSetting.enabled, onCheckedChange = null)
        }
    }
}
