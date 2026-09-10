package com.forma.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val ShortDate = DateTimeFormatter.ofPattern("EEE, MMM d")
private val MonthLabel = DateTimeFormatter.ofPattern("MMMM yyyy")

/** Habit dayparts are stored as stable English keys; only the label on screen is translated. */
@Composable fun daypartLabel(key: String): String = when (key) {
    "Morning" -> stringResource(R.string.daypart_morning)
    "Afternoon" -> stringResource(R.string.daypart_afternoon)
    "Evening" -> stringResource(R.string.daypart_evening)
    else -> stringResource(R.string.daypart_anytime)
}

@Composable fun TodayScreen(state: HabitState, onToggle: (Habit, LocalDate) -> Unit, onNew: () -> Unit, onJournal: () -> Unit) {
    val today = LocalToday.current
    val stats = rememberHabitStats(state, today)
    var dateString by rememberSaveable(today) { mutableStateOf(today.toString()) }
    val date = LocalDate.parse(dateString)
    var filter by rememberSaveable { mutableStateOf("All") }
    LazyColumn(contentPadding = PaddingValues(23.dp, 10.dp, 23.dp, 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(ShortDate).uppercase(), fontSize = 10.sp, color = Quiet, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp)); Text(stringResource(R.string.today_greeting), style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(5.dp)); Text(stringResource(R.string.today_subtitle, state.name), color = Quiet, fontSize = 12.sp)
                }
            }
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp)).background(Lavender).padding(18.dp)) {
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
                    if (expandedText) Flower(Modifier.align(Alignment.End).size(86.dp))
                }
                if (!expandedText) Flower(Modifier.align(Alignment.CenterEnd).size(111.dp).offset(x = 9.dp))
            }
        }
        item {
            BoxWithConstraints {
                val restDay = state.isRestDay(date)
                val streakValue = pluralStringResource(R.plurals.streak_days, stats.bestStreak, stats.bestStreak)
                val streakCaption = stringResource(R.string.today_streak_caption)
                val progressValue = if (restDay) stringResource(R.string.today_rest_day) else stringResource(R.string.today_percent_complete, state.percent(date))
                val progressCaption = if (restDay) stringResource(R.string.today_rest_caption) else stringResource(R.string.today_percent_caption)
                val stackStats = maxWidth < 344.dp || LocalDensity.current.fontScale > 1.2f
                if (stackStats) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WideStat(Peach, Icons.Rounded.LocalFireDepartment, streakValue, streakCaption)
                    WideStat(Mint, Icons.Rounded.Stars, progressValue, progressCaption)
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Peach).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocalFireDepartment, null, tint = Ink, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(10.dp))
                        Column { Text(streakValue, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(streakCaption, fontSize = 10.sp, color = Quiet) }
                    }
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Mint).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Stars, null, tint = Ink, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(10.dp))
                        Column { Text(progressValue, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(progressCaption, fontSize = 10.sp, color = Quiet) }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                val start = today.minusDays((today.dayOfWeek.value - 1).toLong())
                repeat(7) { index ->
                    val d = start.plusDays(index.toLong()); val selected = d == date
                    val dayLabel = stringResource(if (selected) R.string.cd_day_selected else R.string.cd_day, d.format(ShortDate))
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(17.dp)).background(if (selected) Purple else Paper)
                        .clickable(enabled = !d.isAfter(today), role = Role.Button) { dateString = d.toString() }
                        .semantics { contentDescription = dayLabel }
                        .padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.dayOfWeek.name.take(1), fontSize = 10.sp, color = if (selected) OnAccent else Quiet)
                        Spacer(Modifier.height(7.dp)); Text(d.dayOfMonth.toString(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (selected) OnAccent else if (d.isAfter(today)) Quiet.copy(alpha = .78f) else Ink)
                        Spacer(Modifier.height(7.dp)); Box(Modifier.size(4.dp).background(if (selected) Highlight else Lavender, CircleShape))
                    }
                }
            }
        }
        item {
            SectionTitle(if (date == today) stringResource(R.string.today_section) else date.format(ShortDate), stringResource(R.string.action_new_habit), onNew)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                (listOf("All") + Dayparts).forEach { item ->
                    val label = if (item == "All") stringResource(R.string.filter_all) else daypartLabel(item)
                    FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(label, fontSize = 11.sp) }, shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink, selectedLabelColor = OnInk), border = null)
                }
            }
        }
        val habits = state.due(date).filter { filter == "All" || it.time == filter }
        items(habits, key = { it.id }) { habit -> HabitRow(habit, state, date, { onToggle(habit, date) }) }
        if (habits.isEmpty()) item { EmptySpace(stringResource(R.string.today_empty_title), stringResource(R.string.today_empty_body)) }
        item {
            PlayCard(Yellow) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BubbleIcon(Icons.Rounded.EditNote, Overlay); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(stringResource(R.string.journal_prompt), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.today_journal_caption), fontSize = 11.sp, color = Quiet) }
                    IconButton(onClick = onJournal) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.cd_open_journal)) }
                }
            }
        }
        if (state.demo) item { Text(stringResource(R.string.demo_footer_today), color = Quiet, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
    }
}

@Composable fun HabitRow(habit: Habit, state: HabitState, date: LocalDate, onToggle: () -> Unit) {
    val today = LocalToday.current
    val checked = state.done(habit.id, date)
    val haptics = LocalHapticFeedback.current
    val background by animateColorAsState(TileColors[habit.color.coerceIn(0, 5)], label = "habit color")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(background).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        BubbleIcon(HabitSymbols[habit.icon.coerceIn(0, 7)], Overlay, size = 44)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(habit.name, style = MaterialTheme.typography.titleMedium, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp)); Text(habit.goal, fontSize = 10.sp, color = Quiet)
        }
        val toggleLabel = stringResource(if (checked) R.string.cd_undo_habit else R.string.cd_complete_habit, habit.name)
        IconToggleButton(checked = checked, enabled = !date.isAfter(today), onCheckedChange = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onToggle() },
            modifier = Modifier.size(48.dp).semantics { contentDescription = toggleLabel }) {
            Box(Modifier.size(29.dp).clip(CircleShape).background(if (checked) Ink else Overlay)
                .border(1.5.dp, if (checked) Ink else Quiet, CircleShape), contentAlignment = Alignment.Center) {
                if (checked) Icon(Icons.Rounded.Check, null, Modifier.size(19.dp), tint = OnInk)
            }
        }
    }
}

@Composable fun HabitsScreen(state: HabitState, onNew: () -> Unit, onEdit: (Habit) -> Unit) {
    val today = LocalToday.current
    val stats = rememberHabitStats(state, today)
    var query by rememberSaveable { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(23.dp, 17.dp, 23.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle(stringResource(R.string.habits_title), stringResource(R.string.habits_subtitle)) }
        item {
            OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.habits_search_label)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), singleLine = true)
        }
        item { Text(pluralStringResource(R.plurals.habits_collection_count, state.habits.size, state.habits.size), style = MaterialTheme.typography.titleLarge) }
        items(state.habits.filter { it.name.contains(query, true) }, key = { it.id }) { h ->
            PlayCard(TileColors[h.color.coerceIn(0, 5)]) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BubbleIcon(HabitSymbols[h.icon.coerceIn(0, 7)], Overlay, size = 51)
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(h.name, style = MaterialTheme.typography.titleMedium); Text(h.goal, color = Quiet, fontSize = 11.sp) }
                    IconButton(onClick = { onEdit(h) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.cd_edit_habit, h.name), Modifier.size(21.dp)) }
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
                            Text(d.dayOfWeek.name.take(1), fontSize = 10.sp, color = Quiet)
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton({ monthString = month.minusMonths(1).toString() }, enabled = month.year > 1970 || month.monthValue > 1) { Icon(Icons.Rounded.ChevronLeft, stringResource(R.string.cd_prev_month)) }
                    Text(month.format(MonthLabel), style = MaterialTheme.typography.titleMedium)
                    IconButton({ monthString = month.plusMonths(1).toString() }, enabled = month.year < 2200 || month.monthValue < 12) { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.cd_next_month)) }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) { weekdayInitials().forEach { Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Quiet, fontSize = 10.sp) } }
                Spacer(Modifier.height(12.dp))
                val offset = month.atDay(1).dayOfWeek.value - 1
                val cells = (offset + month.lengthOfMonth() + 6) / 7 * 7
                (0 until cells).chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        week.forEach { n ->
                            val day = n - offset + 1
                            if (day !in 1..month.lengthOfMonth()) Box(Modifier.weight(1f).height(44.dp))
                            else {
                                val d = month.atDay(day); val pct = state.percent(d); val selected = d == date
                                val rest = state.isRestDay(d)
                                val dayLabel = if (rest) stringResource(R.string.cd_calendar_day_rest, d.format(ShortDate))
                                    else pluralStringResource(R.plurals.cd_calendar_day, state.count(d), d.format(ShortDate), state.count(d))
                                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(13.dp))
                                    // A rest day stays unfilled so it never reads as a day you missed.
                                    .background(if (selected) Purple else if (rest) Color.Transparent else if (pct == 100) Mint else if (pct > 0) Lavender else Cream)
                                    .clickable(role = Role.Button) { dateString = d.toString() }
                                    .semantics { contentDescription = dayLabel }, contentAlignment = Alignment.Center) {
                                    Text(day.toString(), fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                        color = if (selected) OnAccent else if (rest) Quiet.copy(alpha = .72f) else Ink)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
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
        item { SectionTitle(date.format(ShortDate), stringResource(R.string.action_today)) { monthString = YearMonth.from(today).toString(); dateString = today.toString() } }
        items(state.due(date), key = { it.id }) { HabitRow(it, state, date) { onToggle(it, date) } }
        if (state.due(date).isEmpty()) item { EmptySpace(stringResource(R.string.calendar_empty_title), stringResource(R.string.calendar_empty_body)) }
    }
}

@Composable fun InsightsScreen(state: HabitState) {
    val today = LocalToday.current
    val enlargedText = LocalDensity.current.fontScale > 1.2f
    val stats = rememberHabitStats(state, today)
    val summary = rememberInsights(state, today)
    val count = summary.wins
    val percent = summary.percent
    LazyColumn(contentPadding = PaddingValues(23.dp, 17.dp, 23.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageTitle(stringResource(R.string.insights_title), stringResource(R.string.insights_subtitle)) }
        item {
            PlayCard(Purple) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.insights_energy_label), color = OnAccent, fontSize = 10.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(10.dp)); Text(stringResource(R.string.percent_value, percent), color = Highlight, fontWeight = FontWeight.ExtraBold, fontSize = 49.sp)
                        Text(stringResource(R.string.insights_consistency), color = OnAccent, fontSize = 12.sp)
                    }
                    Flower(Modifier.size(119.dp), petal = Petal)
                }
            }
        }
        item {
            Row(Modifier.then(if (enlargedText) Modifier.height(IntrinsicSize.Min) else Modifier), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayCard(Yellow, Modifier.weight(1f).then(if (enlargedText) Modifier.fillMaxHeight() else Modifier)) { Icon(Icons.Rounded.Bolt, null); Spacer(Modifier.height(9.dp)); Text("$count", style = MaterialTheme.typography.headlineMedium); Text(stringResource(R.string.insights_wins), fontSize = 11.sp, color = Quiet) }
                PlayCard(Pink, Modifier.weight(1f).then(if (enlargedText) Modifier.fillMaxHeight() else Modifier)) { Icon(Icons.Rounded.LocalFireDepartment, null); Spacer(Modifier.height(9.dp)); Text("${stats.bestStreak}", style = MaterialTheme.typography.headlineMedium); Text(stringResource(R.string.insights_best_streak), fontSize = 11.sp, color = Quiet) }
            }
        }
        item {
            PlayCard(Paper) {
                Text(stringResource(R.string.insights_week_chart), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth().then(if (enlargedText) Modifier.heightIn(min = 165.dp) else Modifier.height(165.dp)), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Bottom) {
                    summary.week.forEachIndexed { i, day ->
                        val barLabel = if (day.rest) stringResource(R.string.cd_chart_bar_rest, day.date.format(ShortDate))
                            else pluralStringResource(R.plurals.cd_chart_bar, day.percent, day.date.format(ShortDate), day.percent)
                        Column(Modifier.weight(1f).semantics { contentDescription = barLabel }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            // A rest day shows a dash and a flat marker; charting it as 0% would
                            // make a perfect week look like it collapsed every weekend.
                            Text(if (day.rest) stringResource(R.string.rest_marker) else stringResource(R.string.percent_value, day.percent),
                                fontSize = 9.sp, color = Quiet); Spacer(Modifier.height(7.dp))
                            Box(Modifier.fillMaxWidth().height(if (day.rest) 4.dp else (day.percent * 1.05f + 4).dp).clip(RoundedCornerShape(11.dp))
                                .background(if (day.rest) Quiet.copy(alpha = .25f) else if (i == 6) Purple else TileColors[i % 6]))
                            Spacer(Modifier.height(9.dp)); Text(day.date.dayOfWeek.name.take(1), fontSize = 10.sp, color = Quiet)
                        }
                    }
                }
            }
        }
        item { SectionTitle(stringResource(R.string.insights_habits_section)) }
        items(state.habits, key = { it.id }) { habit ->
            val pct = summary.consistency[habit.id] ?: 0
            PlayCard(TileColors[habit.color.coerceIn(0, 5)]) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(HabitSymbols[habit.icon.coerceIn(0, 7)], null, Modifier.size(24.dp)); Spacer(Modifier.width(10.dp))
                    Text(habit.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontSize = 13.sp)
                    Text(stringResource(R.string.percent_value, pct), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Ink, trackColor = Overlay, drawStopIndicator = {})
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

@Composable private fun WideStat(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, caption: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Ink, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(caption, fontSize = 10.sp, color = Quiet)
        }
    }
}

private val MoodIcons = listOf(Icons.Rounded.SentimentVeryDissatisfied, Icons.Rounded.SentimentNeutral,
    Icons.Rounded.SentimentSatisfied, Icons.Rounded.SentimentVerySatisfied, Icons.Rounded.Celebration)
@Composable private fun moodNames(): Array<String> = stringArrayResource(R.array.mood_names)

/** Locale-correct single-letter column headings, Monday first, matching the grid layout. */
@Composable private fun weekdayInitials(): List<String> {
    val locale = LocalConfiguration.current.locales[0]
    return (0..6).map { java.time.DayOfWeek.of(it + 1).getDisplayName(java.time.format.TextStyle.NARROW, locale) }
}

@Composable fun JournalScreen(state: HabitState, onSave: (Int, String) -> Unit, onDelete: (LocalDate) -> Unit,
    mood: Int, text: String, onDraft: (Int, String) -> Unit, draftDate: LocalDate,
    onEditDate: (LocalDate) -> Unit, draftDates: List<LocalDate>) {
    val today = LocalToday.current
    var deleteDate by remember { mutableStateOf<LocalDate?>(null) }
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun editDate(date: LocalDate) { onEditDate(date); scope.launch { scroll.animateScrollToItem(0) } }
    LazyColumn(state = scroll, contentPadding = PaddingValues(23.dp, 17.dp, 23.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageTitle(stringResource(R.string.journal_title), stringResource(R.string.journal_subtitle)) }
        if (draftDate != today || draftDates.isNotEmpty()) item {
            Column {
                if (draftDate != today) { Text(stringResource(R.string.journal_writing_for, draftDate.format(ShortDate)), color = Accent); TextButton(onClick = { editDate(today) }) { Text(stringResource(R.string.journal_back_to_today)) } }
                draftDates.filter { it != draftDate }.forEach { date -> TextButton(onClick = { editDate(date) }) { Text(stringResource(R.string.journal_continue_draft, date.format(ShortDate))) } }
            }
        }
        item {
            PlayCard(Pink) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(stringResource(R.string.journal_moods_title), style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.journal_moods_body), fontSize = 12.sp, color = Quiet) }
                    Flower(Modifier.size(100.dp), happy = mood > 1, petal = Petal)
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.journal_prompt))
            Spacer(Modifier.height(15.dp))
            val names = moodNames()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MoodIcons.forEachIndexed { index, icon ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (index == mood) Yellow else Paper)
                        .semantics { selected = index == mood }.clickable(role = Role.RadioButton) { onDraft(index, text) }.padding(vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, names[index], Modifier.size(28.dp)); Spacer(Modifier.height(8.dp)); Text(names[index], fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            OutlinedTextField(text, { onDraft(mood, it.take(10000)) }, label = { Text(stringResource(R.string.journal_field_label)) }, placeholder = { Text(stringResource(R.string.journal_field_placeholder)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp), shape = RoundedCornerShape(22.dp), minLines = 5)
            Spacer(Modifier.height(14.dp)); MainButton(stringResource(R.string.action_save_reflection), enabled = text.isNotBlank()) { onSave(mood, text) }
            Spacer(Modifier.height(10.dp)); Text(stringResource(R.string.journal_privacy), color = Quiet, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        item { SectionTitle(stringResource(R.string.journal_history_section)) }
        items(state.journal.sortedByDescending { it.date }, key = { it.date.toString() }) { entry ->
            PlayCard(Paper) {
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

@Composable fun SettingsScreen(state: HabitState, onRename: (String) -> Unit, onReset: () -> Unit, export: () -> Unit, restore: () -> Unit, damaged: Boolean, onSaveHabit: (Habit, () -> Unit) -> Unit, account: KimiAccount? = null, onAccount: () -> Unit = {}) {
    var name by rememberSaveable(state.name) { mutableStateOf(state.name) }
    var confirm by remember { mutableStateOf(false) }
    var credits by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(23.dp, 17.dp, 23.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageTitle(stringResource(R.string.settings_title), stringResource(R.string.settings_subtitle)) }
        item {
            PlayCard(Lavender) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Flower(Modifier.size(85.dp)); Spacer(Modifier.width(17.dp))
                    Column(Modifier.weight(1f)) { Text(stringResource(R.string.settings_team, state.name), style = MaterialTheme.typography.headlineMedium); Text(stringResource(R.string.settings_team_caption), color = Quiet, fontSize = 12.sp) }
                }
            }
        }
        item {
            PlayCard(Paper) {
                Text(stringResource(R.string.settings_name_question), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp)); OutlinedTextField(name, { name = it.take(30) }, label = { Text(stringResource(R.string.settings_name_label)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(15.dp))
                Spacer(Modifier.height(14.dp)); MainButton(stringResource(R.string.action_thats_me), enabled = name.isNotBlank()) { onRename(name) }
            }
        }
        item { AccountCard(account, onAccount) }
        item { AppearanceCard() }
        item {
            PlayCard(Paper) {
                SectionTitle(stringResource(R.string.settings_data_section))
                Text(stringResource(R.string.settings_data_caption), fontSize = 12.sp, color = Quiet)
                if (damaged) Text(stringResource(R.string.settings_damaged_notice), color = Accent)
                Spacer(Modifier.height(14.dp)); OutlinedButton(onClick = export, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Rounded.IosShare, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_export)) }
                OutlinedButton(onClick = restore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_restore)) }
                TextButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.action_reset), color = Danger) }
            }
        }
        item { NotificationSettings(state.habits, onSaveHabit) }
        if (state.demo) item { Text(stringResource(R.string.demo_footer_settings), color = Quiet, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        item { AboutCard { credits = true } }
    }
    if (credits) CreditsDialog { credits = false }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(stringResource(R.string.dialog_reset_title)) }, text = { Text(stringResource(R.string.dialog_reset_body)) },
        confirmButton = { TextButton(onClick = { onReset(); confirm = false }) { Text(stringResource(R.string.action_reset_confirm)) } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_keep_progress)) } })
}

/** Version is here so a bug report can name a build; credits are an obligation, not decoration. */
@Composable fun AboutCard(onCredits: () -> Unit) {
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
        OutlinedButton(onClick = onCredits, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp)) {
            Text(stringResource(R.string.action_credits))
        }
    }
}

/**
 * Kimi bundles Nunito, whose SIL Open Font License requires the licence and copyright to travel
 * with the font. The repository carries `Nunito-OFL.txt`, but nothing shipped inside the APK said
 * so until this screen existed.
 */
@Composable fun CreditsDialog(onDismiss: () -> Unit) {
    val entries = listOf(
        R.string.credits_kimi_name to R.string.credits_kimi_detail,
        R.string.credits_nunito_name to R.string.credits_nunito_detail,
        R.string.credits_icons_name to R.string.credits_icons_detail,
        R.string.credits_androidx_name to R.string.credits_androidx_detail,
        R.string.credits_firebase_name to R.string.credits_firebase_detail
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Cream).padding(24.dp)) {
            Text(stringResource(R.string.credits_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.credits_intro), color = Quiet, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                entries.forEach { (name, detail) ->
                    Column {
                        Text(stringResource(name), style = MaterialTheme.typography.titleMedium, fontSize = 14.sp)
                        Text(stringResource(detail), color = Quiet, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
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
                    modifier = Modifier.weight(1f))
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
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(23.dp, 0.dp, 23.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(stringResource(if (existing == null) R.string.editor_title_new else R.string.editor_title_edit), style = MaterialTheme.typography.headlineMedium); Text(stringResource(R.string.editor_subtitle), color = Quiet, fontSize = 12.sp) }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.cd_close_editor)) }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TileColors[color]).padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            BubbleIcon(HabitSymbols[symbol], Overlay); Spacer(Modifier.width(13.dp))
            Column { Text(name.ifBlank { stringResource(R.string.editor_preview_name) }, style = MaterialTheme.typography.titleMedium); Text(goal.ifBlank { stringResource(R.string.editor_preview_goal) }, fontSize = 11.sp, color = Quiet) }
        }
        OutlinedTextField(name, { name = it.take(70) }, label = { Text(stringResource(R.string.editor_name_label)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
        OutlinedTextField(goal, { goal = it.take(80) }, label = { Text(stringResource(R.string.editor_goal_label)) }, placeholder = { Text(stringResource(R.string.editor_goal_placeholder)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
        Text(stringResource(R.string.editor_icon_section), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HabitSymbols.forEachIndexed { i, icon -> IconButton(onClick = { symbol = i }, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(if (symbol == i) Purple else Paper)) { Icon(icon, stringResource(R.string.cd_habit_icon, i + 1), tint = if (symbol == i) OnAccent else Ink) } }
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
        MainButton(stringResource(if (existing == null) R.string.action_create_habit else R.string.action_save_changes), enabled = !busy && name.isNotBlank() && goal.isNotBlank()) {
            onSave((existing ?: Habit(name = name, goal = goal)).copy(name = name.trim(), goal = goal.trim(), color = color, icon = symbol, time = time, weekdays = weekdays))
        }
        if (existing != null) TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_delete_habit_full), color = Danger) }
    }
}
