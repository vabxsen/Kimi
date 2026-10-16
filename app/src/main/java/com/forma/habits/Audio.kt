package com.forma.habits

import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.tanh

/**
 * Kimi's sound settings, kept on this device. Sounds are off until switched on. "Effects" are the
 * little sounds that answer what you do; the "soundscape" is the optional background on Today;
 * [ambience] is how loud backgrounds play (the garden's and Today's); [nudge] is the evening
 * notification when a streak is about to slip.
 */
object SoundSetting {
    private const val PREFS = "kimi_sound"
    var effects by mutableStateOf(false)
        private set
    var soundscape by mutableStateOf(false)
        private set
    var ambience by mutableFloatStateOf(.6f)
        private set
    var nudge by mutableStateOf(false)
        private set
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        effects = prefs.getBoolean("effects", false)
        soundscape = prefs.getBoolean("soundscape", false)
        ambience = prefs.getFloat("ambience", .6f).coerceIn(0f, 1f)
        nudge = prefs.getBoolean("nudge", false)
    }
    private fun save(context: Context, block: android.content.SharedPreferences.Editor.() -> Unit) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit(action = block)
    fun setEffects(context: Context, value: Boolean) { effects = value; save(context) { putBoolean("effects", value) } }
    fun setSoundscape(context: Context, value: Boolean) { soundscape = value; save(context) { putBoolean("soundscape", value) } }
    fun setAmbience(context: Context, value: Float) { ambience = value.coerceIn(0f, 1f); save(context) { putFloat("ambience", ambience) } }
    fun setNudge(context: Context, value: Boolean) { nudge = value; save(context) { putBoolean("nudge", value) } }
}

/**
 * Plays every sound Kimi makes, mixed into one stream. Short sounds are rendered on a background
 * thread (and kept, so each is made once) before they are handed over; the stream itself runs on
 * its own thread only while something is playing, and closes a few seconds after the last sound.
 */
internal object Mixer {
    private const val Chunk = 512
    private val incoming = ConcurrentLinkedQueue<Voice>()
    private var thread: Thread? = null
    @Volatile var running = false
        private set
    private val renderer = Executors.newSingleThreadExecutor { Thread(it, "kimi-sound-render").apply { isDaemon = true } }
    private val cache = object : LinkedHashMap<String, FloatArray>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > 48
    }

    fun play(voice: Voice) {
        incoming += voice
        start()
    }

    /** Plays the cue [key] (made by [make] the first time) at [gain], [delayMillis] from now. */
    fun cue(key: String, gain: Float = 1f, delayMillis: Int = 0, make: () -> FloatArray) {
        val asked = SystemClock.uptimeMillis()
        renderer.execute {
            val data = rendered(key, make) ?: return@execute
            val late = (SystemClock.uptimeMillis() - asked).toInt()
            play(SampleVoice(data, gain, samples((delayMillis - late).coerceAtLeast(0) / 1000.0)))
        }
    }

    /** Makes a cue ahead of time, so the first time it is wanted it plays at once. */
    fun prepare(key: String, make: () -> FloatArray) = renderer.execute { rendered(key, make) }

    private fun rendered(key: String, make: () -> FloatArray): FloatArray? {
        synchronized(cache) { cache[key] }?.let { return it }
        val data = runCatching(make).onFailure { Log.e("KimiSound", "Could not make $key", it) }.getOrNull() ?: return null
        synchronized(cache) { cache[key] = data }
        return data
    }

    @Synchronized private fun start() {
        if (thread != null) return
        thread = Thread(::loop, "kimi-sound").apply { start() }
    }

    /** Called by the stream when it has been quiet a while: stops, unless a sound arrived meanwhile. */
    @Synchronized private fun finishIfIdle(): Boolean {
        if (incoming.isNotEmpty()) return false
        thread = null
        return true
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val track = runCatching {
            val min = AudioTrack.getMinBufferSize(SampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SampleRate).setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, Chunk * 4 * 3))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build().also { it.play() }
        }.onFailure { Log.e("KimiSound", "No audio output", it) }.getOrNull()
        if (track == null) { incoming.clear(); synchronized(this) { thread = null }; return }
        running = true
        val voices = mutableListOf<Voice>()
        val buffer = FloatArray(Chunk)
        var quiet = 0
        try {
            while (true) {
                while (true) voices += incoming.poll() ?: break
                if (voices.isEmpty()) {
                    quiet += Chunk
                    if (quiet > samples(3.0) && finishIfIdle()) break
                } else quiet = 0
                buffer.fill(0f)
                val it = voices.iterator()
                while (it.hasNext()) if (!it.next().render(buffer, 0, Chunk)) it.remove()
                for (i in buffer.indices) buffer[i] = soften(buffer[i])
                if (track.write(buffer, 0, Chunk, AudioTrack.WRITE_BLOCKING) < 0) break
            }
        } catch (e: Exception) {
            Log.e("KimiSound", "Sound stream stopped", e)
        } finally {
            running = false
            runCatching { track.stop() }; track.release()
            synchronized(this) { if (thread === Thread.currentThread()) thread = null }
        }
    }

    /** Level as it is until close to the top, then rounded off rather than clipped. */
    private fun soften(x: Float): Float {
        val a = abs(x)
        if (a <= .8f) return x
        val s = .8f + .2f * tanh((a - .8f) / .2f)
        return if (x < 0) -s else s
    }
}

/**
 * The sounds themselves, by moment. Each little sound plays only when sounds are switched on and
 * the phone's ringer is on, so silent and vibrate always mean quiet, and they are mixed with
 * anything else playing rather than interrupting it. Sounds you start yourself, like a focus
 * session, play whatever the ringer says.
 */
object Sound {
    private var audio: AudioManager? = null
    fun init(context: Context) {
        audio = context.applicationContext.getSystemService(AudioManager::class.java)
        SoundSetting.load(context)
        if (SoundSetting.effects) prepare()
    }
    /** Whether the phone's ringer is on (not silent or vibrate). */
    fun ringerOn() = audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL
    private fun allowed() = SoundSetting.effects && ringerOn()
    /** Something else (music, a podcast, a call) is playing; backgrounds stay out of its way. */
    fun othersPlaying() = audio?.isMusicActive == true && !Mixer.running

    /** Makes the commonest sounds ahead, so the first tick of the day plays straight away. */
    fun prepare() {
        for (s in 0 until Synth.ScaleSteps) Mixer.prepare("tick$s") { Synth.tick(s) }
        Mixer.prepare("catch") { Synth.swipeCatch() }
        Mixer.prepare("crackle0") { Synth.crackle(0) }
    }

    private fun cue(key: String, gain: Float = 1f, delayMillis: Int = 0, make: () -> FloatArray) {
        if (allowed()) Mixer.cue(key, gain, delayMillis, make)
    }

    /** A habit ticked off: the [step]th note of the day's tune (0 for the day's first tick). */
    fun tick(step: Int) = cue("tick$step") { Synth.tick(step) }
    fun untick(step: Int) = cue("untick$step") { Synth.untick(step) }
    fun dayDone(notes: Int, delayMillis: Int = 0) = cue("day${notes.coerceIn(3, Synth.ScaleSteps)}", delayMillis = delayMillis) { Synth.dayDone(notes) }
    /** Starts the hum that follows a swipe, or null when sounds are off. */
    internal fun swipe(): SwipeHum? = if (allowed()) SwipeHum().also(Mixer::play) else null
    fun swipeCatch() = cue("catch") { Synth.swipeCatch() }
    fun crackle(level: Int, delayMillis: Int = 0) = cue("crackle$level", delayMillis = delayMillis) { Synth.crackle(level) }
    fun freeze(delayMillis: Int = 0) = cue("freeze", delayMillis = delayMillis) { Synth.freeze() }
    fun whoosh() = cue("whoosh") { Synth.whoosh() }
    fun levelUp() = cue("level") { Synth.levelUp() }
    fun petals(count: Int, delayMillis: Int = 0) = cue("petals${count.coerceIn(1, 12)}", delayMillis = delayMillis) { Synth.petals(count) }
    fun keepsake(season: Season) = cue("keepsake$season") { Synth.keepsake(season) }
    fun storyBloom() = cue("bloom") { Synth.storyBloom() }
    fun drumroll() = cue("drumroll") { Synth.drumroll() }
    fun flourish(stars: Int) = cue("flourish${stars.coerceIn(0, 7)}") { Synth.flourish(stars) }
    fun storyEnd(level: Int) = cue("storyEnd$level") { Synth.storyEnd(level) }
    fun giggle() = cue("giggle") { Synth.giggle() }
    fun yawn() = cue("yawn") { Synth.yawn() }
    fun wheee() = cue("wheee") { Synth.wheee() }
    /** The bell at the end of a focus session: asked for, so it plays even with sounds off. */
    fun focusBell() = Mixer.cue("bell", .9f) { Synth.focusBell() }
}

/** How loud a streak's sound should be: fuller at 7, 30 and 100 days. */
internal fun streakLevel(days: Int) = when {
    days >= 100 -> 3
    days >= 30 -> 2
    days >= 7 -> 1
    else -> 0
}

/** Backgrounds sit under everything else: at full ambience volume they are about as loud as a soft tick. */
private const val AmbientGain = 1.2f

internal fun Context.findActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is ComponentActivity) return c; c = c.baseContext }
    return null
}

/**
 * Plays a background while this is on screen and Kimi is in front, fading in and out, at the
 * ambience volume. It never starts over something else that is already playing, and a new [key]
 * (a different scene) cross-fades to it.
 */
@Composable internal fun AmbientBed(key: Any?, enabled: Boolean, make: () -> Bed) {
    val context = LocalContext.current
    val lifecycle = (context.findActivity() ?: return).lifecycle
    val playing = remember { arrayOfNulls<Bed>(1) }
    val volume = SoundSetting.ambience
    DisposableEffect(key, enabled, lifecycle) {
        fun start() {
            if (playing[0] != null || !enabled || Sound.othersPlaying()) return
            playing[0] = make().also { it.fadeTo(SoundSetting.ambience * AmbientGain, 2.5); Mixer.play(it) }
        }
        fun stop() { playing[0]?.stop(1.2); playing[0] = null }
        val observer = LifecycleEventObserver { _, event ->
            when (event) { Lifecycle.Event.ON_START -> start(); Lifecycle.Event.ON_STOP -> stop(); else -> Unit }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
    LaunchedEffect(volume) { playing[0]?.fadeTo(volume * AmbientGain, .4) }
}
