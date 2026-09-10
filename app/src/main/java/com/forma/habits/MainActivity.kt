package com.forma.habits

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FormaTheme { KimiRoot() } }
    }
}

enum class Page { Today, Habits, Calendar, Insights, Journal, Settings }
val LocalToday = compositionLocalOf { LocalDate.now() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FormaApp(vm: FormaViewModel = viewModel(), account: KimiAccount? = null, onAccount: () -> Unit = {}) {
    var page by rememberSaveable { mutableStateOf(Page.Today) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val state = vm.state
    val savedPages = rememberSaveableStateHolder()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportBackup) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::readBackup) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refreshDate() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { while (true) { delay(60_000); vm.refreshDate() } }
    BackHandler(page != Page.Today && !sheetOpen) { page = Page.Today }
    LaunchedEffect(Unit) { vm.events.collectLatest { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it, withDismissAction = true) } }
    fun edit(habit: Habit?) { editingId = habit?.id; sheetOpen = true }
    CompositionLocalProvider(LocalToday provides vm.today) {
    if (!state.onboarded) {
        Box(Modifier.fillMaxSize()) {
            WelcomeScreen(vm.busy, vm::start, onAccount) { restore.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    } else {
    Scaffold(containerColor = Cream, snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 23.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Flower(Modifier.size(32.dp), petal = Color(0xFFD8CBFF))
                    Text("Kimi", fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, letterSpacing = (-1).sp)
                    Text(".", color = Purple, fontWeight = FontWeight.ExtraBold, fontSize = 27.sp)
                }
                IconButton(onClick = { page = Page.Settings }, modifier = Modifier.size(48.dp).background(Lavender, CircleShape).semantics { contentDescription = "Open settings" }) {
                    Text(state.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Ink)
                }
            }
        }, bottomBar = {
            NavigationBar(containerColor = Cream, tonalElevation = 0.dp) {
                listOf(Page.Today to Icons.Rounded.WbSunny, Page.Habits to Icons.Rounded.Dashboard,
                    Page.Calendar to Icons.Rounded.CalendarMonth, Page.Insights to Icons.Rounded.BarChart,
                    Page.Journal to Icons.Rounded.AutoStories).forEach { (destination, icon) ->
                    NavigationBarItem(selected = page == destination, onClick = { page = destination },
                        icon = { Icon(icon, null, Modifier.size(23.dp)) }, label = { Text(destination.name, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = Purple,
                            unselectedIconColor = Quiet, unselectedTextColor = Quiet, indicatorColor = Purple))
                }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            savedPages.SaveableStateProvider(page.name) {
            when (page) {
                Page.Today -> TodayScreen(state, vm::toggle, { edit(null) }, { page = Page.Journal })
                Page.Habits -> HabitsScreen(state, { edit(null) }, { edit(it) })
                Page.Calendar -> CalendarScreen(state, vm::toggle)
                Page.Insights -> InsightsScreen(state)
                Page.Journal -> JournalScreen(state, vm::reflect, vm::deleteReflection, vm.draftMood, vm.draftText, vm::updateDraft, vm.draftDate, vm::loadDraft, vm.draftDates())
                Page.Settings -> SettingsScreen(state, vm::rename, vm::reset,
                    { export.launch("Kimi-backup-${vm.today}.json") },
                    { restore.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }, vm.damaged, vm::saveHabit, account, onAccount)
            }
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Purple)
        }
    }
    }
    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, containerColor = Cream,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            HabitEditor(state.habits.find { it.id == editingId }, onSave = { vm.saveHabit(it) { sheetOpen = false } },
                onDelete = { deleteId = editingId }, onClose = { sheetOpen = false }, busy = vm.busy)
        }
    }
    deleteId?.let { id ->
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Let this one go?") },
            text = { Text("This removes the habit and its check-in history. There’s always room for a new beginning.") },
            confirmButton = { TextButton(enabled = !vm.busy, onClick = { vm.deleteHabit(id) { deleteId = null; sheetOpen = false } }) { Text("Delete habit") } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Keep it") } })
    }
    vm.pendingRestore?.let { backup ->
        AlertDialog(onDismissRequest = vm::cancelRestore, title = { Text("Bring your space back?") },
            text = { Text("This backup belongs to ${backup.name}: ${backup.habits.size} habits, ${backup.checks.values.sumOf { it.size }} check-ins, and ${backup.journal.size} reflections. Restoring replaces your current space. Export it first if you want to keep both.") },
            confirmButton = { TextButton(enabled = !vm.busy, onClick = vm::confirmRestore) { Text("Restore backup") } },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text("Keep current space") } })
    }
    }
}
