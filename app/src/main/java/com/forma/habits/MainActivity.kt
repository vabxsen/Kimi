package com.forma.habits

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
        ThemeSetting.load(this)
        enableEdgeToEdge()
        setContent {
            val dark = kimiDarkTheme()
            // System bar icons have to follow Kimi's own light/dark choice, not just the device's.
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                this@MainActivity.enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                )
            }
            FormaTheme { KimiRoot() }
        }
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
            WelcomeScreen(vm.busy, { name -> vm.start(name) { edit(null) } }, onAccount) {
                restore.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    } else {
    Scaffold(containerColor = Cream, snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 23.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Flower(Modifier.size(32.dp), petal = Color(0xFFD8CBFF))
                    Text(stringResource(R.string.app_name), fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, letterSpacing = (-1).sp)
                    Text(stringResource(R.string.app_wordmark_dot), color = Accent, fontWeight = FontWeight.ExtraBold, fontSize = 27.sp)
                }
                val settingsLabel = stringResource(R.string.cd_open_settings)
                IconButton(onClick = { page = Page.Settings }, modifier = Modifier.size(48.dp).background(Lavender, CircleShape).semantics { contentDescription = settingsLabel }) {
                    Text(state.name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Ink)
                }
            }
        }, bottomBar = {
            NavigationBar(containerColor = Cream, tonalElevation = 0.dp) {
                listOf(Triple(Page.Today, Icons.Rounded.WbSunny, R.string.nav_today),
                    Triple(Page.Habits, Icons.Rounded.Dashboard, R.string.nav_habits),
                    Triple(Page.Calendar, Icons.Rounded.CalendarMonth, R.string.nav_calendar),
                    Triple(Page.Insights, Icons.Rounded.BarChart, R.string.nav_insights),
                    Triple(Page.Journal, Icons.Rounded.AutoStories, R.string.nav_journal)).forEach { (destination, icon, label) ->
                    NavigationBarItem(selected = page == destination, onClick = { page = destination },
                        icon = { Icon(icon, null, Modifier.size(23.dp)) }, label = { Text(stringResource(label), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = OnAccent, selectedTextColor = Accent,
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
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Accent)
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
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text(stringResource(R.string.dialog_delete_habit_title)) },
            text = { Text(stringResource(R.string.dialog_delete_habit_body)) },
            confirmButton = { TextButton(enabled = !vm.busy, onClick = { vm.deleteHabit(id) { deleteId = null; sheetOpen = false } }) { Text(stringResource(R.string.action_delete_habit)) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.action_keep_it)) } })
    }
    vm.pendingRestore?.let { backup ->
        AlertDialog(onDismissRequest = vm::cancelRestore, title = { Text(stringResource(R.string.dialog_restore_title)) },
            text = {
                val checkIns = backup.checks.values.sumOf { it.size }
                Text(stringResource(R.string.dialog_restore_body, backup.name,
                    pluralStringResource(R.plurals.habit_count, backup.habits.size, backup.habits.size),
                    pluralStringResource(R.plurals.checkin_count, checkIns, checkIns),
                    pluralStringResource(R.plurals.reflection_count, backup.journal.size, backup.journal.size)))
            },
            confirmButton = { TextButton(enabled = !vm.busy, onClick = vm::confirmRestore) { Text(stringResource(R.string.action_restore_backup)) } },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text(stringResource(R.string.action_keep_current_space)) } })
    }
    }
}
