package com.forma.habits

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import kotlinx.coroutines.delay

/** What plays during a focus session. [Breathe] also turns the flower into a breathing guide. */
internal enum class FocusSound(val label: Int, val icon: ImageVector, val make: (() -> Bed)?) {
    Rain(R.string.focus_sound_rain, Icons.Rounded.WaterDrop, { Rain(51) }),
    Cafe(R.string.focus_sound_cafe, Icons.Rounded.LocalCafe, { Cafe(52) }),
    Fireplace(R.string.focus_sound_fire, Icons.Rounded.LocalFireDepartment, { Fire(53) }),
    Brown(R.string.focus_sound_brown, Icons.Rounded.Grain, { Brown(54) }),
    Breathe(R.string.focus_sound_breathe, Icons.Rounded.Air, { Breath(55) }),
    Silence(R.string.focus_sound_silence, Icons.Rounded.VolumeOff, null)
}

internal val FocusMinutes = listOf(5, 10, 15, 20, 25, 30, 45, 60)

/** Habits about calm (meditation, wellness, the mind, or named so) start with the breathing guide. */
internal fun Habit.calm(): Boolean = icon in setOf(0, 7, 23) ||
    Regex("medit|breath|yoga|calm|mindful|relax", RegexOption.IGNORE_CASE).containsMatchIn(name)

/** The last minutes and sound used for each habit, kept on this device. */
private object FocusMemory {
    private fun prefs(context: Context) = context.getSharedPreferences("kimi_focus", Context.MODE_PRIVATE)
    fun minutes(context: Context, habit: Habit) = prefs(context).getInt("${habit.id}:minutes", if (habit.calm()) 5 else 20)
    fun sound(context: Context, habit: Habit): FocusSound = prefs(context).getString("${habit.id}:sound", null)
        ?.let { name -> FocusSound.entries.firstOrNull { it.name == name } } ?: if (habit.calm()) FocusSound.Breathe else FocusSound.Rain
    fun remember(context: Context, habit: Habit, minutes: Int, sound: FocusSound) =
        prefs(context).edit { putInt("${habit.id}:minutes", minutes); putString("${habit.id}:sound", sound.name) }
}

/**
 * A focus session for one habit: pick how long and what to listen to (rain, a café, a fireplace,
 * brown noise, a breathing guide or silence), then a ring counts down while it plays. A singing
 * bowl marks the end, and if the habit is still waiting for today, it is ticked off.
 *
 * The screen stays on while it runs. The countdown keeps true time even if Kimi goes to the
 * background, and the sound steps aside when something else (a call, say) needs to be heard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun FocusDialog(habit: Habit, canTick: Boolean, onTick: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var minutes by rememberSaveable { mutableIntStateOf(FocusMemory.minutes(context, habit)) }
    var sound by rememberSaveable { mutableStateOf(FocusMemory.sound(context, habit)) }
    // Running: the time it ends on the elapsed-time clock; paused: how much is left.
    var endsAt by rememberSaveable { mutableLongStateOf(0L) }
    var leftWhenPaused by rememberSaveable { mutableLongStateOf(0L) }
    var started by rememberSaveable { mutableLongStateOf(0L) }
    var finished by rememberSaveable { mutableStateOf(false) }
    val running = endsAt > 0L
    val paused = !running && leftWhenPaused > 0L
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val left = when { running -> (endsAt - now).coerceAtLeast(0L); paused -> leftWhenPaused; else -> minutes * 60_000L }
    val total = minutes * 60_000L
    DuckSoundscape()
    // The screen stays awake for the whole session.
    val view = LocalView.current
    DisposableEffect(running || paused) {
        view.keepScreenOn = running || paused
        onDispose { view.keepScreenOn = false }
    }
    val player = remember { FocusPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.stop() } }
    LaunchedEffect(running) {
        if (running) player.play(sound) else if (paused) player.pause()
    }
    LaunchedEffect(running, endsAt) {
        while (running) {
            now = SystemClock.elapsedRealtime()
            if (now >= endsAt) {
                endsAt = 0L; leftWhenPaused = 0L; finished = true
                player.stop()
                Sound.focusBell()
                haptics.play(Feel.Confirm)
                if (canTick) onTick()
                break
            }
            delay(250)
        }
    }
    // The breathing guide, read off the same clock as the tone.
    var breathClock by remember { mutableLongStateOf(0L) }
    LaunchedEffect(running, sound) {
        if (!running || sound != FocusSound.Breathe) return@LaunchedEffect
        while (true) withFrameMillis { breathClock = SystemClock.elapsedRealtime() - started }
    }
    fun start() {
        FocusMemory.remember(context, habit, minutes, sound)
        haptics.play(Feel.On)
        val remaining = if (paused) leftWhenPaused else total
        now = SystemClock.elapsedRealtime()
        if (!paused) started = now
        endsAt = now + remaining
        leftWhenPaused = 0L
    }
    fun pause() { leftWhenPaused = (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(1L); endsAt = 0L; haptics.play(Feel.Off) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.focus_title), color = Quiet, fontSize = 12.sp, lineHeight = 16.sp)
                    Text(habit.name, style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.cd_focus_close)) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Spacer(Modifier.height(10.dp))
                val breathing = sound == FocusSound.Breathe && running
                val breath = if (breathing) breathAt(breathClock / 1000.0).toFloat() else 0f
                val ringColor = Purple; val track = Overlay
                val timeLabel = clock(left)
                Box(Modifier.size(250.dp).semantics { contentDescription = timeLabel }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 12.dp.toPx()
                        val inset = Offset(stroke / 2, stroke / 2); val arc = Size(size.width - stroke, size.height - stroke)
                        drawArc(track, 0f, 360f, false, inset, arc, style = Stroke(stroke))
                        val share = if (finished) 1f else 1f - left.toFloat() / total.coerceAtLeast(1)
                        drawArc(ringColor, -90f, 360f * share, false, inset, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Flower(Modifier.size(96.dp).graphicsLayer { val s = .7f + .45f * breath; scaleX = s; scaleY = s },
                            alive = true, bloom = 6f, happy = true)
                        Spacer(Modifier.height(8.dp))
                        Text(timeLabel, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.testTag("focus_clock"))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    when {
                        finished -> stringResource(if (canTick) R.string.focus_done_ticked else R.string.focus_done)
                        breathing -> stringResource(if (breathingIn(breathClock / 1000.0)) R.string.focus_breathe_in else R.string.focus_breathe_out)
                        paused -> stringResource(R.string.focus_paused)
                        running -> stringResource(R.string.focus_running)
                        else -> stringResource(R.string.focus_ready)
                    },
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
                if (!running && !paused && !finished) {
                    Spacer(Modifier.height(22.dp))
                    Text(stringResource(R.string.focus_how_long), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusMinutes.forEach { m ->
                            FilterChip(selected = m == minutes, onClick = { minutes = m },
                                label = { Text(pluralStringResource(R.plurals.focus_minutes, m, m)) })
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.focus_what_to_hear), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusSound.entries.forEach { s ->
                            FilterChip(selected = s == sound, onClick = { sound = s },
                                leadingIcon = { Icon(s.icon, null, Modifier.size(18.dp)) },
                                label = { Text(stringResource(s.label)) })
                        }
                    }
                }
            }
            when {
                finished -> MainButton(stringResource(R.string.action_focus_close), onClick = onClose)
                running -> MainButton(stringResource(R.string.action_focus_pause)) { pause() }
                else -> MainButton(stringResource(if (paused) R.string.action_focus_resume else R.string.action_focus_start), Modifier.testTag("focus_start")) { start() }
            }
            if (running || paused) TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.action_focus_stop))
            }
        }
    }
}

private fun clock(millis: Long): String {
    val seconds = (millis + 999) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/**
 * Plays a focus sound with audio focus, so music pauses for it and it steps aside for a call;
 * when focus comes back, so does the sound.
 */
private class FocusPlayer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var bed: Bed? = null
    private var request: AudioFocusRequest? = null

    fun play(sound: FocusSound) {
        val make = sound.make
        if (bed != null) { bed?.fadeTo(1f, .8); return }
        if (make == null) return
        val made = make().also { it.fadeTo(1f, 2.0); Mixer.play(it) }
        bed = made
        request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> made.fadeTo(1f, 1.0)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> made.fadeTo(.3f, .3)
                    else -> made.fadeTo(0f, .3)
                }
            }.build().also { audio.requestAudioFocus(it) }
    }
    fun pause() { bed?.fadeTo(0f, .5) }
    fun stop() {
        bed?.stop(1.0); bed = null
        request?.let { audio.abandonAudioFocusRequest(it) }; request = null
    }
}
