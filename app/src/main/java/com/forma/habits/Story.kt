package com.forma.habits

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The weekly story: four short pages that replay the last seven days, played like a phone story.
 * Bars across the top fill as each page plays and it moves on by itself; tap the right side for the
 * next page and the left for the last, or press and hold to pause. Every page also has real buttons,
 * and with a screen reader or animations off it never moves on by itself.
 */

private const val StoryPages = 4
private const val PageMillis = 5_000f

@Composable fun WeekStoryDialog(state: HabitState, today: LocalDate, onClose: () -> Unit) {
    val frozen = LocalGame.current?.frozenDays ?: emptySet()
    val story = remember(state, today, frozen) { state.weekStory(today, frozen) }
    val reduced = reducedMotion()
    val context = LocalContext.current
    val talkBack = remember(context) {
        (context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as AccessibilityManager).isTouchExplorationEnabled
    }
    val autoplay = !reduced && !talkBack
    var page by rememberSaveable { mutableIntStateOf(0) }
    var played by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    // Plays the current page through, then turns to the next; the last page waits for "Done".
    LaunchedEffect(page, autoplay) {
        played = 0f
        if (!autoplay) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (played < 1f) {
            val now = withFrameNanos { it }
            if (!paused) played = (played + (now - last) / 1_000_000f / PageMillis).coerceAtMost(1f)
            last = now
        }
        if (page < StoryPages - 1) page++
    }
    val colors = listOf(Purple, Mint, Yellow, Peach)
    val onPage = if (page == 0) OnAccent else Ink
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(colors[page])
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { paused = true; tryAwaitRelease(); paused = false },
                    onTap = { at -> if (at.x < size.width / 3f) { if (page > 0) page-- } else if (page < StoryPages - 1) page++ }
                )
            }) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
                // The progress bars, one per page.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(StoryPages) { i ->
                        val fill = when { i < page -> 1f; i > page -> 0f; autoplay -> played; else -> 1f }
                        Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(onPage.copy(alpha = .25f))) {
                            Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(onPage))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.cd_story_page, page + 1, StoryPages), color = onPage.copy(alpha = .8f), fontSize = 12.sp,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.cd_story_close), tint = onPage) }
                }
                AnimatedContent(page, Modifier.weight(1f).fillMaxWidth(), transitionSpec = {
                    if (reduced) EnterTransition.None togetherWith ExitTransition.None
                    else {
                        val forward = targetState > initialState
                        (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { (if (forward) it else -it) / 4 } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { (if (forward) -it else it) / 4 } + fadeOut(tween(200)))
                    }
                }, label = "story page") { shown ->
                    Column(Modifier.fillMaxSize().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        when (shown) {
                            0 -> IntroPage(state.name, story, today)
                            1 -> TicksPage(story)
                            2 -> BestDayPage(story)
                            else -> StreakPage(story)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (page > 0) page-- }, enabled = page > 0) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_story_previous), tint = onPage.copy(alpha = if (page > 0) 1f else .3f))
                    }
                    Spacer(Modifier.weight(1f))
                    if (page < StoryPages - 1) IconButton(onClick = { page++ }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.cd_story_next), tint = onPage)
                    } else MainButton(stringResource(R.string.action_story_done), Modifier.width(150.dp), onClick = onClose)
                }
            }
        }
    }
}

@Composable private fun storyDate(pattern: String): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
}

/** Page one: the flower blooms open over the week's dates. */
@Composable private fun IntroPage(name: String, story: WeekStory, today: LocalDate) {
    val bloom by rememberGrow(6f, 1400, 150)
    Flower(Modifier.size(170.dp), bloom = bloom, alive = true)
    Spacer(Modifier.height(26.dp))
    Text(stringResource(R.string.story_intro_title, name), color = OnAccent, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    val short = storyDate("MMM d")
    Text(stringResource(R.string.story_intro_range, story.days.first().date.format(short), today.format(short)),
        color = OnAccent.copy(alpha = .85f), fontSize = 15.sp, textAlign = TextAlign.Center)
}

/** Page two: every tick of the week, counting up, over seven bars rising one after another. */
@Composable private fun TicksPage(story: WeekStory) {
    val count by rememberGrow(story.ticks.toFloat(), 1100, 200)
    Text("${count.roundToInt()}", color = Ink, fontSize = 96.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 100.sp)
    Text(pluralStringResource(R.plurals.story_ticks_caption, story.ticks), color = Ink, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(34.dp))
    Row(Modifier.height(150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        val letters = storyDate("EEEEE")
        story.days.forEachIndexed { i, day ->
            val rise by rememberGrow(if (day.rest) 0f else day.percent / 100f, 650, 350 + i * 90)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(26.dp).height((8 + 92 * rise).dp).clip(RoundedCornerShape(9.dp))
                    .background(if (day.rest) Ink.copy(alpha = .15f) else Ink))
                Spacer(Modifier.height(8.dp))
                Text(day.date.format(letters), color = Ink.copy(alpha = .75f), fontSize = 11.sp)
            }
        }
    }
}

/** Page three: the best day, and a star for every day with everything done. */
@Composable private fun BestDayPage(story: WeekStory) {
    val best = story.bestDay
    Text(stringResource(R.string.story_best_day_title), color = Ink, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(10.dp))
    if (best == null) {
        Flower(Modifier.size(120.dp), alive = true)
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.story_no_best_day), color = Ink, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        return
    }
    Text(best.date.format(storyDate("EEEE")), color = Ink, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
    Text(stringResource(R.string.story_best_day_caption, best.percent), color = Ink.copy(alpha = .8f), fontSize = 15.sp)
    if (story.perfectDays > 0) {
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(story.perfectDays) { i ->
                // Each star pops in after the last.
                val pop by rememberGrow(1f, 420, 300 + i * 160)
                Icon(Icons.Rounded.Star, null, Modifier.size(34.dp).graphicsLayer {
                    val s = easeOutBack(pop); scaleX = s; scaleY = s; alpha = pop.coerceIn(0f, 1f)
                }, tint = Ink)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(pluralStringResource(R.plurals.story_perfect_days, story.perfectDays, story.perfectDays), color = Ink, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** Page four: the longest streak, its flame burning at its size, and the habit done most. */
@Composable private fun StreakPage(story: WeekStory) {
    Text(stringResource(R.string.story_streak_title), color = Ink, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(18.dp))
    val grow by rememberGrow(1f, 900, 150)
    Flame(story.streak > 0, Modifier.size(84.dp).graphicsLayer { scaleX = .4f + .6f * easeOutBack(grow); scaleY = scaleX }, streak = story.streak)
    Spacer(Modifier.height(18.dp))
    val habit = story.streakHabit
    Text(if (habit != null) pluralStringResource(R.plurals.story_streak_caption, story.streak, habit, story.streak) else stringResource(R.string.story_no_streak),
        color = Ink, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    val top = story.topHabit
    if (top != null) {
        Spacer(Modifier.height(14.dp))
        Text(pluralStringResource(R.plurals.story_top_habit, story.topCount, top, story.topCount), color = Ink.copy(alpha = .8f), fontSize = 14.sp, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(30.dp))
    Text(stringResource(R.string.story_outro), color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
}

