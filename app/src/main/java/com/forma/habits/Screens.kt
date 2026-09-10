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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val ShortDate = DateTimeFormatter.ofPattern("EEE, MMM d")
private val MonthLabel = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable fun TodayScreen(state: HabitState, onToggle: (Habit, LocalDate) -> Unit, onNew: () -> Unit, onJournal: () -> Unit) {
    val today = LocalToday.current
    val stackStats = LocalConfiguration.current.screenWidthDp < 390 || LocalDensity.current.fontScale > 1.2f
    var dateString by rememberSaveable(today) { mutableStateOf(today.toString()) }
    val date = LocalDate.parse(dateString)
    var filter by rememberSaveable { mutableStateOf("All") }
    LazyColumn(contentPadding = PaddingValues(23.dp, 10.dp, 23.dp, 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(today.format(ShortDate).uppercase(), fontSize = 10.sp, color = Quiet, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp)); Text("Hey, sunshine!", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(5.dp)); Text("Let’s grow a little today, ${state.name}.", color = Quiet, fontSize = 12.sp)
                }
            }
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp)).background(Lavender).padding(18.dp)) {
                val expandedText = maxWidth < 300.dp || LocalDensity.current.fontScale > 1.2f
                Column(Modifier.fillMaxWidth(if (expandedText) 1f else .64f)) {
                    Pill("YOUR DAILY DOSE OF GOOD", Color.White.copy(alpha = .72f))
                    Spacer(Modifier.height(9.dp))
                    Text("Small steps.\nBig happy energy.", style = MaterialTheme.typography.titleLarge, fontSize = 23.sp, lineHeight = 28.sp)
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Purple, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp))
                        Text("${state.count(date)} of ${state.due(date).size} done. You’ve got this!", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    if (expandedText) Flower(Modifier.align(Alignment.End).size(86.dp))
                }
                if (!expandedText) Flower(Modifier.align(Alignment.CenterEnd).size(111.dp).offset(x = 9.dp))
            }
        }
        item {
            if (stackStats) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WideStat(Peach, Icons.Rounded.LocalFireDepartment, "${state.habits.maxOfOrNull { state.streak(it) } ?: 0} day streak", "Keep your spark!")
                WideStat(Mint, Icons.Rounded.Stars, "${state.percent(date)}% complete", "Every little bit counts")
            } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Peach).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalFireDepartment, null, tint = Ink, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(10.dp))
                    Column { Text("${state.habits.maxOfOrNull { state.streak(it) } ?: 0} day streak", fontSize = 14.sp, fontWeight = FontWeight.Bold); Text("Keep your spark!", fontSize = 10.sp, color = Quiet) }
                }
                Row(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Mint).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Stars, null, tint = Ink, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(10.dp))
                    Column { Text("${state.percent(date)}% complete", fontSize = 14.sp, fontWeight = FontWeight.Bold); Text("Every little bit counts", fontSize = 10.sp, color = Quiet) }
                }
            }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                val start = today.minusDays((today.dayOfWeek.value - 1).toLong())
                repeat(7) { index ->
                    val d = start.plusDays(index.toLong()); val selected = d == date
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(17.dp)).background(if (selected) Purple else Color.White)
                        .clickable(enabled = !d.isAfter(today), role = Role.Button) { dateString = d.toString() }
                        .semantics { contentDescription = "${d.format(ShortDate)}${if (selected) ", selected" else ""}" }
                        .padding(vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.dayOfWeek.name.take(1), fontSize = 10.sp, color = if (selected) Color.White else Quiet)
                        Spacer(Modifier.height(7.dp)); Text(d.dayOfMonth.toString(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else if (d.isAfter(today)) Quiet.copy(alpha = .65f) else Ink)
                        Spacer(Modifier.height(7.dp)); Box(Modifier.size(4.dp).background(if (selected) Yellow else Lavender, CircleShape))
                    }
                }
            }
        }
        item {
            SectionTitle(if (date == today) "Your little rituals" else date.format(ShortDate), "+ New habit", onNew)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("All", "Morning", "Afternoon", "Evening", "Anytime").forEach { item ->
                    FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item, fontSize = 11.sp) }, shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Ink, selectedLabelColor = Color.White), border = null)
                }
            }
        }
        val habits = state.due(date).filter { filter == "All" || it.time == filter }
        items(habits, key = { it.id }) { habit -> HabitRow(habit, state, date, { onToggle(habit, date) }) }
        if (habits.isEmpty()) item { EmptySpace("Room for a new beginning", "Add a habit or try another time of day.") }
        item {
            PlayCard(Yellow) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BubbleIcon(Icons.Rounded.EditNote, Color.White.copy(alpha = .65f)); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text("How’s your heart today?", style = MaterialTheme.typography.titleMedium); Text("Make a little space for your thoughts.", fontSize = 11.sp, color = Quiet) }
                    IconButton(onClick = onJournal) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Open journal") }
                }
            }
        }
        if (state.demo) item { Text("Sample habits & check-ins · Make them your own", color = Quiet, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
    }
}

@Composable fun HabitRow(habit: Habit, state: HabitState, date: LocalDate, onToggle: () -> Unit) {
    val today = LocalToday.current
    val checked = state.done(habit.id, date)
    val haptics = LocalHapticFeedback.current
    val background by animateColorAsState(TileColors[habit.color.coerceIn(0, 5)], label = "habit color")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(background).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        BubbleIcon(HabitSymbols[habit.icon.coerceIn(0, 7)], Color.White.copy(alpha = .65f), size = 44)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(habit.name, style = MaterialTheme.typography.titleMedium, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp)); Text(habit.goal, fontSize = 10.sp, color = Quiet)
        }
        IconToggleButton(checked = checked, enabled = !date.isAfter(today), onCheckedChange = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onToggle() },
            modifier = Modifier.size(48.dp).semantics { contentDescription = "${if (checked) "Undo" else "Complete"} ${habit.name}" }) {
            Box(Modifier.size(29.dp).clip(CircleShape).background(if (checked) Ink else Color.White.copy(alpha = .7f))
                .border(1.5.dp, if (checked) Ink else Quiet, CircleShape), contentAlignment = Alignment.Center) {
                if (checked) Icon(Icons.Rounded.Check, null, Modifier.size(19.dp), tint = Color.White)
            }
        }
    }
}

@Composable fun HabitsScreen(state: HabitState, onNew: () -> Unit, onEdit: (Habit) -> Unit) {
    val today = LocalToday.current
    var query by rememberSaveable { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(23.dp, 17.dp, 23.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Good habits,\ngreat you.", "Your own little collection of feel-good rituals.") }
        item {
            OutlinedTextField(query, { query = it }, label = { Text("Find your habit") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), singleLine = true)
        }
        item { Text("Your collection · ${state.habits.size} habits", style = MaterialTheme.typography.titleLarge) }
        items(state.habits.filter { it.name.contains(query, true) }, key = { it.id }) { h ->
            PlayCard(TileColors[h.color.coerceIn(0, 5)]) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BubbleIcon(HabitSymbols[h.icon.coerceIn(0, 7)], Color.White.copy(alpha = .7f), size = 51)
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(h.name, style = MaterialTheme.typography.titleMedium); Text(h.goal, color = Quiet, fontSize = 11.sp) }
                    IconButton(onClick = { onEdit(h) }) { Icon(Icons.Rounded.Edit, "Edit ${h.name}", Modifier.size(21.dp)) }
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(7) { i ->
                        val d = today.minusDays((6 - i).toLong()); val done = state.done(h.id, d)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(d.dayOfWeek.name.take(1), fontSize = 10.sp, color = Quiet)
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(if (done) Ink else Color.White.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
                                if (done) Icon(Icons.Rounded.Check, null, Modifier.size(17.dp), tint = Color.White) else Text("·", color = Quiet)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(17.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Pill("${state.streak(h)} day streak", Color.White.copy(alpha = .55f), Icons.Rounded.LocalFireDepartment)
                    Text("${if (h.weekdays) "Weekdays" else "Every day"} · ${h.time}", fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        }
        if (state.habits.isNotEmpty() && state.habits.none { it.name.contains(query, true) }) item { EmptySpace("No little rituals found", "Try a different name or plant a new habit.") }
        if (state.habits.isEmpty()) item { EmptySpace("Let’s plant your first habit", "Something small is a great place to start.") }
        item { MainButton("Plant a new habit", onClick = onNew) }
    }
}

@Composable fun CalendarScreen(state: HabitState, onToggle: (Habit, LocalDate) -> Unit) {
    val today = LocalToday.current
    var monthString by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var dateString by rememberSaveable { mutableStateOf(today.toString()) }
    val month = YearMonth.parse(monthString); val date = LocalDate.parse(dateString)
    LazyColumn(contentPadding = PaddingValues(23.dp, 17.dp, 23.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(19.dp)) {
        item { PageTitle("Look at you grow.", "A whole lot of little wins, one day at a time.") }
        item {
            PlayCard(Color.White) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton({ monthString = month.minusMonths(1).toString() }, enabled = month.year > 1970 || month.monthValue > 1) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
                    Text(month.format(MonthLabel), style = MaterialTheme.typography.titleMedium)
                    IconButton({ monthString = month.plusMonths(1).toString() }, enabled = month.year < 2200 || month.monthValue < 12) { Icon(Icons.Rounded.ChevronRight, "Next month") }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Quiet, fontSize = 10.sp) } }
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
                                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(13.dp))
                                    .background(if (selected) Purple else if (pct == 100) Mint else if (pct > 0) Lavender else Cream)
                                    .clickable(role = Role.Button) { dateString = d.toString() }
                                    .semantics { contentDescription = "${d.format(ShortDate)}, ${state.count(d)} habits complete" }, contentAlignment = Alignment.Center) {
                                    Text(day.toString(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (selected) Color.White else Ink)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Lavender, CircleShape)); Text("  Some wins    ", fontSize = 10.sp, color = Quiet)
                    Box(Modifier.size(8.dp).background(Mint, CircleShape)); Text("  All done!", fontSize = 10.sp, color = Quiet)
                }
            }
        }
        item {
            PlayCard(Peach) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Flower(Modifier.size(65.dp)); Spacer(Modifier.width(14.dp))
                    Column { Text("Showing up is a superpower.", style = MaterialTheme.typography.titleMedium); Text("Missed a day? The next one is all yours.", fontSize = 11.sp, color = Quiet) }
                }
            }
        }
        item { SectionTitle(date.format(ShortDate), "Today") { monthString = YearMonth.from(today).toString(); dateString = today.toString() } }
        items(state.due(date), key = { it.id }) { HabitRow(it, state, date) { onToggle(it, date) } }
        if (state.due(date).isEmpty()) item { EmptySpace("A quiet day", "No habits scheduled for this date.") }
    }
}

@Composable fun InsightsScreen(state: HabitState) {
    val today = LocalToday.current
    val enlargedText = LocalDensity.current.fontScale > 1.2f
    val dates = (6L downTo 0L).map { today.minusDays(it) }
    val possible = dates.sumOf { state.due(it).size }
    val count = dates.sumOf { state.count(it) }
    val percent = if (possible == 0) 0 else count * 100 / possible
    LazyColumn(contentPadding = PaddingValues(23.dp, 17.dp, 23.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageTitle("You’re on a roll!", "Proof that your little efforts are adding up.") }
        item {
            PlayCard(Purple) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("THIS WEEK’S GOOD ENERGY", color = Color.White, fontSize = 10.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(10.dp)); Text("$percent%", color = Yellow, fontWeight = FontWeight.ExtraBold, fontSize = 49.sp)
                        Text("consistency. That’s all you.", color = Color.White, fontSize = 12.sp)
                    }
                    Flower(Modifier.size(119.dp), petal = Pink)
                }
            }
        }
        item {
            Row(Modifier.then(if (enlargedText) Modifier.height(IntrinsicSize.Min) else Modifier), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayCard(Yellow, Modifier.weight(1f).then(if (enlargedText) Modifier.fillMaxHeight() else Modifier)) { Icon(Icons.Rounded.Bolt, null); Spacer(Modifier.height(9.dp)); Text("$count", style = MaterialTheme.typography.headlineMedium); Text("wins this week", fontSize = 11.sp, color = Quiet) }
                PlayCard(Pink, Modifier.weight(1f).then(if (enlargedText) Modifier.fillMaxHeight() else Modifier)) { Icon(Icons.Rounded.LocalFireDepartment, null); Spacer(Modifier.height(9.dp)); Text("${state.habits.maxOfOrNull { state.streak(it) } ?: 0}", style = MaterialTheme.typography.headlineMedium); Text("best current streak", fontSize = 11.sp, color = Quiet) }
            }
        }
        item {
            PlayCard(Color.White) {
                Text("Your week in little wins", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth().then(if (enlargedText) Modifier.heightIn(min = 165.dp) else Modifier.height(165.dp)), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Bottom) {
                    dates.forEachIndexed { i, date ->
                        val p = state.percent(date)
                        Column(Modifier.weight(1f).semantics { contentDescription = "${date.format(ShortDate)}: $p percent" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text("$p%", fontSize = 9.sp, color = Quiet); Spacer(Modifier.height(7.dp))
                            Box(Modifier.fillMaxWidth().height((p * 1.05f + 4).dp).clip(RoundedCornerShape(11.dp)).background(if (i == 6) Purple else TileColors[i % 6]))
                            Spacer(Modifier.height(9.dp)); Text(date.dayOfWeek.name.take(1), fontSize = 10.sp, color = Quiet)
                        }
                    }
                }
            }
        }
        item { SectionTitle("Habits taking root") }
        items(state.habits, key = { it.id }) { habit ->
            val due = (0L..29L).map { today.minusDays(it) }.filter { habit.isDue(it) }
            val pct = if (due.isEmpty()) 0 else due.count { state.done(habit.id, it) } * 100 / due.size
            PlayCard(TileColors[habit.color.coerceIn(0, 5)]) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(HabitSymbols[habit.icon.coerceIn(0, 7)], null, Modifier.size(24.dp)); Spacer(Modifier.width(10.dp))
                    Text(habit.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontSize = 13.sp)
                    Text("$pct%", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Ink, trackColor = Color.White.copy(alpha = .65f), drawStopIndicator = {})
            }
        }
        item { Text("Consistency is based on scheduled days in the last 30 days.", color = Quiet, fontSize = 10.sp) }
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
private val MoodNames = listOf("Low", "Okay", "Good", "Great", "Amazing")

@Composable fun JournalScreen(state: HabitState, onSave: (Int, String) -> Unit, onDelete: (LocalDate) -> Unit,
    mood: Int, text: String, onDraft: (Int, String) -> Unit, draftDate: LocalDate,
    onEditDate: (LocalDate) -> Unit, draftDates: List<LocalDate>) {
    val today = LocalToday.current
    var deleteDate by remember { mutableStateOf<LocalDate?>(null) }
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun editDate(date: LocalDate) { onEditDate(date); scope.launch { scroll.animateScrollToItem(0) } }
    LazyColumn(state = scroll, contentPadding = PaddingValues(23.dp, 17.dp, 23.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageTitle("Hello, feelings.", "A soft landing for whatever’s on your mind.") }
        if (draftDate != today || draftDates.isNotEmpty()) item {
            Column {
                if (draftDate != today) { Text("Writing for ${draftDate.format(ShortDate)}", color = Purple); TextButton(onClick = { editDate(today) }) { Text("Back to today") } }
                draftDates.filter { it != draftDate }.forEach { date -> TextButton(onClick = { editDate(date) }) { Text("Continue draft · ${date.format(ShortDate)}") } }
            }
        }
        item {
            PlayCard(Pink) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("All your moods\nare welcome here.", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(8.dp)); Text("No perfect days required.", fontSize = 12.sp, color = Quiet) }
                    Flower(Modifier.size(100.dp), happy = mood > 1, petal = Color.White.copy(alpha = .85f))
                }
            }
        }
        item {
            SectionTitle("How’s your heart today?")
            Spacer(Modifier.height(15.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MoodIcons.forEachIndexed { index, icon ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (index == mood) Yellow else Color.White)
                        .semantics { selected = index == mood }.clickable(role = Role.RadioButton) { onDraft(index, text) }.padding(vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, MoodNames[index], Modifier.size(28.dp)); Spacer(Modifier.height(8.dp)); Text(MoodNames[index], fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            OutlinedTextField(text, { onDraft(mood, it.take(10000)) }, label = { Text("A thought worth keeping") }, placeholder = { Text("A tiny win, a happy accident, a thing you’re grateful for…") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp), shape = RoundedCornerShape(22.dp), minLines = 5)
            Spacer(Modifier.height(14.dp)); MainButton("Keep this little moment", enabled = text.isNotBlank()) { onSave(mood, text) }
            Spacer(Modifier.height(10.dp)); Text("Just for you. Saved privately on this device.", color = Quiet, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        item { SectionTitle("Pages from your story") }
        items(state.journal.sortedByDescending { it.date }, key = { it.date.toString() }) { entry ->
            PlayCard(Color.White) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.date.format(ShortDate), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Pill(MoodNames[entry.mood.coerceIn(0, 4)], Yellow, MoodIcons[entry.mood.coerceIn(0, 4)])
                }
                Spacer(Modifier.height(12.dp)); Text(entry.text, style = MaterialTheme.typography.bodyMedium, color = Quiet)
                Row(Modifier.align(Alignment.End)) {
                    TextButton(onClick = { editDate(entry.date) }) { Text("Edit", fontSize = 11.sp) }
                    TextButton(onClick = { deleteDate = entry.date }) { Text("Delete", fontSize = 11.sp) }
                }
            }
        }
    }
    deleteDate?.let { date -> AlertDialog(onDismissRequest = { deleteDate = null }, title = { Text("Delete this moment?") }, text = { Text("This reflection will be removed from your journal.") },
        confirmButton = { TextButton(onClick = { onDelete(date); deleteDate = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteDate = null }) { Text("Keep it") } }) }
}

@Composable fun SettingsScreen(state: HabitState, onRename: (String) -> Unit, onReset: () -> Unit, export: () -> Unit, restore: () -> Unit, damaged: Boolean, onSaveHabit: (Habit, () -> Unit) -> Unit, account: KimiAccount? = null, onAccount: () -> Unit = {}) {
    var name by rememberSaveable(state.name) { mutableStateOf(state.name) }
    var confirm by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(23.dp, 17.dp, 23.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageTitle("Your kind of happy.", "A little space that’s completely your own.") }
        item {
            PlayCard(Lavender) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Flower(Modifier.size(85.dp)); Spacer(Modifier.width(17.dp))
                    Column(Modifier.weight(1f)) { Text("Team ${state.name}", style = MaterialTheme.typography.headlineMedium); Text("Growing at your own pace.", color = Quiet, fontSize = 12.sp) }
                }
            }
        }
        item {
            PlayCard(Color.White) {
                Text("What should we call you?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp)); OutlinedTextField(name, { name = it.take(30) }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(15.dp))
                Spacer(Modifier.height(14.dp)); MainButton("That’s me!", enabled = name.isNotBlank()) { onRename(name) }
            }
        }
        item { AccountCard(account, onAccount) }
        item {
            PlayCard(Mint) {
                Row(verticalAlignment = Alignment.CenterVertically) { BubbleIcon(Icons.Rounded.Lock, Color.White.copy(alpha = .6f)); Spacer(Modifier.width(12.dp)); Text("Your world stays yours.", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(12.dp)); Text("Your habits and journal stay on this device. Your account and guest spaces are separate. Cloud sync is not enabled.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            PlayCard(Color.White) {
                SectionTitle("Your data")
                Text("Keep a copy of your habits, check-ins, and reflections.", fontSize = 12.sp, color = Quiet)
                if (damaged) Text("Your original save is protected. Restore a backup or reset to continue.", color = Purple)
                Spacer(Modifier.height(14.dp)); OutlinedButton(onClick = export, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Rounded.IosShare, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Export my space") }
                OutlinedButton(onClick = restore, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Restore a backup") }
                TextButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Start with a clean slate", color = Color(0xFF9C463A)) }
            }
        }
        item { NotificationSettings(state.habits, onSaveHabit) }
        item { Text(if (state.demo) "You’re exploring sample habits and check-ins. Reset for a fresh workspace." else "Made for small steps and fresh starts. Kimi 1.0", color = Quiet, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Ready for a fresh start?") }, text = { Text("All habits, check-ins, and journal entries will be removed. Export a copy first if you want to keep them.") },
        confirmButton = { TextButton(onClick = { onReset(); confirm = false }) { Text("Reset everything") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep my progress") } })
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
            Column(Modifier.weight(1f)) { Text(if (existing == null) "Plant a little habit." else "Make it more you.", style = MaterialTheme.typography.headlineMedium); Text("Start small. Let the good stuff grow.", color = Quiet, fontSize = 12.sp) }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close habit editor") }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TileColors[color]).padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            BubbleIcon(HabitSymbols[symbol], Color.White.copy(alpha = .65f)); Spacer(Modifier.width(13.dp))
            Column { Text(name.ifBlank { "Your next little win" }, style = MaterialTheme.typography.titleMedium); Text(goal.ifBlank { "A promise to yourself" }, fontSize = 11.sp, color = Quiet) }
        }
        OutlinedTextField(name, { name = it.take(70) }, label = { Text("Habit name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
        OutlinedTextField(goal, { goal = it.take(80) }, label = { Text("A small, specific goal") }, placeholder = { Text("e.g. Read for 15 minutes") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true)
        Text("Pick a personality", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            HabitSymbols.forEachIndexed { i, icon -> IconButton(onClick = { symbol = i }, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(if (symbol == i) Purple else Color.White)) { Icon(icon, "Habit icon ${i + 1}", tint = if (symbol == i) Color.White else Ink) } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            TileColors.forEachIndexed { i, c -> IconButton(onClick = { color = i }, modifier = Modifier.weight(1f).height(48.dp).clip(CircleShape).background(c)) { if (color == i) Icon(Icons.Rounded.Check, "Selected color ${i + 1}") else Box(Modifier.semantics { contentDescription = "Color ${i + 1}" }.size(24.dp)) } }
        }
        Text("Make it a regular thing", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FilterChip(!weekdays, { weekdays = false }, label = { Text("Every day") }, shape = CircleShape)
            FilterChip(weekdays, { weekdays = true }, label = { Text("Weekdays") }, shape = CircleShape)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Morning", "Afternoon", "Evening", "Anytime").forEach { t -> FilterChip(time == t, { time = t }, label = { Text(t, fontSize = 11.sp) }, shape = CircleShape) }
        }
        MainButton(if (existing == null) "Let’s make it a habit" else "Save my changes", enabled = !busy && name.isNotBlank() && goal.isNotBlank()) {
            onSave((existing ?: Habit(name = name, goal = goal)).copy(name = name.trim(), goal = goal.trim(), color = color, icon = symbol, time = time, weekdays = weekdays))
        }
        if (existing != null) TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Delete this habit", color = Color(0xFF9C463A)) }
    }
}
