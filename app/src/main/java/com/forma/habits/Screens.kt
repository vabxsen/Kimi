package com.forma.habits

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Built for the current locale; a top-level formatter would keep the locale the process started with. */
private val ShortDate: DateTimeFormatter @Composable get() = localizedPattern("EEE, MMM d")
private val MonthLabel: DateTimeFormatter @Composable get() = localizedPattern("MMMM yyyy")
@Composable private fun localizedPattern(pattern: String): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
}

/** Habit dayparts are stored as stable English keys; only the label on screen is translated. */
@Composable fun daypartLabel(key: String): String = when (key) {
    "Morning" -> stringResource(R.string.daypart_morning)
    "Afternoon" -> stringResource(R.string.daypart_afternoon)
    "Evening" -> stringResource(R.string.daypart_evening)
    else -> stringResource(R.string.daypart_anytime)
}

/** How far the Today strip scrolls either side of today. Older days live on the Calendar page. */
private const val StripPastDays = 90L
private const val StripFutureDays = 7L

@Composable fun TodayScreen(state: HabitState, onToggle: (Habit, LocalDate) -> Unit, onNew: () -> Unit) {
    val today = LocalToday.current
    val stats = rememberHabitStats(state, today)
    var dateString by rememberSaveable(today) { mutableStateOf(today.toString()) }
    val date = LocalDate.parse(dateString)
    var filter by rememberSaveable { mutableStateOf("All") }
    // Celebrates the moment the day is finished, not every later visit to a finished day.
    val haptics = LocalHapticFeedback.current
    val dayDone = !state.isRestDay(today) && state.percent(today) == 100
    var celebrations by remember { mutableIntStateOf(0) }
    var wasDone by remember { mutableStateOf(dayDone) }
    LaunchedEffect(dayDone) {
        if (dayDone && !wasDone) { celebrations++; haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
        wasDone = dayDone
    }
    // A streak reaching 7, 30 or 100 days gets its own moment, once per habit and milestone. Only
    // habits already on screen count, so a sync or restore that brings in a long streak stays quiet.
    var milestoneShows by remember { mutableIntStateOf(0) }
    var milestone by remember { mutableStateOf(0 to "") }
    var lastStreaks by remember { mutableStateOf(stats.streaks) }
    val celebrated = remember { mutableSetOf<String>() }
    LaunchedEffect(stats.streaks) {
        val reached = state.habits.firstNotNullOfOrNull { habit ->
            val before = lastStreaks[habit.id] ?: return@firstNotNullOfOrNull null
            val now = stats.streaks[habit.id] ?: 0
            StreakMilestones.lastOrNull { before < it && now >= it && celebrated.add("${habit.id}/$it") }?.let { it to habit.name }
        }
        lastStreaks = stats.streaks
        // A beat after the check-in pop, so the two moments do not land on top of each other.
        if (reached != null) { delay(650); milestone = reached; milestoneShows++ }
    }
    val reduced = reducedMotion()
    val list = rememberLazyListState()
    val cascade = rememberCascade()
    val arrivals = rememberArrivals { state.habits.map { it.id } }
    var headerHeight by remember { mutableIntStateOf(1) }
    val gap = with(LocalDensity.current) { 14.dp.toPx() }
    // How far the page has moved past the greeting. Read only inside layer blocks, so scrolling
    // redraws the header and banner without recomposing the screen.
    fun scrolled(): Float = when (list.firstVisibleItemIndex) {
        0 -> list.firstVisibleItemScrollOffset.toFloat()
        1 -> headerHeight + gap + list.firstVisibleItemScrollOffset
        else -> Float.MAX_VALUE
    }
    Box {
    LazyColumn(state = list, contentPadding = PaddingValues(23.dp, 10.dp, 23.dp, 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            // The greeting drifts up more slowly than the page, shrinking and fading as it goes.
            Row(Modifier.fillMaxWidth().cascaded(cascade.entrance(0)).onSizeChanged { headerHeight = it.height.coerceAtLeast(1) }
                .graphicsLayer {
                    if (reduced) return@graphicsLayer
                    val moved = scrolled().coerceAtMost(headerHeight.toFloat())
                    val fraction = moved / headerHeight
                    transformOrigin = TransformOrigin(0f, .5f)
                    alpha = 1f - .85f * fraction
                    scaleX = 1f - .1f * fraction; scaleY = scaleX
                    // A gentle lag; much more and the fading subtitle peeks out from behind the banner.
                    translationY = moved * .25f
                }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(ShortDate).uppercase(), fontSize = 10.sp, color = Quiet, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp)); Text(stringResource(R.string.today_greeting), style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(5.dp)); Text(stringResource(R.string.today_subtitle, state.name), color = Quiet, fontSize = 12.sp)
                }
                SkyBadge(Modifier.size(width = 96.dp, height = 62.dp))
            }
        }
        item {
            // The banner flower's petals fill as the day's habits are ticked off, and it drifts
            // behind the banner as the page scrolls.
            val dayProgress = if (state.isRestDay(date)) null else state.count(date).toFloat() / state.due(date).size
            val parallax = Modifier.graphicsLayer { if (!reduced) translationY = scrolled().coerceAtMost(900f) * .3f }
            BoxWithConstraints(Modifier.fillMaxWidth().cascaded(cascade.entrance(1)).clip(RoundedCornerShape(27.dp)).background(Lavender).padding(18.dp)) {
                val expandedText = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.2f
                Column(Modifier.fillMaxWidth(if (expandedText) 1f else .64f)) {
                    Pill(stringResource(R.string.today_banner_pill), Overlay)
                    Spacer(Modifier.height(9.dp))
                    Text(stringResource(R.string.today_banner_title), style = MaterialTheme.typography.titleLarge, fontSize = 23.sp, lineHeight = 28.sp)
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp))
                        Text(if (state.isRestDay(date)) stringResource(R.string.today_banner_rest)
                            else pluralStringResource(R.plurals.today_banner_progress, state.due(date).size, state.count(date), state.due(date).size), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    if (expandedText) Flower(Modifier.align(Alignment.End).size(86.dp).then(parallax), progress = dayProgress, alive = true)
                }
                if (!expandedText) Flower(Modifier.align(Alignment.CenterEnd).size(111.dp).offset(x = 9.dp).then(parallax), progress = dayProgress, alive = true)
            }
        }
        item {
            BoxWithConstraints(Modifier.cascaded(cascade.entrance(2))) {
                val restDay = state.isRestDay(date)
                // The streak rolls like an odometer and the percentage counts toward its new value.
                val streakValue: @Composable () -> Unit = {
                    Rolling(stats.bestStreak) { n -> Text(pluralStringResource(R.plurals.streak_days, n, n), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
                val streakIcon: @Composable () -> Unit = { Flame(stats.bestStreak > 0, Modifier.size(28.dp)) }
                val streakCaption = stringResource(R.string.today_streak_caption)
                val shownPercent by animateIntAsState(state.percent(date), tween(600), label = "percent complete")
                val progressText = if (restDay) stringResource(R.string.today_rest_day) else stringResource(R.string.today_percent_complete, shownPercent)
                val progressValue: @Composable () -> Unit = { Text(progressText, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                val progressIcon: @Composable () -> Unit = { Icon(Icons.Rounded.Stars, null, tint = Ink, modifier = Modifier.size(28.dp)) }
                val progressCaption = if (restDay) stringResource(R.string.today_rest_caption) else stringResource(R.string.today_percent_caption)
                val stackStats = maxWidth < 344.dp || LocalDensity.current.fontScale > 1.2f
                if (stackStats) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WideStat(Peach, streakIcon, streakValue, streakCaption)
                    WideStat(Mint, progressIcon, progressValue, progressCaption)
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Peach).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        streakIcon(); Spacer(Modifier.width(10.dp))
                        Column { streakValue(); Text(streakCaption, fontSize = 10.sp, color = Quiet) }
                    }
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Mint).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        progressIcon(); Spacer(Modifier.width(10.dp))
                        Column { progressValue(); Text(progressCaption, fontSize = 10.sp, color = Quiet) }
                    }
                }
            }
        }
        item {
            // A scrolling strip rather than one fixed week, so recent days stay reachable without
            // leaving Today. Cells keep the width they had when exactly seven filled the row, so
            // the page looks unchanged until you actually drag it.
            BoxWithConstraints(Modifier.cascaded(cascade.entrance(3))) {
                val dayWidth = (maxWidth - 7.dp * 6) / 7
                val strip = rememberLazyListState()
                val centre = with(LocalDensity.current) { ((maxWidth - dayWidth) / 2).roundToPx() }
                // Re-centres only when the day itself rolls over. Keying this on the selection
                // instead would drag the strip back every time a past day was tapped.
                LaunchedEffect(today, centre) { strip.scrollToItem(StripPastDays.toInt(), -centre) }
                val lastIndex = (StripPastDays + StripFutureDays).toInt()
                val selectedIndex = (StripPastDays - ChronoUnit.DAYS.between(date, today)).toInt().coerceIn(0, lastIndex)
                // One purple pill glides between days. Each cell paints only the part of the pill that
                // overlaps it, so the pill slides along the strip without a layer over the items.
                val pill = animateFloatAsState(selectedIndex.toFloat(),
                    if (reducedMotion()) snap() else spring(dampingRatio = .72f, stiffness = 520f), label = "day pill")
                val step = with(LocalDensity.current) { (dayWidth + 7.dp).toPx() }
                val purple = Purple
                val dayShape = RoundedCornerShape(17.dp)
                LazyRow(state = strip, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(lastIndex + 1) { index ->
                        val d = today.minusDays(StripPastDays - index); val selected = d == date
                        val cover = (1f - abs(pill.value - index)).coerceIn(0f, 1f)
                        val dayLabel = stringResource(if (selected) R.string.cd_day_selected else R.string.cd_day, d.format(ShortDate))
                        val press = rememberSquish(.9f)
                        Column(Modifier.width(dayWidth).squish(press).clip(dayShape).background(Paper)
                            .drawBehind {
                                val shift = (pill.value - index) * step
                                if (abs(shift) < size.width) drawRoundRect(purple, Offset(shift, 0f), size, CornerRadius(17.dp.toPx()))
                            }
                            .then(if (cover > .5f) Modifier else Modifier.lightCardBorder(dayShape))
                            .clickable(press.interaction, LocalIndication.current, enabled = !d.isAfter(today), role = Role.Button) { dateString = d.toString() }
                            .semantics { contentDescription = dayLabel }
                            .padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(weekdayInitial(d.dayOfWeek), fontSize = 10.sp, color = lerp(Quiet, OnAccent, cover))
                            Spacer(Modifier.height(7.dp)); Text(d.dayOfMonth.toString(), fontSize = 17.sp, fontWeight = FontWeight.Bold,
                                color = lerp(if (d.isAfter(today)) Quiet.copy(alpha = .78f) else Ink, OnAccent, cover))
                            Spacer(Modifier.height(7.dp)); Box(Modifier.size(4.dp).background(lerp(if (LocalKimiPalette.current.dark) Lavender else Accent, Highlight, cover), CircleShape))
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.cascaded(cascade.entrance(4))) {
            SectionTitle(if (date == today) stringResource(R.string.today_section) else date.format(ShortDate), stringResource(R.string.action_new_habit), onNew)
            if (date != today && state.due(date).isNotEmpty()) {
                Text(stringResource(R.string.checkins_today_only), color = Quiet, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                (listOf("All") + Dayparts).forEach { item ->
                    val label = if (item == "All") stringResource(R.string.filter_all) else daypartLabel(item)
                    FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(label, fontSize = 11.sp) }, shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = if (LocalKimiPalette.current.dark) Color.Transparent else Paper,
                            labelColor = if (LocalKimiPalette.current.dark) Quiet else Ink,
                            selectedContainerColor = Ink, selectedLabelColor = OnInk),
                        border = if (LocalKimiPalette.current.dark || filter == item) null else BorderStroke(1.dp, LightControlOutline))
                }
            }
            }
        }
        val habits = state.due(date).filter { filter == "All" || it.time == filter }
        // Rows slide into place when the filter or the list changes, and a habit planted since the
        // screen opened sprouts up instead of just appearing.
        itemsIndexed(habits, key = { _, habit -> habit.id }) { index, habit ->
            val fresh = remember { arrivals.isNew(habit.id) }
            HabitRow(habit, state, date, { onToggle(habit, date) },
                listMotion().cascaded(cascade.entrance(5 + index)).sprouted(rememberSprout(fresh)))
        }
        if (habits.isEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EmptySpace(stringResource(R.string.today_empty_title), stringResource(
                    if (state.habits.isEmpty()) R.string.today_empty_first_body else R.string.today_empty_body))
                if (state.habits.isEmpty()) MainButton(stringResource(R.string.action_plant_new_habit), onClick = onNew)
            }
        }
        if (state.demo) item { Text(stringResource(R.string.demo_footer_today), color = Quiet, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
    }
    DayCompleteCelebration(celebrations)
    MilestoneMoment(milestoneShows, milestone.first, milestone.second)
    }
}

@Composable fun HabitRow(habit: Habit, state: HabitState, date: LocalDate, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val today = LocalToday.current
    val checked = state.done(habit.id, date)
    val haptics = LocalHapticFeedback.current
    val background by animateColorAsState(TileColors[habit.color.coerceIn(0, 5)], label = "habit color")
    val toggleLabel = stringResource(if (checked) R.string.cd_undo_habit else R.string.cd_complete_habit, habit.name)
    // Pops only on a fresh tick, never for a habit that was already done when the row appeared.
    val burst = rememberBurst()
    var wasChecked by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        val fresh = checked && !wasChecked
        wasChecked = checked
        if (fresh) burst.fire()
    }
    val press = rememberSquish(.97f)
    Row(modifier.fillMaxWidth().squish(press)
        .drawBurst(burst) { Offset(size.width - 39.dp.toPx(), size.height / 2) }
        .clip(RoundedCornerShape(22.dp)).background(background)
        .toggleable(value = checked, interactionSource = press.interaction, indication = LocalIndication.current,
            enabled = date == today, role = Role.Checkbox, onValueChange = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onToggle()
        }).semantics { contentDescription = toggleLabel }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        BubbleIcon(HabitSymbols[habit.icon.coerceIn(HabitSymbols.indices)], Overlay, size = 44)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(habit.name, style = MaterialTheme.typography.titleMedium, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp)); Text(habit.goal, fontSize = 10.sp, color = Quiet)
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CheckCircle(checked) }
    }
}

@Composable fun HabitsScreen(state: HabitState, onNew: () -> Unit, onEdit: (Habit) -> Unit) {
    val today = LocalToday.current
    val stats = rememberHabitStats(state, today)
    var query by rememberSaveable { mutableStateOf("") }
    val cascade = rememberCascade()
    val arrivals = rememberArrivals { state.habits.map { it.id } }
    LazyColumn(contentPadding = PaddingValues(23.dp, 17.dp, 23.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle(stringResource(R.string.habits_title), stringResource(R.string.habits_subtitle)) }
        item {
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.habits_search_label)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier.fillMaxWidth().cascaded(cascade.entrance(0)), shape = RoundedCornerShape(18.dp), singleLine = true)
        }
        item { Text(pluralStringResource(R.plurals.habits_collection_count, state.habits.size, state.habits.size), style = MaterialTheme.typography.titleLarge) }
        // Search results slide together as the query narrows them, and a new habit sprouts in.
        itemsIndexed(state.habits.filter { it.name.contains(query, true) }, key = { _, h -> h.id }) { index, h ->
            val editLabel = stringResource(R.string.cd_edit_habit, h.name)
            val fresh = remember { arrivals.isNew(h.id) }
            PlayCard(TileColors[h.color.coerceIn(0, 5)], listMotion().cascaded(cascade.entrance(1 + index)).sprouted(rememberSprout(fresh))
                .semantics { contentDescription = editLabel }, { onEdit(h) }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BubbleIcon(HabitSymbols[h.icon.coerceIn(HabitSymbols.indices)], Overlay, size = 51)
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(h.name, style = MaterialTheme.typography.titleMedium); Text(h.goal, color = Quiet, fontSize = 11.sp) }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Edit, null, Modifier.size(21.dp)) }
                }
                Spacer(Modifier.height(18.dp))
                val week = (0..6).map { today.minusDays((6 - it).toLong()) }
                val scheduledDays = week.count { h.isDue(it) }
                val weekLabel = pluralStringResource(R.plurals.cd_habit_week, scheduledDays,
                    week.count { state.done(h.id, it) }, scheduledDays)
                Row(Modifier.fillMaxWidth().semantics { contentDescription = weekLabel }, horizontalArrangement = Arrangement.SpaceBetween) {
                    week.forEach { d ->
                        val done = state.done(h.id, d); val scheduled = h.isDue(d)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(weekdayInitial(d.dayOfWeek), fontSize = 10.sp, color = Quiet)
                            Spacer(Modifier.height(6.dp))
                            // A day this habit was never due on is left blank, not marked missed.
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (done) Ink else if (scheduled) Overlay else Color.Transparent), contentAlignment = Alignment.Center) {
                                when {
                                    done -> Icon(Icons.Rounded.Check, null, Modifier.size(17.dp), tint = OnInk)
                                    scheduled -> Text("·", color = Quiet)
                                    else -> Text(stringResource(R.string.rest_marker), color = Quiet.copy(alpha = .68f), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(17.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Pill(pluralStringResource(R.plurals.streak_days, stats.streaks[h.id] ?: 0, stats.streaks[h.id] ?: 0), Overlay, Icons.Rounded.LocalFireDepartment)
                    Text(stringResource(R.string.habits_schedule_summary,
                        stringResource(if (h.weekdays) R.string.schedule_weekdays else R.string.schedule_every_day), daypartLabel(h.time)),
                        fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        }
        if (state.habits.isNotEmpty() && state.habits.none { it.name.contains(query, true) }) item { EmptySpace(stringResource(R.string.habits_empty_search_title), stringResource(R.string.habits_empty_search_body)) }
        if (state.habits.isEmpty()) item { EmptySpace(stringResource(R.string.habits_empty_title), stringResource(R.string.habits_empty_body)) }
        item { MainButton(stringResource(R.string.action_plant_new_habit), onClick = onNew) }
    }
}

@Composable fun CalendarScreen(state: HabitState, onToggle: (Habit, LocalDate) -> Unit) {
    val today = LocalToday.current
    var monthString by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var dateString by rememberSaveable { mutableStateOf(today.toString()) }
    val month = YearMonth.parse(monthString); val date = LocalDate.parse(dateString)
    LazyColumn(contentPadding = PaddingValues(23.dp, 17.dp, 23.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(19.dp)) {
        item { PageTitle(stringResource(R.string.calendar_title), stringResource(R.string.calendar_subtitle)) }
        item {
            PlayCard(Paper) {
                val canGoBack = month.year > 1970 || month.monthValue > 1
                val canGoForward = month.year < 2200 || month.monthValue < 12
                val reduced = reducedMotion()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton({ monthString = month.minusMonths(1).toString() }, enabled = canGoBack) { Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.cd_prev_month)) }
                    AnimatedContent(month, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "month label") { shown ->
                        Text(shown.format(MonthLabel), style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton({ monthString = month.plusMonths(1).toString() }, enabled = canGoForward) { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.cd_next_month)) }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) { weekdayInitials().forEach { Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Quiet, fontSize = 10.sp) } }
                Spacer(Modifier.height(12.dp))
                // Months slide in from the side you are heading to, by arrow or by a sideways swipe.
                val swipe = with(LocalDensity.current) { 56.dp.toPx() }
                AnimatedContent(month, Modifier.pointerInput(monthString) {
                    var drag = 0f
                    detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                        if (drag < -swipe && canGoForward) monthString = month.plusMonths(1).toString()
                        else if (drag > swipe && canGoBack) monthString = month.minusMonths(1).toString()
                    }) { change, amount -> change.consume(); drag += amount }
                }, transitionSpec = {
                    if (reduced) EnterTransition.None togetherWith ExitTransition.None
                    else {
                        val forward = targetState > initialState
                        (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { if (forward) it else -it } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { if (forward) -it else it } + fadeOut(tween(160))) using
                            SizeTransform(clip = true)
                    }
                }, label = "month") { shown ->
                Column {
                val offset = shown.atDay(1).dayOfWeek.value - 1
                val cells = (offset + shown.lengthOfMonth() + 6) / 7 * 7
                (0 until cells).chunked(7).forEachIndexed { row, week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        week.forEachIndexed { column, n ->
                            val day = n - offset + 1
                            if (day !in 1..shown.lengthOfMonth()) Box(Modifier.weight(1f).height(44.dp))
                            else {
                                // Days pop in on a diagonal sweep from the top-left whenever a month arrives.
                                val pop = rememberGrow(1f, 300, delayMillis = (row + column) * 24)
                                val press = rememberSquish(.88f)
                                val d = shown.atDay(day); val pct = state.percent(d); val selected = d == date
                                val rest = state.isRestDay(d)
                                val dayLabel = if (rest) stringResource(R.string.cd_calendar_day_rest, d.format(ShortDate))
                                    else pluralStringResource(R.plurals.cd_calendar_day, state.count(d), d.format(ShortDate), state.count(d))
                                // A rest day stays unfilled so it never reads as a day you missed.
                                val fill by animateColorAsState(if (selected) Purple else if (rest) Color.Transparent else if (pct == 100) Mint else if (pct > 0) Lavender else Cream, label = "day fill")
                                val ink by animateColorAsState(if (selected) OnAccent else if (rest) Quiet.copy(alpha = .72f) else Ink, label = "day ink")
                                Box(Modifier.weight(1f).height(44.dp)
                                    .graphicsLayer {
                                        val p = pop.value
                                        val s = (.55f + .45f * easeOutBack(p)) * press.scale.value
                                        scaleX = s; scaleY = s; alpha = p
                                    }
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(fill)
                                    .clickable(press.interaction, LocalIndication.current, role = Role.Button) { dateString = d.toString() }
                                    .semantics { contentDescription = dayLabel }, contentAlignment = Alignment.Center) {
                                    Text(day.toString(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ink)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(Lavender, stringResource(R.string.calendar_legend_some))
                    LegendDot(Mint, stringResource(R.string.calendar_legend_all))
                    LegendDot(Quiet.copy(alpha = .55f), stringResource(R.string.calendar_legend_rest))
                }
            }
        }
        item {
            PlayCard(Peach) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Flower(Modifier.size(65.dp)); Spacer(Modifier.width(14.dp))
                    Column { Text(stringResource(R.string.calendar_encourage_title), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.calendar_encourage_body), fontSize = 11.sp, color = Quiet) }
                }
            }
        }
        item {
            SectionTitle(date.format(ShortDate), stringResource(R.string.action_today)) { monthString = YearMonth.from(today).toString(); dateString = today.toString() }
            if (date != today && state.due(date).isNotEmpty()) Text(stringResource(R.string.checkins_today_only), color = Quiet, fontSize = 11.sp)
        }
        items(state.due(date), key = { it.id }) { HabitRow(it, state, date, { onToggle(it, date) }, listMotion()) }
        if (state.due(date).isEmpty()) item { EmptySpace(stringResource(R.string.calendar_empty_title), stringResource(R.string.calendar_empty_body)) }
    }
}

@Composable fun InsightsScreen(state: HabitState) {
    val today = LocalToday.current
    val enlargedText = LocalDensity.current.fontScale > 1.2f
    val stats = rememberHabitStats(state, today)
    val summary = rememberInsights(state, today)
    // The page arrives with its numbers counting up and its bars growing in, one after another.
    val count by rememberGrow(summary.wins.toFloat())
    val percent by rememberGrow(summary.percent.toFloat(), 1100)
    val best by rememberGrow(stats.bestStreak.toFloat())
    val cascade = rememberCascade()
    LazyColumn(contentPadding = PaddingValues(23.dp, 17.dp, 23.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageTitle(stringResource(R.string.insights_title), stringResource(R.string.insights_subtitle)) }
        item {
            PlayCard(Purple, Modifier.cascaded(cascade.entrance(0))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.insights_energy_label), color = OnAccent, fontSize = 10.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(10.dp)); Text(stringResource(R.string.percent_value, percent.roundToInt()), color = Highlight, fontWeight = FontWeight.ExtraBold, fontSize = 49.sp)
                        Text(stringResource(R.string.insights_consistency), color = OnAccent, fontSize = 12.sp)
                    }
                    Flower(Modifier.size(119.dp), petal = Petal, alive = true)
                }
            }
        }
        item {
            Row(Modifier.cascaded(cascade.entrance(1)).then(if (enlargedText) Modifier.height(IntrinsicSize.Min) else Modifier), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayCard(Yellow, Modifier.weight(1f).then(if (enlargedText) Modifier.fillMaxHeight() else Modifier)) { Icon(Icons.Rounded.Bolt, null); Spacer(Modifier.height(9.dp)); Text("${count.roundToInt()}", style = MaterialTheme.typography.headlineMedium); Text(stringResource(R.string.insights_wins), fontSize = 11.sp, color = Quiet) }
                PlayCard(Pink, Modifier.weight(1f).then(if (enlargedText) Modifier.fillMaxHeight() else Modifier)) { Flame(stats.bestStreak > 0); Spacer(Modifier.height(9.dp)); Text("${best.roundToInt()}", style = MaterialTheme.typography.headlineMedium); Text(stringResource(R.string.insights_best_streak), fontSize = 11.sp, color = Quiet) }
            }
        }
        item {
            PlayCard(Paper, Modifier.cascaded(cascade.entrance(2))) {
                Text(stringResource(R.string.insights_week_chart), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth().then(if (enlargedText) Modifier.heightIn(min = 165.dp) else Modifier.height(165.dp)), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Bottom) {
                    summary.week.forEachIndexed { i, day ->
                        val barLabel = if (day.rest) stringResource(R.string.cd_chart_bar_rest, day.date.format(ShortDate))
                            else pluralStringResource(R.plurals.cd_chart_bar, day.percent, day.date.format(ShortDate), day.percent)
                        Column(Modifier.weight(1f).semantics { contentDescription = barLabel }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            // A rest day shows a dash and a flat marker; charting it as 0% would
                            // make a perfect week look like it collapsed every weekend.
                            val rise by rememberGrow(if (day.rest) 0f else day.percent.toFloat(), 700, delayMillis = i * 70)
                            Text(if (day.rest) stringResource(R.string.rest_marker) else stringResource(R.string.percent_value, rise.roundToInt()),
                                fontSize = 9.sp, color = Quiet); Spacer(Modifier.height(7.dp))
                            // Scaled so a 100% bar plus both labels fits the 165dp chart; 1.05 pushed that day's letter below the rest.
                            Box(Modifier.fillMaxWidth().height(if (day.rest) 4.dp else (rise * .95f + 4).dp).clip(RoundedCornerShape(11.dp))
                                .background(if (day.rest) Quiet.copy(alpha = .25f) else if (i == 6) Purple else TileColors[i % 6]))
                            Spacer(Modifier.height(9.dp)); Text(weekdayInitial(day.date.dayOfWeek), fontSize = 10.sp, color = Quiet)
                        }
                    }
                }
            }
        }
        item { SectionTitle(stringResource(R.string.insights_habits_section)) }
        itemsIndexed(state.habits, key = { _, habit -> habit.id }) { index, habit ->
            val pct = summary.consistency[habit.id] ?: 0
            val fill = rememberGrow(pct / 100f, 800, 150)
            PlayCard(TileColors[habit.color.coerceIn(0, 5)], listMotion().cascaded(cascade.entrance(3 + index))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(HabitSymbols[habit.icon.coerceIn(HabitSymbols.indices)], null, Modifier.size(24.dp)); Spacer(Modifier.width(10.dp))
                    Text(habit.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontSize = 13.sp)
                    Text(stringResource(R.string.percent_value, pct), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { fill.value }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Ink, trackColor = Overlay, drawStopIndicator = {})
            }
        }
        item { Text(stringResource(R.string.insights_footnote), color = Quiet, fontSize = 10.sp) }
    }
}

@Composable private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, fontSize = 10.sp, color = Quiet)
    }
}

@Composable private fun WideStat(color: Color, icon: @Composable () -> Unit, value: @Composable () -> Unit, caption: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        icon(); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            value()
            Text(caption, fontSize = 10.sp, color = Quiet)
        }
    }
}

private val MoodIcons = listOf(Icons.Rounded.SentimentVeryDissatisfied, Icons.Rounded.SentimentNeutral,
    Icons.Rounded.SentimentSatisfied, Icons.Rounded.SentimentVerySatisfied, Icons.Rounded.Celebration)
@Composable private fun moodNames(): Array<String> = stringArrayResource(R.array.mood_names)

/** Locale-correct single-letter column headings, Monday first, matching the grid layout. */
@Composable private fun weekdayInitials(): List<String> = (0..6).map { weekdayInitial(java.time.DayOfWeek.of(it + 1)) }

@Composable private fun weekdayInitial(day: java.time.DayOfWeek): String =
    day.getDisplayName(java.time.format.TextStyle.NARROW, LocalConfiguration.current.locales[0])

@Composable fun JournalScreen(state: HabitState, onSave: (Int, String) -> Unit, onDelete: (LocalDate) -> Unit,
    mood: Int, text: String, onDraft: (Int, String) -> Unit, draftDate: LocalDate,
    onEditDate: (LocalDate) -> Unit, draftDates: List<LocalDate>) {
    val today = LocalToday.current
    var deleteDate by remember { mutableStateOf<LocalDate?>(null) }
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun editDate(date: LocalDate) { onEditDate(date); scope.launch { scroll.animateScrollToItem(0) } }
    val reduced = reducedMotion()
    val cascade = rememberCascade()
    val arrivals = rememberArrivals { state.journal.map { it.date.toString() } }
    // Saving folds a paper copy of the note shut and drops it toward the history below. The real
    // field stays put underneath, so nothing the person typed ever disappears from view.
    val fold = remember { Animatable(1f) }
    var folded by remember { mutableStateOf("") }
    fun save() {
        onSave(mood, text)
        if (reduced) return
        folded = text
        scope.launch { fold.snapTo(0f); fold.animateTo(1f, tween(700, easing = LinearEasing)) }
    }
    LazyColumn(state = scroll, contentPadding = PaddingValues(23.dp, 17.dp, 23.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageTitle(stringResource(R.string.journal_title), stringResource(R.string.journal_subtitle)) }
        if (draftDate != today || draftDates.isNotEmpty()) item {
            Column {
                if (draftDate != today) { Text(stringResource(R.string.journal_writing_for, draftDate.format(ShortDate)), color = Accent); TextButton(onClick = { editDate(today) }) { Text(stringResource(R.string.journal_back_to_today)) } }
                draftDates.filter { it != draftDate }.forEach { date -> TextButton(onClick = { editDate(date) }) { Text(stringResource(R.string.journal_continue_draft, date.format(ShortDate))) } }
            }
        }
        item {
            PlayCard(Pink, Modifier.cascaded(cascade.entrance(0))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(stringResource(R.string.journal_moods_title), style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.journal_moods_body), fontSize = 12.sp, color = Quiet) }
                    Flower(Modifier.size(100.dp), mood = mood, petal = Petal, alive = true)
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.journal_prompt))
            Spacer(Modifier.height(15.dp))
            val names = moodNames()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MoodIcons.forEachIndexed { index, icon ->
                    val tile by animateColorAsState(if (index == mood) Yellow else Paper, label = "mood tile")
                    val press = rememberSquish(.9f)
                    Column(Modifier.weight(1f).squish(press).clip(RoundedCornerShape(18.dp)).background(tile)
                        .semantics { selected = index == mood }
                        .clickable(press.interaction, LocalIndication.current, role = Role.RadioButton) { onDraft(index, text) }.padding(vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        HopIcon(icon, index == mood, Modifier.size(28.dp), names[index]); Spacer(Modifier.height(8.dp)); Text(names[index], fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Box {
                OutlinedTextField(text, { onDraft(mood, it.take(10000)) }, label = { Text(stringResource(R.string.journal_field_label)) }, placeholder = { Text(stringResource(R.string.journal_field_placeholder)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp), shape = RoundedCornerShape(22.dp), minLines = 5)
                if (fold.isRunning) Box(Modifier.matchParentSize()
                    .graphicsLayer {
                        val p = fold.value
                        // First the page folds up toward its top edge, then it tips and falls away.
                        val closing = easeOutCubic((p / .45f).coerceIn(0f, 1f))
                        val falling = ((p - .35f) / .65f).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(.5f, 0f)
                        scaleY = 1f - .82f * closing
                        scaleX = 1f - .12f * closing
                        rotationX = 35f * closing
                        translationY = falling * falling * 260.dp.toPx()
                        alpha = 1f - falling
                    }
                    .clip(RoundedCornerShape(22.dp)).background(Paper).lightCardBorder(RoundedCornerShape(22.dp)).padding(16.dp)) {
                    Text(folded, style = MaterialTheme.typography.bodyMedium, color = Quiet, maxLines = 6)
                }
            }
            Spacer(Modifier.height(14.dp)); MainButton(stringResource(R.string.action_save_reflection), enabled = text.isNotBlank()) { save() }
            Spacer(Modifier.height(10.dp)); Text(stringResource(R.string.journal_privacy), color = Quiet, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        item { SectionTitle(stringResource(R.string.journal_history_section)) }
        itemsIndexed(state.journal.sortedByDescending { it.date }, key = { _, entry -> entry.date.toString() }) { index, entry ->
            // A new entry sprouts in where the folded note lands; an edited one gives a little bounce.
            val fresh = remember { arrivals.isNew(entry.date.toString()) }
            val bounce = remember { Animatable(1f) }
            var shown by remember { mutableStateOf(entry.text to entry.mood) }
            LaunchedEffect(entry.text, entry.mood) {
                if (shown == (entry.text to entry.mood)) return@LaunchedEffect
                shown = entry.text to entry.mood
                if (reduced) return@LaunchedEffect
                delay(380)
                bounce.snapTo(.94f)
                bounce.animateTo(1f, spring(dampingRatio = .4f, stiffness = Spring.StiffnessMediumLow))
            }
            PlayCard(Paper, listMotion().cascaded(cascade.entrance(3 + index)).sprouted(rememberSprout(fresh))
                .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.date.format(ShortDate), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Pill(moodNames()[entry.mood.coerceIn(0, 4)], Yellow, MoodIcons[entry.mood.coerceIn(0, 4)])
                }
                Spacer(Modifier.height(12.dp)); Text(entry.text, style = MaterialTheme.typography.bodyMedium, color = Quiet)
                Row(Modifier.align(Alignment.End)) {
                    TextButton(onClick = { editDate(entry.date) }) { Text(stringResource(R.string.action_edit), fontSize = 11.sp) }
                    TextButton(onClick = { deleteDate = entry.date }) { Text(stringResource(R.string.action_delete), fontSize = 11.sp) }
                }
            }
        }
    }
    deleteDate?.let { date -> AlertDialog(onDismissRequest = { deleteDate = null }, title = { Text(stringResource(R.string.dialog_delete_reflection_title)) }, text = { Text(stringResource(R.string.dialog_delete_reflection_body)) },
        confirmButton = { TextButton(onClick = { onDelete(date); deleteDate = null }) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = { deleteDate = null }) { Text(stringResource(R.string.action_keep_it)) } }) }
}

@Composable fun SettingsScreen(
    state: HabitState,
    onRename: (String, (Boolean) -> Unit) -> Unit,
    onSaveHabit: (Habit, (Boolean) -> Unit) -> Unit,
    account: KimiAccount? = null,
    onAccount: () -> Unit = {},
    syncable: Boolean = false,
    syncing: Boolean = false,
    syncFailed: Boolean = false,
    syncError: String? = null,
    onRetrySync: () -> Unit = {}
) {
    var credits by remember { mutableStateOf(false) }
    /**
     * The name card sits in a LazyColumn item, and Compose disposes items that scroll out of the
     * viewport - which the keyboard causes by resizing the window. Holding the flag here keeps edit
     * mode alive across that, while plain `remember` still drops it on a process restart, so Kimi
     * never reopens with a live field and the keyboard climbing over the page.
     */
    var editingName by remember(state.name) { mutableStateOf(false) }
    // The cards rise in one after another as the page opens.
    val cascade = rememberCascade()
    LazyColumn(contentPadding = PaddingValues(23.dp, 17.dp, 23.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageTitle(stringResource(R.string.settings_title), stringResource(R.string.settings_subtitle)) }
        item {
            PlayCard(Lavender, Modifier.cascaded(cascade.entrance(0))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Flower(Modifier.size(85.dp), alive = true); Spacer(Modifier.width(17.dp))
                    Column(Modifier.weight(1f)) { Text(stringResource(R.string.settings_team, state.name), style = MaterialTheme.typography.headlineMedium); Text(stringResource(R.string.settings_team_caption), color = Quiet, fontSize = 12.sp) }
                }
            }
        }
        item { Box(Modifier.cascaded(cascade.entrance(1))) { NameCard(state.name, editingName, { editingName = it }, onRename) } }
        item { Box(Modifier.cascaded(cascade.entrance(2))) { AccountCard(account, onAccount) } }
        if (syncable) item { Box(Modifier.cascaded(cascade.entrance(3))) { SyncStatusCard(syncing, syncFailed, syncError, onRetrySync) } }
        item { Box(Modifier.cascaded(cascade.entrance(4))) { AppearanceCard() } }
        item { Box(Modifier.cascaded(cascade.entrance(5))) { NotificationSettings(state.habits, onSaveHabit) } }
        if (state.demo) item { Text(stringResource(R.string.demo_footer_settings), color = Quiet, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        item { UpdateCard() }
        item { AboutCard { credits = true } }
    }
    if (credits) CreditsDialog { credits = false }
}

@Composable private fun SyncStatusCard(syncing: Boolean, failed: Boolean, error: String?, onRetry: () -> Unit) {
    val icon = when {
        failed -> Icons.Rounded.CloudOff
        syncing -> Icons.Rounded.Sync
        else -> Icons.Rounded.CloudDone
    }
    val title = stringResource(when {
        failed -> R.string.sync_failed_title
        syncing -> R.string.syncing_title
        else -> R.string.synced_title
    })
    val body = if (failed && error != null) error else stringResource(when {
        failed -> R.string.sync_failed_body
        syncing -> R.string.syncing_body
        else -> R.string.synced_body
    })
    // The arrows turn while a sync runs, and the icon pops into a cloud with a tick when it is done.
    val turning = if (syncing && !failed && !reducedMotion()) {
        rememberInfiniteTransition(label = "sync").animateFloat(0f, -360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "sync turn")
    } else null
    PlayCard(if (failed) Peach else Blue) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(icon, transitionSpec = {
                (scaleIn(spring(dampingRatio = .45f, stiffness = Spring.StiffnessMedium)) + fadeIn()) togetherWith (scaleOut() + fadeOut())
            }, label = "sync icon") { shown ->
                BubbleIcon(shown, Overlay, iconModifier = Modifier.graphicsLayer { rotationZ = turning?.value ?: 0f })
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, color = Quiet, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (failed) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp)) {
                Text(stringResource(R.string.action_retry_sync))
            }
        }
    }
}

private const val WaterRippleDurationMillis = 380
/**
 * Fixed colours, not palette tokens: water looks like water in either theme.
 *
 * The crest is a pale cyan rather than white on purpose - a white highlight is invisible on the
 * white card this ripple runs over, which left the whole effect reading as a haze. Tinting it means
 * both halves of the wave carry, on Paper and after dark alike.
 */
private val WaterCrest = Color(0xFFCDEBFF)
private val WaterShade = Color(0xFF3E6488)

/**
 * Material's check comes in one fixed, fairly light weight. This one is a stroke, so the tick that
 * saves a name can be a little bolder. Its colour is only a placeholder: [Icon] tints it.
 */
private val BoldCheck = ImageVector.Builder(
    name = "BoldCheck", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 3f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(5f, 12.5f)
        lineTo(9.5f, 17f)
        lineTo(19f, 7.5f)
    }
}.build()

/**
 * The name is displayed, not offered for editing, until the pencil is pressed - tapping the text
 * itself used to raise the keyboard on a field nobody meant to change.
 *
 * [focusProperties] is what actually prevents that: `readOnly` alone still lets a field take focus
 * and draw a caret. Once editing, the pencil becomes a tick, so there is always a way back out, and
 * the ripple gives the press somewhere to land.
 */
@Composable fun NameCard(saved: String, editing: Boolean, onEditing: (Boolean) -> Unit, onRename: (String, (Boolean) -> Unit) -> Unit) {
    // The draft stays saveable: LazyColumn keeps an item’s saveable state across disposal, so a
    // half-typed name survives scrolling even though the item itself does not. It is a
    // TextFieldValue rather than a String so the caret position is Kimi's to set, and survives too.
    var name by rememberSaveable(saved, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(saved)) }
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val drop = remember { Animatable(0f) }
    // Palette tokens are @Composable getters, so the ripple colour is read here rather than
    // inside the draw lambda, which runs outside composition.
    val ink = Accent
    LaunchedEffect(editing) { if (editing) focus.requestFocus() else keyboard?.hide() }
    // The tick always closes the field. Only a real change is saved: a blank or untouched name goes
    // back to the saved one, instead of leaving a dead button or announcing a rename to the same name.
    fun commit() {
        if (saving) return
        val typed = name.text.trim()
        if (typed.isNotEmpty() && typed != saved) {
            saving = true
            onRename(typed) { succeeded ->
                saving = false
                if (succeeded) onEditing(false)
            }
        } else {
            name = TextFieldValue(saved)
            onEditing(false)
        }
    }
    PlayCard(Paper) {
        Text(stringResource(R.string.settings_name_question), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Box {
            // Keep the water inside the field and behind its content. The inset prevents the rings
            // from painting over the outline, which previously made the effect look uncontained.
            Canvas(Modifier.matchParentSize().padding(2.dp).clip(RoundedCornerShape(13.dp))) {
                val t = drop.value
                if (t <= 0f || t >= 1f) return@Canvas
                val origin = Offset(size.width - 26.dp.toPx(), size.height / 2)
                val reach = size.width * .42f
                val fade = 1f - t
                drawCircle(
                    WaterCrest.copy(alpha = .14f * fade),
                    radius = 8.dp.toPx() + reach * t,
                    center = origin
                )
                repeat(3) { ring ->
                    val delay = ring * .12f
                    if (t <= delay) return@repeat
                    val progress = ((t - delay) / (1f - delay)).coerceIn(0f, 1f)
                    val alpha = .30f * (1f - progress) * (1f - progress)
                    drawCircle(
                        color = if (ring == 0) WaterShade.copy(alpha = alpha)
                            else WaterCrest.copy(alpha = alpha * .9f),
                        radius = 7.dp.toPx() + reach * progress,
                        center = origin,
                        style = Stroke(width = (1.8f - ring * .25f).dp.toPx())
                    )
                }
            }
            OutlinedTextField(name, { name = it.copy(text = it.text.take(30)) }, label = { Text(stringResource(R.string.settings_name_label)) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).focusProperties { canFocus = editing },
                singleLine = true, readOnly = !editing, shape = RoundedCornerShape(15.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                trailingIcon = {
                    IconButton(enabled = !saving, onClick = {
                        scope.launch { drop.snapTo(0f); drop.animateTo(1f,
                            tween(WaterRippleDurationMillis, easing = LinearEasing)) }
                        if (editing) commit() else {
                            // A field focused from code keeps its selection, which starts at 0, so the
                            // caret would sit before the first letter. Editing a name starts at its end.
                            name = name.copy(selection = TextRange(name.text.length))
                            onEditing(true)
                        }
                    }) {
                        Icon(if (editing) BoldCheck else Icons.Rounded.Edit,
                            stringResource(if (editing) R.string.cd_save_name else R.string.cd_edit_name),
                            tint = Accent)
                    }
                })
        }
    }
}

/** Version is here so a bug report can name a build; credits are an obligation, not decoration. */
@Composable fun AboutCard(onCredits: () -> Unit) {
    val context = LocalContext.current
    PlayCard(Paper) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BubbleIcon(Icons.Rounded.Info, Overlay); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_body), color = Quiet, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.about_version_label), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodyMedium, color = Quiet)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, PrivacyPolicyUrl.toUri())) }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp)) {
            Text(stringResource(R.string.action_privacy_policy))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCredits, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp)) {
            Text(stringResource(R.string.action_credits))
        }
    }
}

private const val PrivacyPolicyUrl = "https://github.com/vabxsen/Kimi/blob/main/docs/PRIVACY.md"

/**
 * Built like [WelcomeDialog] rather than as a page of its own: a rounded Cream sheet, the mascot,
 * and tinted [PlayCard]s, so Credits reads as part of Kimi instead of a settings screen borrowed
 * from somewhere else.
 *
 * License notices are bundled in res/raw and remain viewable offline from this dialog.
 */
@Composable fun CreditsDialog(onDismiss: () -> Unit) {
    var notices by rememberSaveable { mutableStateOf(false) }
    val author = stringResource(R.string.credits_author_name)
    val credit = stringResource(R.string.credits_author_line, author)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Cream)
                .verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Flower(Modifier.size(84.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.credits_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.credits_version, BuildConfig.VERSION_NAME), color = Quiet,
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(18.dp))
            PlayCard(Lavender) {
                Text(buildAnnotatedString {
                    val at = credit.indexOf(author)
                    append(credit)
                    if (at >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold), at, at + author.length)
                }, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.credits_made_in), color = Quiet,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(12.dp))
            PlayCard(Mint) {
                Text(stringResource(R.string.credits_open_source), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.credits_open_source_body), color = Quiet,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { notices = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp)) {
                Text(stringResource(R.string.action_open_source_licenses))
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.credits_copyright), color = Quiet, fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            MainButton(stringResource(R.string.action_close), onClick = onDismiss)
        }
    }
    if (notices) OpenSourceNoticesDialog { notices = false }
}

@Composable private fun OpenSourceNoticesDialog(onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val text = remember(resources) {
        resources.openRawResource(R.raw.open_source_notices).bufferedReader().use { it.readText() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.88f).clip(RoundedCornerShape(24.dp)).background(Cream).padding(20.dp)
        ) {
            Text(stringResource(R.string.open_source_licenses_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(text, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                color = Quiet, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            MainButton(stringResource(R.string.action_close), onClick = onDismiss)
        }
    }
}

/** Light, dark, or whatever the device is doing. Stored per device, never in a backup. */
@Composable fun AppearanceCard() {
    val context = LocalContext.current
    PlayCard(Blue) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BubbleIcon(Icons.Rounded.DarkMode, Overlay); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_appearance_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_appearance_body), color = Quiet, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        // Deliberately not horizontally scrollable: three short chips always fit, and a second
        // scroll container on this page would make the settings list ambiguous to scroll.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ThemeMode.System to R.string.settings_theme_system,
                ThemeMode.Light to R.string.settings_theme_light,
                ThemeMode.Dark to R.string.settings_theme_dark).forEach { (value, label) ->
                FilterChip(selected = ThemeSetting.mode == value, onClick = { ThemeSetting.set(context, value) },
                    label = { Text(stringResource(label), fontSize = 11.sp) }, shape = CircleShape,
                    modifier = Modifier.weight(1f).testTag("theme_${value.name.lowercase()}"))
            }
        }
    }
}

@Composable fun HabitEditor(existing: Habit?, onSave: (Habit) -> Unit, onDelete: () -> Unit, onClose: () -> Unit, busy: Boolean = false) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var goal by rememberSaveable(existing?.id) { mutableStateOf(existing?.goal.orEmpty()) }
    var color by rememberSaveable(existing?.id) { mutableIntStateOf(existing?.color ?: 0) }
    var symbol by rememberSaveable(existing?.id) { mutableIntStateOf(existing?.icon ?: 0) }
    var time by rememberSaveable(existing?.id) { mutableStateOf(existing?.time ?: "Morning") }
    var weekdays by rememberSaveable(existing?.id) { mutableStateOf(existing?.weekdays ?: false) }
    var reminderMinutes by rememberSaveable(existing?.id) { mutableStateOf(existing?.reminderMinutes) }
    var reminderCount by rememberSaveable(existing?.id) { mutableIntStateOf(existing?.reminderCount ?: 1) }
    val goalFocus = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(23.dp, 0.dp, 23.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(stringResource(if (existing == null) R.string.editor_title_new else R.string.editor_title_edit), style = MaterialTheme.typography.headlineMedium); Text(stringResource(R.string.editor_subtitle), color = Quiet, fontSize = 12.sp) }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.cd_close_editor)) }
        }
        // The preview card rises in as the sheet opens, then follows every colour and icon choice live.
        val tile by animateColorAsState(TileColors[color], label = "preview colour")
        val arrival = rememberGrow(1f, 520)
        Row(Modifier.fillMaxWidth().graphicsLayer {
                val a = arrival.value
                alpha = a
                scaleX = .9f + .1f * easeOutBack(a); scaleY = scaleX
                translationY = (1f - a) * 24.dp.toPx()
            }.clip(RoundedCornerShape(20.dp)).background(tile).padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(symbol, transitionSpec = {
                (scaleIn(spring(dampingRatio = .5f, stiffness = Spring.StiffnessMedium)) + fadeIn()) togetherWith (scaleOut() + fadeOut())
            }, label = "preview icon") { BubbleIcon(HabitSymbols[it], Overlay) }
            Spacer(Modifier.width(13.dp))
            Column { Text(name.ifBlank { stringResource(R.string.editor_preview_name) }, style = MaterialTheme.typography.titleMedium); Text(goal.ifBlank { stringResource(R.string.editor_preview_goal) }, fontSize = 11.sp, color = Quiet) }
        }
        OutlinedTextField(name, { name = it.take(70) }, label = { Text(stringResource(R.string.editor_name_label)) }, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { goalFocus.requestFocus() }))
        OutlinedTextField(goal, { goal = it.take(80) }, label = { Text(stringResource(R.string.editor_goal_label)) },
            placeholder = { Text(stringResource(R.string.editor_goal_placeholder)) }, modifier = Modifier.fillMaxWidth().focusRequester(goalFocus),
            shape = RoundedCornerShape(16.dp), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }))
        Text(stringResource(R.string.editor_icon_section), style = MaterialTheme.typography.titleMedium)
        val iconDescriptions = stringArrayResource(R.array.habit_icon_names)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HabitSymbols.forEachIndexed { i, icon ->
                val chip by animateColorAsState(if (symbol == i) Purple else Paper, label = "icon chip")
                val glyph by animateColorAsState(if (symbol == i) OnAccent else Ink, label = "icon glyph")
                IconButton(onClick = { symbol = i }, modifier = Modifier.size(48.dp)
                    .semantics { contentDescription = iconDescriptions[i]; selected = symbol == i }
                    .clip(RoundedCornerShape(15.dp)).background(chip)) {
                    HopIcon(icon, symbol == i, tint = glyph)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            TileColors.forEachIndexed { i, c ->
                val swatch = stringResource(R.string.cd_color, i + 1)
                IconButton(onClick = { color = i }, modifier = Modifier.weight(1f).height(48.dp).clip(CircleShape).background(c)) {
                    if (color == i) Icon(Icons.Rounded.Check, stringResource(R.string.cd_color_selected, i + 1)) else Box(Modifier.semantics { contentDescription = swatch }.size(24.dp))
                }
            }
        }
        Text(stringResource(R.string.editor_schedule_section), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FilterChip(!weekdays, { weekdays = false }, label = { Text(stringResource(R.string.schedule_every_day)) }, shape = CircleShape)
            FilterChip(weekdays, { weekdays = true }, label = { Text(stringResource(R.string.schedule_weekdays)) }, shape = CircleShape)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dayparts.forEach { t -> FilterChip(time == t, { time = t }, label = { Text(daypartLabel(t), fontSize = 11.sp) }, shape = CircleShape) }
        }
        ReminderPicker(reminderMinutes, reminderCount) { nextMinutes, nextCount ->
            reminderMinutes = nextMinutes
            reminderCount = nextCount
        }
        MainButton(stringResource(if (existing == null) R.string.action_create_habit else R.string.action_save_changes), enabled = !busy && name.isNotBlank() && goal.isNotBlank()) {
            onSave((existing ?: Habit(name = name, goal = goal)).copy(
                name = name.trim(), goal = goal.trim(), color = color, icon = symbol, time = time,
                weekdays = weekdays, reminderMinutes = reminderMinutes, reminderCount = reminderCount
            ))
        }
        if (existing != null) TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_delete_habit_full), color = Danger) }
    }
}
