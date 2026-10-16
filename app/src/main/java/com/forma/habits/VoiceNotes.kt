package com.forma.habits

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate
import kotlin.math.sqrt

/**
 * Voice notes for the journal: a short recording kept with a day's reflection, played back over
 * its own little waveform. They are recorded only when you tap the microphone, and stay on this
 * phone: they are never synced, uploaded or put in backups, and they go when the reflection does.
 */
object VoiceNotes {
    const val MaxMillis = 3 * 60_000
    const val Bars = 48
    /** Bumped whenever a note is saved or deleted, so every view of it updates. */
    var changes by mutableIntStateOf(0)
        private set

    fun dir(context: Context, owner: String = AccountSession.owner) =
        File(context.filesDir, "voice_notes/" + AccountSession.preferenceName("space", owner))
    fun audio(context: Context, date: LocalDate) = File(dir(context), "$date.m4a")
    private fun wave(context: Context, date: LocalDate) = File(dir(context), "$date.wave")
    fun exists(context: Context, date: LocalDate) = audio(context, date).isFile

    /** Days with a note, newest first. */
    fun dates(context: Context): List<LocalDate> = dir(context).listFiles().orEmpty()
        .mapNotNull { f -> f.name.takeIf { it.endsWith(".m4a") && !it.contains(".recording") }?.removeSuffix(".m4a") }
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sortedDescending()

    /** How long a note is, and the shape of its waveform. */
    fun shape(context: Context, date: LocalDate): Pair<Long, FloatArray> {
        val lines = runCatching { wave(context, date).readLines() }.getOrDefault(emptyList())
        val millis = lines.firstOrNull()?.toLongOrNull() ?: 0L
        val bars = lines.getOrNull(1)?.split(',')?.mapNotNull { it.toFloatOrNull() }?.toFloatArray() ?: FloatArray(Bars) { .3f }
        return millis to bars
    }

    internal fun save(context: Context, date: LocalDate, recording: File, millis: Long, levels: List<Float>) {
        val target = audio(context, date)
        target.delete()
        if (!recording.renameTo(target)) { recording.delete(); return }
        wave(context, date).writeText("$millis\n" + bars(levels).joinToString(",") { "%.3f".format(java.util.Locale.ROOT, it) })
        changes++
    }

    fun delete(context: Context, date: LocalDate) {
        audio(context, date).delete(); wave(context, date).delete()
        changes++
    }

    /** Removes every note in a space, as when its account is deleted. */
    fun eraseSpace(context: Context, owner: String) = dir(context, owner).deleteRecursively()

    /** Squeezes a recording's loudness readings into [Bars] bars, keeping each stretch's peak. */
    internal fun bars(levels: List<Float>): FloatArray {
        if (levels.isEmpty()) return FloatArray(Bars) { .05f }
        return FloatArray(Bars) { b ->
            val from = b * levels.size / Bars
            val to = ((b + 1) * levels.size / Bars).coerceAtLeast(from + 1).coerceAtMost(levels.size)
            (from until to).maxOf { levels[it] }.coerceIn(.05f, 1f)
        }
    }
}

/** Records one note at a time into a temporary file, keeping a loudness reading as it goes. */
private class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    val levels = mutableStateListOf<Float>()
    val elapsed get() = if (recorder == null) 0L else SystemClock.elapsedRealtime() - startedAt
    val recording get() = recorder != null

    fun start(date: LocalDate, onLimit: () -> Unit): Boolean {
        val dir = VoiceNotes.dir(context).apply { mkdirs() }
        val target = File(dir, "$date.recording.m4a")
        @Suppress("DEPRECATION")
        val made = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        return try {
            made.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1); setAudioSamplingRate(32000); setAudioEncodingBitRate(64000)
                setMaxDuration(VoiceNotes.MaxMillis)
                setOutputFile(target.path)
                setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimit() }
                prepare(); start()
            }
            recorder = made; file = target; startedAt = SystemClock.elapsedRealtime(); levels.clear()
            true
        } catch (e: Exception) {
            Log.e("KimiVoice", "Could not start recording", e)
            made.release(); target.delete()
            false
        }
    }

    /** A loudness reading, 0 to 1, shaped so quiet speech still shows. */
    fun sample() {
        val r = recorder ?: return
        levels += sqrt((runCatching { r.maxAmplitude }.getOrDefault(0) / 32767f).coerceIn(0f, 1f))
    }

    /** Stops and keeps the note for [date]; a recording too short to hold anything is dropped. */
    fun finish(date: LocalDate) {
        val r = recorder ?: return
        val millis = elapsed
        val ok = runCatching { r.stop() }.isSuccess && millis > 600
        r.release(); recorder = null
        val f = file ?: return
        if (ok) VoiceNotes.save(context, date, f, millis, levels.toList()) else f.delete()
    }

    fun cancel() {
        recorder?.let { runCatching { it.stop() }; it.release() }
        recorder = null; file?.delete()
    }
}

private fun clock(millis: Long): String {
    val s = millis / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * A day's voice note. With [editable] (the journal's writing area), a button records one, and a
 * recorded one can be deleted; otherwise it only plays, and shows nothing if there is no note.
 */
@Composable fun VoiceNote(date: LocalDate, editable: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val changes = VoiceNotes.changes
    val exists = VoiceNotes.exists(context, date)
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }
    fun stop() { recorder.finish(date); recording = false }
    fun begin() {
        denied = false
        recording = recorder.start(date) { stop() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else denied = true
    }
    // Leaving the page or the app keeps what has been said so far.
    val lifecycle = context.findActivity()?.lifecycle
    DisposableEffect(date, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && recorder.recording) stop() }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer); if (recorder.recording) recorder.finish(date) }
    }
    LaunchedEffect(recording) {
        while (recording) { recorder.sample(); elapsed = recorder.elapsed; delay(80) }
    }
    when {
        recording -> {
            DuckSoundscape()
            val pulse = rememberInfiniteTransition(label = "recording").animateFloat(.35f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "dot")
            val stopLabel = stringResource(R.string.cd_voice_stop)
            Row(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Paper).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).graphicsLayer { alpha = pulse.value }.clip(CircleShape).background(Color(0xFFE5484D)))
                Spacer(Modifier.width(10.dp))
                Waveform(recorder.levels.takeLast(VoiceNotes.Bars).toFloatArray(), 1f, Modifier.weight(1f).height(32.dp), live = true)
                Spacer(Modifier.width(10.dp))
                Text(clock(elapsed), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = ::stop, modifier = Modifier.testTag("voice_stop").semantics { contentDescription = stopLabel }) {
                    Icon(Icons.Rounded.Stop, null, tint = Ink)
                }
            }
        }
        exists -> VoicePlayer(date, editable, modifier)
        editable -> {
            OutlinedButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
                else permission.launch(Manifest.permission.RECORD_AUDIO)
            }, modifier = modifier.fillMaxWidth().testTag("voice_record")) {
                Icon(Icons.Rounded.Mic, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_voice_record))
            }
            if (denied) Text(stringResource(R.string.voice_permission_needed), color = Quiet, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable private fun VoicePlayer(date: LocalDate, editable: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val changes = VoiceNotes.changes
    val (millis, bars) = remember(date, changes) { VoiceNotes.shape(context, date) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var played by remember { mutableLongStateOf(0L) }
    val playing = player != null
    fun release() { player?.release(); player = null; played = 0L }
    DisposableEffect(date, changes) { onDispose { release() } }
    LaunchedEffect(playing) {
        while (player != null) { played = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L); delay(50) }
    }
    if (playing) DuckSoundscape()
    val playLabel = stringResource(if (playing) R.string.cd_voice_pause else R.string.cd_voice_play)
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Overlay).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (playing) release()
            else player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    setDataSource(VoiceNotes.audio(context, date).path)
                    setOnCompletionListener { release() }
                    prepare(); start()
                }
            }.onFailure { Log.e("KimiVoice", "Could not play", it) }.getOrNull()
        }, modifier = Modifier.semantics { contentDescription = playLabel }) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Ink)
        }
        Waveform(bars, if (millis > 0) (played.toFloat() / millis).coerceIn(0f, 1f) else 0f, Modifier.weight(1f).height(30.dp))
        Spacer(Modifier.width(10.dp))
        Text(clock(if (playing) played else millis), fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = if (editable) 0.dp else 12.dp))
        if (editable) IconButton(onClick = { release(); VoiceNotes.delete(context, date) }) {
            Icon(Icons.Rounded.Delete, stringResource(R.string.cd_voice_delete), tint = Quiet)
        }
    }
}

/** Bars of loudness; those already played are drawn in ink, the rest faded. While [live], all are ink. */
@Composable private fun Waveform(bars: FloatArray, progress: Float, modifier: Modifier, live: Boolean = false) {
    val ink = Ink; val faded = Quiet.copy(alpha = .4f)
    Canvas(modifier) {
        val count = VoiceNotes.Bars
        val slot = size.width / count
        val w = (slot * .55f).coerceAtLeast(1.5f)
        for (i in 0 until count) {
            val level = if (live) bars.getOrNull(i - (count - bars.size)) ?: 0f else bars.getOrElse(i) { 0f }
            val h = (size.height * level).coerceAtLeast(w)
            val color = if (live || (i + .5f) / count <= progress) ink else faded
            drawRoundRect(color, Offset(i * slot + (slot - w) / 2, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
        }
    }
}
