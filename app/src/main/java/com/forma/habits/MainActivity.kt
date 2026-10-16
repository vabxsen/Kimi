package com.forma.habits

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) holdSplashUntilBloomed()
        ThemeSetting.load(this)
        GameSetting.load(this)
        enableEdgeToEdge()
        setContent {
            val dark = kimiDarkTheme()
            // System bar icons have to follow Kimi's own light/dark choice, not just the device's.
            LaunchedEffect(dark) {
                // While a theme ripple is still spreading, the top of the screen shows the old colours,
                // so the icons wait for it to finish.
                snapshotFlow { ThemeBlend.instant }.first { !it }
                val transparent = android.graphics.Color.TRANSPARENT
                this@MainActivity.enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                )
            }
            FormaTheme { ThemeRippleHost { KimiRoot() } }
        }
    }

    /**
     * Android removes the splash the moment Kimi draws its first frame. On a quick phone that is
     * before the flower has finished blooming, so it vanished half open. Taking over the exit keeps
     * the splash up for whatever is left of the bloom (never more than its 750 ms), then fades it.
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun holdSplashUntilBloomed() {
        splashScreen.setOnExitAnimationListener { splash ->
            val started = splash.iconAnimationStart?.toEpochMilli()
            val length = splash.iconAnimationDuration?.toMillis() ?: 0L
            // The start is stamped on one of two clocks depending on the Android build: an uptime
            // value is tiny next to a wall-clock one, so compare against whichever it came from.
            val now = if (started != null && started < 100_000_000_000L) SystemClock.uptimeMillis() else System.currentTimeMillis()
            val remaining = if (started == null) 0L else (started + length - now).coerceIn(0L, length)
            splash.animate().alpha(0f).setStartDelay(remaining).setDuration(SplashFadeMillis)
                .withEndAction { splash.remove() }.start()
        }
    }
}

private const val SplashFadeMillis = 180L

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
    val reduced = reducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::readBackup) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.refreshDate() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Watches for midnight only while Kimi is on screen; a composition outlives onStop, so a bare
    // loop here would keep waking the app in the background.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while (true) { delay(60_000); vm.refreshDate(sync = false) } }
    }
    BackHandler(page != Page.Today && !sheetOpen) { page = Page.Today }
    LaunchedEffect(Unit) { vm.events.collectLatest { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it, withDismissAction = true) } }
    fun edit(habit: Habit?) { editingId = habit?.id; sheetOpen = true }
    val gust = remember { PetalGust() }
    // The garden game, worked out from this space's history whenever it changes. It is worked out even
    // while switched off, so streak freezes already earned keep protecting streaks either way.
    val game = remember(state, vm.today) { state.game(vm.today) }
    val playing = GameSetting.enabled
    val context = LocalContext.current
    val gameMemory = remember { GameMemory(context, AccountSession.owner) }
    CompositionLocalProvider(LocalToday provides vm.today, LocalPetalGust provides gust, LocalGame provides game) {
    if (!state.onboarded) {
        Box(Modifier.fillMaxSize()) {
            WelcomeScreen(vm.busy, { name -> vm.start(name) { edit(null) } }, onAccount) {
                restore.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    } else {
    // Today's sky glows behind the top of the screen, status bar and wordmark included, and fades
    // away on the other tabs.
    val sky by animateFloatAsState(if (page == Page.Today) 1f else 0f, tween(if (reduced) 0 else 450), label = "sky")
    Box(Modifier.fillMaxSize().background(Cream)) {
    // Sky rewards from the garden: shooting stars at night, and a morning rainbow after a day with everything done.
    val yesterday = vm.today.minusDays(1)
    val rainbow = playing && game.unlocked(Unlock.Rainbow) && !state.isRestDay(yesterday) && state.percent(yesterday) == 100
    if (sky > 0f) LivingSky(Modifier.fillMaxWidth().height(340.dp).graphicsLayer { alpha = sky },
        shootingStars = playing && game.unlocked(Unlock.ShootingStars), rainbow = rainbow)
    Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) },
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
                        icon = { HopIcon(icon, page == destination, Modifier.size(23.dp)) }, label = { Text(stringResource(label), fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = OnAccent, selectedTextColor = Accent,
                            unselectedIconColor = Quiet, unselectedTextColor = Quiet, indicatorColor = Purple))
                }
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Tabs drift a little toward the side they come from in the bar while they crossfade.
            AnimatedContent(page, transitionSpec = {
                if (reduced) EnterTransition.None togetherWith ExitTransition.None
                else {
                    val forward = targetState.ordinal > initialState.ordinal
                    (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { (if (forward) it else -it) / 10 }) togetherWith
                        (fadeOut(tween(120)) + slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { (if (forward) -it else it) / 10 })
                }
            }, label = "page") { shown ->
            savedPages.SaveableStateProvider(shown.name) {
            when (shown) {
                Page.Today -> TodayScreen(state, vm::toggle, { edit(null) }, { edit(it) }, fresh = vm.freshHabit)
                Page.Habits -> HabitsScreen(state, { edit(null) }, { edit(it) }, fresh = vm.freshHabit)
                Page.Calendar -> CalendarScreen(state, vm::toggle) { edit(it) }
                Page.Insights -> InsightsScreen(state)
                Page.Journal -> JournalScreen(state, vm::reflect, vm::deleteReflection, vm.draftMood, vm.draftText, vm::updateDraft, vm.draftDate, vm::loadDraft, vm.draftDates)
                Page.Settings -> SettingsScreen(
                    state = state,
                    onRename = vm::rename,
                    onSaveHabit = vm::saveHabit,
                    account = account,
                    onAccount = onAccount,
                    syncable = vm.syncable,
                    syncing = vm.syncing,
                    syncFailed = vm.syncFailed,
                    syncError = vm.syncError,
                    onRetrySync = vm::syncNow
                )
            }
            }
            }
            // A small petal spinner in a chip, floating over the top of the page while work is in flight.
            if (vm.busy) Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).clip(CircleShape).background(Paper).padding(6.dp)) {
                PetalSpinner(Modifier.size(22.dp))
            }
        }
    }
    // A deleted habit's petals blow away over everything, once the sheet has gone.
    PetalGustOverlay(gust)
    // The optional soundscape under every tab, following the sky.
    TodaySoundscape(vm.today)
    // New levels and badges, celebrated once each; noted quietly while the game is switched off.
    GameMoments(game, gameMemory, quiet = !playing)
    }
    }
    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, containerColor = Cream,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            HabitEditor(state.habits.find { it.id == editingId }, onSave = { vm.saveHabit(it) { saved -> if (saved) sheetOpen = false } },
                onDelete = { deleteId = editingId }, onClose = { sheetOpen = false }, busy = vm.busy)
        }
    }
    deleteId?.let { id ->
        AlertDialog(onDismissRequest = { deleteId = null }, title = { Text(stringResource(R.string.dialog_delete_habit_title)) },
            text = { Text(stringResource(R.string.dialog_delete_habit_body)) },
            confirmButton = { TextButton(enabled = !vm.busy, onClick = { vm.deleteHabit(id) { deleteId = null; sheetOpen = false; gust.blow(id) } }) { Text(stringResource(R.string.action_delete_habit)) } },
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
