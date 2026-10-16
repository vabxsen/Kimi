package com.forma.habits

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Kimi's sounds are made here, in code, rather than played from recordings: a handful of sines,
 * noise and filters shaped into chimes, plucks, crackles, birds and rain. That keeps the app small,
 * keeps every sound in one consistent voice, and means nothing has to be licensed or downloaded.
 *
 * Everything in this file is plain Kotlin with no Android in it, so each sound can be rendered and
 * checked by a unit test. Short sounds ("cues") come back as whole buffers of samples between -1
 * and 1; long ones ("beds") are [Voice]s that render a little at a time, for as long as they play.
 */
internal const val SampleRate = 32000
private const val Tau = 2 * PI

internal fun samples(seconds: Double) = (seconds * SampleRate).roundToInt().coerceAtLeast(1)

/** Equal-tempered pitch in hertz, [semis] semitones above (or below) A4. */
internal fun pitch(semis: Double) = 440.0 * 2.0.pow(semis / 12.0)

/** Something that plays over time: adds itself into [out] from [from] for [n] samples, and says whether it is still going. */
internal interface Voice {
    fun render(out: FloatArray, from: Int, n: Int): Boolean
}

/** A finished buffer played once, [delay] samples after it is started. */
internal class SampleVoice(private val data: FloatArray, private val gain: Float = 1f, delay: Int = 0) : Voice {
    private var at = -delay
    override fun render(out: FloatArray, from: Int, n: Int): Boolean {
        for (i in 0 until n) {
            val k = at + i
            if (k in data.indices) out[from + i] += data[k] * gain
        }
        at += n
        return at < data.size
    }
}

/** A two-pole filter (the "RBJ cookbook" designs), retunable on the fly. */
internal class Biquad {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
    fun bandpass(freq: Double, q: Double) = apply { design(freq, q) { alpha, _ -> doubleArrayOf(alpha, 0.0, -alpha) } }
    fun lowpass(freq: Double, q: Double = .707) = apply { design(freq, q) { _, c -> doubleArrayOf((1 - c) / 2, 1 - c, (1 - c) / 2) } }
    fun highpass(freq: Double, q: Double = .707) = apply { design(freq, q) { _, c -> doubleArrayOf((1 + c) / 2, -(1 + c), (1 + c) / 2) } }
    private inline fun design(freq: Double, q: Double, b: (Double, Double) -> DoubleArray) {
        val w = Tau * freq.coerceIn(10.0, SampleRate * .45) / SampleRate
        val c = cos(w); val alpha = sin(w) / (2 * q)
        val a0 = 1 + alpha
        val (n0, n1, n2) = b(alpha, c).let { Triple(it[0], it[1], it[2]) }
        b0 = n0 / a0; b1 = n1 / a0; b2 = n2 / a0; a1 = -2 * c / a0; a2 = (1 - alpha) / a0
    }
    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }
}

/** Noise in three colours: white (hiss), pink (rain) and brown (rumble, wind, a quiet room). */
internal class Noise(seed: Int) {
    private val rnd = Random(seed)
    private var p0 = 0.0; private var p1 = 0.0; private var p2 = 0.0; private var b = 0.0
    fun white() = rnd.nextDouble() * 2 - 1
    fun pink(): Double {
        val w = white()
        p0 = .99765 * p0 + w * .0990460; p1 = .963 * p1 + w * .2965164; p2 = .57 * p2 + w * 1.0526913
        return (p0 + p1 + p2 + w * .1848) * .18
    }
    fun brown(): Double {
        b = (b + .02 * white()) / 1.02
        return b * 3.5
    }
}

/** One overtone of a struck or plucked tone: its pitch as a multiple, its loudness, and how fast it dies (seconds to fall to about a third). */
internal class Partial(val ratio: Double, val amp: Double, val decay: Double)

internal object Synth {
    // ---- Building blocks -------------------------------------------------------------------------

    /** A struck tone: each partial a sine that dies away at its own rate. */
    fun tone(freq: Double, seconds: Double, partials: List<Partial>, attack: Double = .003): FloatArray {
        val n = samples(seconds); val out = FloatArray(n)
        val a = samples(attack)
        for (p in partials) {
            val f = freq * p.ratio
            if (f >= SampleRate * .45) continue
            val w = Tau * f / SampleRate
            val k = exp(-1.0 / (p.decay * SampleRate))
            var e = p.amp
            for (i in 0 until n) {
                val rise = if (i < a) i.toDouble() / a else 1.0
                out[i] += (sin(w * i) * e * rise).toFloat()
                e *= k
            }
        }
        return out
    }

    /** A frequency-modulated bell: bright as it is struck, mellowing as the modulation fades. */
    fun fmBell(freq: Double, seconds: Double, ratio: Double, index: Double, indexDecay: Double, decay: Double, attack: Double = .002): FloatArray {
        val n = samples(seconds); val out = FloatArray(n)
        val a = samples(attack)
        val wc = Tau * freq / SampleRate; val wm = Tau * freq * ratio / SampleRate
        for (i in 0 until n) {
            val t = i.toDouble() / SampleRate
            val idx = index * exp(-t / indexDecay)
            val rise = if (i < a) i.toDouble() / a else 1.0
            out[i] = (sin(wc * i + idx * sin(wm * i)) * exp(-t / decay) * rise).toFloat()
        }
        return out
    }

    /** A soft plucked string: harmonics that fade faster the higher they are, over a tiny scratch of noise. */
    fun pluck(freq: Double, seconds: Double = 1.2, bright: Double = .6, seed: Int = 1): FloatArray {
        val partials = (1..7).map { k -> Partial(k.toDouble(), bright.pow(k - 1.0) / k, .9 / k.toDouble().pow(.7)) }
        val out = tone(freq, seconds, partials, attack = .002)
        val noise = Noise(seed); val f = Biquad().bandpass(freq * 3, 1.0)
        for (i in 0 until min(samples(.012), out.size)) out[i] += (f.process(noise.white()) * .35 * (1 - i / samples(.012).toDouble())).toFloat()
        return out
    }

    /** Glassy marimba bar: the tone each tick plays. */
    fun bar(freq: Double, seconds: Double = .9) = tone(freq, seconds, listOf(
        Partial(1.0, 1.0, .42), Partial(2.0, .16, .22), Partial(3.98, .14, .07), Partial(9.2, .04, .025)))

    /** A singing bowl: long and shimmering, with two close tones beating slowly against each other. */
    fun bowl(freq: Double, seconds: Double = 6.0) = tone(freq, seconds, listOf(
        Partial(1.0, .8, 2.6), Partial(1.004, .5, 2.4), Partial(2.71, .35, 1.4), Partial(5.15, .14, .6), Partial(8.9, .05, .25)), attack = .006)

    /** White noise through a band, for whooshes, hisses and drums. */
    fun band(seconds: Double, centre: (Double) -> Double, q: Double, envelope: (Double) -> Double, seed: Int): FloatArray {
        val n = samples(seconds); val out = FloatArray(n)
        val noise = Noise(seed); val f = Biquad()
        for (i in 0 until n) {
            val t = i.toDouble() / n
            if (i % 32 == 0) f.bandpass(centre(t), q)
            out[i] = (f.process(noise.white()) * envelope(t)).toFloat()
        }
        return out
    }

    /** A little room around a sound: four combs and two all-passes (Schroeder's reverb), mixed in at [wet]. */
    fun reverb(dry: FloatArray, wet: Double = .2, size: Double = 1.0, tail: Double = .9, damp: Double = .35): FloatArray {
        val n = dry.size + samples(tail); val out = FloatArray(n)
        val combs = doubleArrayOf(29.7, 37.1, 41.1, 43.7).map { ms -> DoubleArray((ms * size * SampleRate / 1000).roundToInt().coerceAtLeast(1)) }
        val combAt = IntArray(4); val combLow = DoubleArray(4)
        val passes = doubleArrayOf(5.0, 1.7).map { ms -> DoubleArray((ms * SampleRate / 1000).roundToInt().coerceAtLeast(1)) }
        val passAt = IntArray(2)
        for (i in 0 until n) {
            val x = if (i < dry.size) dry[i].toDouble() else 0.0
            var sum = 0.0
            combs.forEachIndexed { c, line ->
                val y = line[combAt[c]]
                combLow[c] = y * (1 - damp) + combLow[c] * damp
                line[combAt[c]] = x + combLow[c] * .78
                combAt[c] = (combAt[c] + 1) % line.size
                sum += y
            }
            var y = sum / 4
            passes.forEachIndexed { p, line ->
                val held = line[passAt[p]]
                val v = y + held * .5
                line[passAt[p]] = v
                y = held - v * .5
                passAt[p] = (passAt[p] + 1) % line.size
            }
            out[i] = (x + y * wet).toFloat()
        }
        return out
    }

    /** Adds [src] into [dst] starting [at] samples in, at [gain]; [dst] is grown if it is too short. */
    fun mix(dst: FloatArray, src: FloatArray, at: Int, gain: Double = 1.0): FloatArray {
        val out = if (at + src.size > dst.size) dst.copyOf(at + src.size) else dst
        for (i in src.indices) out[at + i] += (src[i] * gain).toFloat()
        return out
    }

    /** Scales the loudest sample to [peak] and softens the very ends, so nothing clicks. */
    fun finish(buffer: FloatArray, peak: Double): FloatArray {
        val top = buffer.maxOfOrNull { abs(it) } ?: 0f
        if (top > 0f) { val g = (peak / top).toFloat(); for (i in buffer.indices) buffer[i] *= g }
        val inn = min(samples(.0015), buffer.size / 2); val out = min(samples(.012), buffer.size / 2)
        for (i in 0 until inn) buffer[i] *= i.toFloat() / inn
        for (i in 0 until out) buffer[buffer.size - 1 - i] *= i.toFloat() / out
        return buffer
    }

    /** Rises from silence and falls back, shaped like half a sine: for swells and gusts. */
    fun swell(t: Double) = sin(PI * t.coerceIn(0.0, 1.0))

    // ---- Ticking off habits ----------------------------------------------------------------------

    private val Pentatonic = intArrayOf(0, 2, 4, 7, 9)
    const val ScaleSteps = 12

    /** The [step]th note of the day's tune: up a major pentatonic scale from G4, so any run of ticks sounds sweet. */
    fun scaleNote(step: Int): Double {
        val s = step.coerceIn(0, ScaleSteps - 1)
        return pitch(-2.0 + 12 * (s / 5) + Pentatonic[s % 5])
    }

    /** One tick: the next note up the tune. */
    fun tick(step: Int) = finish(reverb(bar(scaleNote(step)), wet = .22), .42)

    /** Undoing a tick plays its note backwards, swelling in and stopping short. */
    fun untick(step: Int): FloatArray {
        val backwards = tick(step).reversedArray()
        return finish(backwards.copyOfRange(samples(.55).coerceAtMost(backwards.size / 2), backwards.size), .3)
    }

    /** The day finished: the whole tune again, quick, then held as a chord. */
    fun dayDone(notes: Int): FloatArray {
        val count = notes.coerceIn(3, ScaleSteps)
        var out = FloatArray(samples(2.4))
        for (s in 0 until count) out = mix(out, bar(scaleNote(s), .7), samples(.055 * s), .55)
        val chordAt = samples(.055 * count + .08)
        for (s in 0 until count) out = mix(out, tone(scaleNote(s), 1.9, listOf(Partial(1.0, 1.0, .9), Partial(2.0, .12, .5)), attack = .03), chordAt, .7 / sqrt(count.toDouble()))
        out = mix(out, sparkle(9, 1.2, seed = 3), chordAt, .3)
        return finish(reverb(out, wet = .3, tail = 1.2), .6)
    }

    /** The catch at the end of a swipe: a bright pluck. */
    fun swipeCatch() = finish(reverb(pluck(pitch(10.0), .8, bright = .7)), .45)

    // ---- Streaks ---------------------------------------------------------------------------------

    /** A short fire crackle as a streak grows; at 7, 30 and 100 days ([level] 1-3) it burns fuller and warmer. */
    fun crackle(level: Int, seed: Int = 7): FloatArray {
        val l = level.coerceIn(0, 3)
        val seconds = .55 + .25 * l
        var out = FloatArray(samples(seconds))
        val rnd = Random(seed + l)
        val noise = Noise(seed)
        // The fire's body: a low rumble that swells and settles.
        val low = Biquad().lowpass(380.0 + 120 * l)
        for (i in out.indices) out[i] += (low.process(noise.brown()) * (.25 + .12 * l) * swell(i.toDouble() / out.size)).toFloat()
        repeat(7 + 9 * l) {
            val at = (rnd.nextDouble() * (out.size - samples(.01))).toInt()
            out = mix(out, pop(rnd), at, .4 + rnd.nextDouble() * .6)
        }
        if (l > 0) out = mix(out, tone(pitch(-33.0 + l), .5, listOf(Partial(1.0, 1.0, .12), Partial(2.0, .3, .08))), 0, .5)
        return finish(out, .32 + .06 * l)
    }

    /** One crackle in a fire: a few milliseconds of bright noise. */
    fun pop(rnd: Random): FloatArray {
        val len = samples(.002 + rnd.nextDouble() * .006)
        val f = Biquad().bandpass(1400.0 + rnd.nextDouble() * 3200, 1.4)
        val noise = Noise(rnd.nextInt())
        return FloatArray(len) { i -> (f.process(noise.white()) * exp(-6.0 * i / len)).toFloat() }
    }

    /** A streak freeze melting into a missed day: a frosty hiss with a glint of ice. */
    fun freeze(): FloatArray {
        var out = band(1.3, { 6500.0 - 2500 * it }, .9, { t -> min(1.0, t * 20) * exp(-3.2 * t) }, seed = 11)
        val glints = listOf(pitch(31.0), pitch(34.0), pitch(38.0))
        glints.forEachIndexed { k, f -> out = mix(out, fmBell(f, .9, 1.41, 1.2, .1, .3), samples(.08 + .11 * k), .25) }
        return finish(reverb(out, wet = .3), .3)
    }

    // ---- Deleting ----------------------------------------------------------------------------------

    /** Petals blown away: a breezy whoosh that rises and falls. */
    fun whoosh() = finish(band(.85, { t -> 380 + 2300 * swell(t * .9) }, 1.3, { t -> swell(t).pow(1.6) }, seed = 5), .4)

    // ---- The garden game ---------------------------------------------------------------------------

    /** Glassy little pings scattered in time: the sparkle at the end of a big moment. */
    fun sparkle(count: Int, seconds: Double, seed: Int): FloatArray {
        var out = FloatArray(samples(seconds + .4))
        val rnd = Random(seed)
        repeat(count) {
            val note = pitch(27.0 + 12 * rnd.nextInt(2) + Pentatonic[rnd.nextInt(5)])
            out = mix(out, fmBell(note, .4, 3.5, 1.4, .05, .12), (rnd.nextDouble() * samples(seconds)).toInt(), .3 + rnd.nextDouble() * .4)
        }
        return out
    }

    /** A new level: a rising arpeggio of bells, a held chord and a shower of sparkle as the rosette spins. */
    fun levelUp(): FloatArray {
        var out = FloatArray(samples(2.8))
        val notes = listOf(3.0, 7.0, 10.0, 15.0, 19.0)
        notes.forEachIndexed { k, s -> out = mix(out, fmBell(pitch(s), 1.6, 2.0, 1.6, .25, .7), samples(.09 * k), .5) }
        val chordAt = samples(.09 * notes.size + .05)
        listOf(3.0, 7.0, 10.0, 15.0).forEach { s -> out = mix(out, tone(pitch(s), 1.8, listOf(Partial(1.0, 1.0, .8), Partial(2.0, .2, .4)), attack = .02), chordAt, .25) }
        out = mix(out, sparkle(12, 1.0, seed = 21), chordAt, .55)
        return finish(reverb(out, wet = .32, tail = 1.3), .62)
    }

    /** Petals landing: one ping each (up to a dozen), quickening into a trickle, so a big win sounds bigger. */
    fun petals(count: Int, seed: Int = 13): FloatArray {
        val n = count.coerceIn(1, 12)
        var out = FloatArray(samples(1.4))
        val rnd = Random(seed)
        var at = 0.0
        for (k in 0 until n) {
            val note = pitch(24.0 + 12 * (k % 2) + Pentatonic[rnd.nextInt(5)])
            out = mix(out, fmBell(note, .5, 3.5, 1.2, .04, .14), samples(at), .9 - .5 * k / n)
            at += .1 * .82.pow(k.toDouble()) + .025
        }
        return finish(reverb(out, wet = .25), .38)
    }

    /** A keepsake's sound, by season: wind chimes, a cicada shimmer, a leaf rustle or sleigh bells. */
    fun keepsake(season: Season): FloatArray = when (season) {
        Season.Spring -> {
            var out = FloatArray(samples(2.6))
            val rnd = Random(31)
            repeat(8) { k ->
                val note = pitch(15.0 + 12 * rnd.nextInt(2) + Pentatonic[rnd.nextInt(5)])
                out = mix(out, tone(note, 1.8, listOf(Partial(1.0, 1.0, .9), Partial(2.76, .4, .35), Partial(5.4, .15, .12))),
                    samples(k * .17 + rnd.nextDouble() * .08), .5 + rnd.nextDouble() * .5)
            }
            finish(reverb(out, wet = .35, tail = 1.0), .45)
        }
        Season.Summer -> {
            val n = samples(1.9); var out = FloatArray(n)
            val noise = Noise(41); val f = Biquad().bandpass(4600.0, 7.0)
            for (i in 0 until n) {
                val t = i.toDouble() / SampleRate
                val pulse = (.5 + .5 * sin(Tau * 46 * t)).pow(4)
                out[i] = (f.process(noise.white()) * pulse * swell(i.toDouble() / n)).toFloat()
            }
            listOf(3.0, 7.0, 10.0).forEach { s -> out = mix(out, tone(pitch(s - 12), 1.9, listOf(Partial(1.0, 1.0, 1.5)), attack = .4), 0, .12) }
            finish(out, .35)
        }
        Season.Autumn -> {
            var out = FloatArray(samples(1.7))
            val rnd = Random(51)
            repeat(70) {
                val t = rnd.nextDouble()
                val grain = band(.01 + rnd.nextDouble() * .03, { 1500.0 + 2500 * rnd.nextDouble() }, 1.2, { u -> swell(u) }, rnd.nextInt())
                out = mix(out, grain, (t * samples(1.3)).toInt(), swell(t) * (.3 + rnd.nextDouble() * .7))
            }
            listOf(-9.0, -5.0, -2.0, 3.0).forEachIndexed { k, s -> out = mix(out, pluck(pitch(s), 1.2, .5, seed = k), samples(.25 + .09 * k), .45) }
            finish(reverb(out, wet = .25), .4)
        }
        Season.Winter -> {
            var out = FloatArray(samples(1.9))
            val rnd = Random(61)
            for (k in 0 until 11) {
                val strength = if (k % 2 == 0) 1.0 else .55
                out = mix(out, jingle(rnd), samples(k * .11), strength * (1 - k / 16.0))
            }
            out = mix(out, fmBell(pitch(22.0), 1.4, 1.41, 1.5, .2, .6), samples(1.2), .35)
            finish(reverb(out, wet = .3), .4)
        }
    }

    /** One shake of sleigh bells: a cluster of bright, inharmonic tones with a scrape of noise. */
    private fun jingle(rnd: Random): FloatArray {
        var out = tone(2400.0 + rnd.nextDouble() * 200, .22, listOf(Partial(1.0, .6, .06), Partial(1.42, .5, .05), Partial(1.97, .4, .04), Partial(2.6, .25, .03)))
        out = mix(out, band(.04, { 7000.0 }, 1.0, { t -> 1 - t }, rnd.nextInt()), 0, .3)
        return out
    }

    // ---- The weekly story -------------------------------------------------------------------------

    /** Page one: the flower blooms to a rising harp and a chord swelling underneath. */
    fun storyBloom(): FloatArray {
        var out = FloatArray(samples(2.2))
        listOf(-21.0, -17.0, -14.0, -9.0, -5.0, -2.0, 3.0).forEachIndexed { k, s -> out = mix(out, pluck(pitch(s), 1.2, .55, seed = k), samples(.11 * k), .4) }
        listOf(-21.0, -14.0, -5.0).forEach { s -> out = mix(out, tone(pitch(s), 2.2, listOf(Partial(1.0, 1.0, 3.0), Partial(2.0, .2, 1.0)), attack = .9), 0, .18) }
        return finish(reverb(out, wet = .3, tail = 1.0), .5)
    }

    /** Page two: a snare roll building under the count, landing on a hit and a soft cymbal. */
    fun drumroll(seconds: Double = 1.1): FloatArray {
        var out = FloatArray(samples(seconds + 1.2))
        val rnd = Random(71)
        val strokes = (seconds * 26).toInt()
        for (k in 0 until strokes) {
            val t = k.toDouble() / strokes
            out = mix(out, snare(rnd, .55), samples(k / 26.0 + rnd.nextDouble() * .004), .15 + .55 * t * t)
        }
        val end = samples(seconds)
        out = mix(out, snare(rnd, 1.0), end, 1.0)
        out = mix(out, tone(pitch(-33.0), .5, listOf(Partial(1.0, 1.0, .18))), end, .7)
        out = mix(out, band(1.1, { 7500.0 }, .6, { t -> exp(-4 * t) }, 72), end, .45)
        return finish(reverb(out, wet = .18), .5)
    }

    private fun snare(rnd: Random, body: Double): FloatArray {
        var out = band(.06, { 2600.0 }, .7, { t -> exp(-7 * t) }, rnd.nextInt())
        out = mix(out, tone(190.0, .06, listOf(Partial(1.0, body, .025))), 0, .6)
        return out
    }

    /** Page three: a harp glissando, then a ping for each star as it pops in ([stars] timed to the page). */
    fun flourish(stars: Int): FloatArray {
        var out = FloatArray(samples(2.6))
        for (k in 0 until 10) out = mix(out, pluck(scaleNote(k), .9, .65, seed = 80 + k), samples(.03 * k), .4)
        for (i in 0 until stars.coerceIn(0, 7)) {
            out = mix(out, fmBell(scaleNote(5 + i), .7, 3.5, 1.2, .06, .2), samples(.3 + i * .16), .5)
        }
        return finish(reverb(out, wet = .3), .5)
    }

    /** Page four: the streak's fire, then a warm chord to close the week. */
    fun storyEnd(level: Int): FloatArray {
        var out = crackle(level)
        listOf(-33.0, -26.0, -21.0, -17.0).forEach { s -> out = mix(out, tone(pitch(s), 2.4, listOf(Partial(1.0, 1.0, 1.6), Partial(2.0, .25, .8)), attack = .5), samples(.3), .25) }
        return finish(reverb(out, wet = .25), .5)
    }

    // ---- The flower's voice -------------------------------------------------------------------------

    /**
     * A tiny voice: a buzzy tone at [pitchAt] through two vowel resonances ([vowelAt] gives the
     * first two formants), shaped by [ampAt]; all three are functions of the time in seconds.
     */
    fun voice(seconds: Double, pitchAt: (Double) -> Double, vowelAt: (Double) -> Pair<Double, Double>, ampAt: (Double) -> Double, breath: Double = .1, seed: Int = 90): FloatArray {
        val n = samples(seconds); val out = FloatArray(n)
        val f1 = Biquad(); val f2 = Biquad(); val noise = Noise(seed)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SampleRate
            val f0 = pitchAt(t) * (1 + .012 * sin(Tau * 5.5 * t))
            phase += Tau * f0 / SampleRate
            if (i % 32 == 0) { val (a, b) = vowelAt(t); f1.bandpass(a, 5.0); f2.bandpass(b, 8.0) }
            var src = 0.0
            var k = 1
            while (k <= 14 && k * f0 < SampleRate * .42) { src += sin(k * phase) / k; k++ }
            src += noise.white() * breath
            out[i] = ((f1.process(src) + .6 * f2.process(src)) * ampAt(t)).toFloat()
        }
        return out
    }

    private val Ee = 300.0 to 2350.0
    private val Ah = 760.0 to 1150.0
    private val Oh = 470.0 to 820.0
    private val Mm = 260.0 to 650.0

    /** A poke: "hee-hee-hee", tumbling down. */
    fun giggle(): FloatArray {
        val starts = listOf(0.0, .12, .24, .36)
        val pitches = listOf(930.0, 880.0, 820.0, 740.0)
        val out = voice(.56, { t ->
            val k = starts.indexOfLast { it <= t }.coerceAtLeast(0)
            pitches[k] * (1 + .08 * ((t - starts[k]) / .1))
        }, { Ee }, { t ->
            val k = starts.indexOfLast { it <= t }.coerceAtLeast(0)
            val u = (t - starts[k]) / .095
            if (u > 1) 0.0 else sin(PI * u).pow(.6)
        }, breath = .25)
        return finish(out, .4)
    }

    /** Woken from a nap: a big yawn, "aaah... mm". */
    fun yawn(): FloatArray {
        val out = voice(1.5, { t -> 470 - 220 * (t / 1.5) + 40 * sin(PI * min(1.0, t / .3)) }, { t ->
            when {
                t < .7 -> Ah
                t < 1.1 -> lerp(Ah, Oh, (t - .7) / .4)
                else -> lerp(Oh, Mm, (t - 1.1) / .3)
            }
        }, { t -> min(1.0, t / .25) * (if (t > 1.2) max(0.0, 1 - (t - 1.2) / .3) else 1.0) * (if (t > 1.1) .55 else 1.0) }, breath = .35)
        return finish(out, .4)
    }

    /** Spun dizzy: "wheeee!", up and over. */
    fun wheee(): FloatArray {
        val out = voice(1.3, { t -> if (t < .55) 460 + 600 * (t / .55) else 1060 - 420 * ((t - .55) / .75) }, { Ee }, { t ->
            min(1.0, t / .08) * (1 - ((t - .9) / .4).coerceIn(0.0, 1.0))
        }, breath = .2)
        return finish(out, .42)
    }

    private fun lerp(a: Pair<Double, Double>, b: Pair<Double, Double>, t: Double): Pair<Double, Double> {
        val u = t.coerceIn(0.0, 1.0)
        return (a.first + (b.first - a.first) * u) to (a.second + (b.second - a.second) * u)
    }

    // ---- Focus and reminders ------------------------------------------------------------------------

    /** The end of a focus session: a singing bowl. */
    fun focusBell() = finish(reverb(bowl(pitch(-9.0)), wet = .3, tail = 1.5), .5)

    /** Reminder sounds, rendered once into the app's resources (see SoundRenderTest). */
    fun reminderChime(): FloatArray {
        var out = FloatArray(samples(1.6))
        listOf(15.0, 19.0, 22.0).forEachIndexed { k, s -> out = mix(out, fmBell(pitch(s), 1.3, 2.0, 1.4, .2, .5), samples(.12 * k), .5) }
        return finish(reverb(out, wet = .3, tail = .5), .8)
    }

    fun reminderBirds(): FloatArray {
        val rnd = Random(101)
        var out = FloatArray(samples(1.5))
        out = mix(out, chirpPhrase(rnd, 4), 0, 1.0)
        out = mix(out, chirpPhrase(rnd, 3), samples(.75), .8)
        return finish(reverb(out, wet = .2, tail = .4), .8)
    }

    fun reminderKnock(): FloatArray {
        var out = FloatArray(samples(.9))
        for (k in 0 until 2) {
            val knock = mix(tone(165.0, .25, listOf(Partial(1.0, 1.0, .05), Partial(2.3, .4, .02))),
                band(.05, { 900.0 }, 1.0, { t -> exp(-8 * t) }, 110 + k), 0, .6)
            out = mix(out, knock, samples(.19 * k), 1.0)
        }
        return finish(reverb(out, wet = .15, size = .6, tail = .3), .85)
    }

    /** The evening streak nudge: two soft notes, a little worried, never alarming. */
    fun streakNudge(): FloatArray {
        var out = FloatArray(samples(1.5))
        out = mix(out, bar(pitch(10.0), 1.0), 0, 1.0)
        out = mix(out, bar(pitch(7.0), 1.1), samples(.32), .9)
        return finish(reverb(out, wet = .25, tail = .4), .8)
    }

    // ---- Birds, bugs and water ------------------------------------------------------------------------

    /** A bird's phrase: a few chirps, each a quick swoop or trill. */
    fun chirpPhrase(rnd: Random, chirps: Int = 2 + rnd.nextInt(5)): FloatArray {
        var out = FloatArray(samples(1.0))
        var at = 0.0
        val base = 2600.0 + rnd.nextDouble() * 1600
        repeat(chirps) {
            val len = .04 + rnd.nextDouble() * .08
            val kind = rnd.nextInt(3)
            val n = samples(len); val chirp = FloatArray(n)
            var phase = 0.0
            for (i in 0 until n) {
                val u = i.toDouble() / n
                val f = when (kind) {
                    0 -> base * (1 + .35 * u)
                    1 -> base * (1.3 - .4 * u)
                    else -> base * (1 + .08 * sin(Tau * 30 * i / SampleRate))
                }
                phase += Tau * f / SampleRate
                chirp[i] = (sin(phase) * sin(PI * u).pow(.8)).toFloat()
            }
            out = mix(out, chirp, samples(at), .5 + rnd.nextDouble() * .5)
            at += len + .03 + rnd.nextDouble() * .06
        }
        return out
    }

    /** One bubble in the pond: a tiny upward blip. */
    fun bubble(rnd: Random): FloatArray {
        val n = samples(.04 + rnd.nextDouble() * .04); val out = FloatArray(n)
        val f0 = 380.0 + rnd.nextDouble() * 300
        var phase = 0.0
        for (i in 0 until n) {
            val u = i.toDouble() / n
            phase += Tau * f0 * (1 + 1.8 * u) / SampleRate
            out[i] = (sin(phase) * exp(-4 * u) * min(1.0, u * 30)).toFloat()
        }
        return out
    }

    /** One cricket's chirp: three quick pulses of a high tone. */
    fun cricket(freq: Double): FloatArray {
        val pulse = .018; val gap = .033
        val n = samples(gap * 2 + pulse); val out = FloatArray(n)
        val w = Tau * freq / SampleRate
        for (i in 0 until n) {
            val t = i.toDouble() / SampleRate
            val k = (t / gap).toInt(); val u = (t - k * gap) / pulse
            if (k < 3 && u in 0.0..1.0) out[i] = (sin(w * i) * sin(PI * u)).toFloat()
        }
        return out
    }

    /** A raindrop on a leaf: a click of bright noise. */
    fun drop(rnd: Random) = band(.004 + rnd.nextDouble() * .006, { 3000.0 + rnd.nextDouble() * 3500 }, 2.0, { t -> exp(-5 * t) }, rnd.nextInt())

    /** A cup set down somewhere across the café. */
    fun clink(rnd: Random) = fmBell(2300.0 + rnd.nextDouble() * 900, .35, 2.43, 1.0, .03, .09)
}

/**
 * A long sound that goes on as long as it is wanted: an even background ([base]) with things
 * happening in it from time to time ([event], every [gap] seconds or so). Its loudness glides to
 * whatever [fadeTo] asks, and once it has faded out after [stop] it is finished.
 */
internal abstract class Bed(seed: Int) : Voice {
    protected val rnd = Random(seed)
    protected val noise = Noise(seed)
    private val events = mutableListOf<IntArray>() // [position] holders, paired with buffers below
    private val buffers = mutableListOf<Pair<FloatArray, Float>>()
    private var untilNext = 0
    @Volatile private var target = 0f
    @Volatile private var stopping = false
    private var level = 0f
    private var step = 0f
    protected var clock = 0L

    fun fadeTo(gain: Float, seconds: Double) {
        target = gain
        step = abs(gain - level) / samples(seconds).toFloat()
    }
    fun stop(seconds: Double = .8) { stopping = true; fadeTo(0f, seconds) }

    /** The steady part of the sound, one sample at a time. */
    protected open fun base(): Double = 0.0
    /** Seconds until the next event; [Double.POSITIVE_INFINITY] for none. */
    protected open fun gap(): Double = Double.POSITIVE_INFINITY
    /** The next event to play, and how loud. */
    protected open fun event(): Pair<FloatArray, Float>? = null

    override fun render(out: FloatArray, from: Int, n: Int): Boolean {
        for (i in 0 until n) {
            if (level != target) level = if (level < target) min(target, level + step) else max(target, level - step)
            if (untilNext <= 0) {
                event()?.let { events += intArrayOf(0); buffers += it }
                val g = gap()
                untilNext = if (g.isInfinite()) Int.MAX_VALUE else samples(g)
            }
            untilNext--
            var s = base()
            var k = 0
            while (k < events.size) {
                val (data, gain) = buffers[k]
                val at = events[k][0]
                if (at >= data.size) { events.removeAt(k); buffers.removeAt(k); continue }
                s += data[at] * gain
                events[k][0] = at + 1
                k++
            }
            out[from + i] += (s * level).toFloat()
            clock++
        }
        return !(stopping && level <= 0f)
    }
}

/** Several beds playing as one, so a whole scene fades in and out together. */
internal class Layers(private val beds: List<Bed>) : Bed(0) {
    private val scratch = FloatArray(1)
    override fun base(): Double {
        scratch[0] = 0f
        beds.forEach { it.render(scratch, 0, 1) }
        return scratch[0].toDouble()
    }
    init { beds.forEach { it.fadeTo(1f, .01) } }
}

/** Birdsong: phrases every few seconds, more often at dawn ([busy] above 1). */
internal class Birds(seed: Int, private val busy: Double = 1.0, private val volume: Float = .5f) : Bed(seed) {
    override fun gap() = (1.2 + rnd.nextDouble() * 4.5) / busy
    override fun event() = Synth.chirpPhrase(rnd) to volume * (.4f + rnd.nextFloat() * .6f)
}

/** Wind: rumbling noise through a band that wanders, in slow gusts; stronger in winter. */
internal class Wind(seed: Int, private val strength: Double = 1.0) : Bed(seed) {
    private val f = Biquad()
    override fun base(): Double {
        val t = clock.toDouble() / SampleRate
        if (clock % 64 == 0L) f.bandpass(330 + 220 * sin(Tau * .05 * t) + 90 * sin(Tau * .13 * t + 1), .8)
        val gust = .55 + .45 * sin(Tau * .07 * t).pow(2)
        return f.process(noise.brown()) * .9 * gust * strength
    }
}

/** A pond: a low trickle with bubbles rising now and then. */
internal class Pond(seed: Int) : Bed(seed) {
    private val f = Biquad().lowpass(700.0)
    override fun base() = f.process(noise.brown()) * .25
    override fun gap() = .15 + rnd.nextDouble() * .5
    override fun event() = Synth.bubble(rnd) to (.1f + rnd.nextFloat() * .15f)
}

/** Crickets: two of them, a little apart in pitch and time. */
internal class Crickets(seed: Int, private val busy: Double = 1.0) : Bed(seed) {
    private val pitches = doubleArrayOf(4250.0 + rnd.nextDouble() * 150, 4600.0 + rnd.nextDouble() * 200)
    override fun gap() = (.25 + rnd.nextDouble() * .4) / busy
    override fun event() = Synth.cricket(pitches[rnd.nextInt(2)]) to (.1f + rnd.nextFloat() * .1f)
}

/** A warm afternoon: a soft chord that breathes slowly, with the faintest room tone under it. */
internal class Hum(seed: Int) : Bed(seed) {
    private val freqs = doubleArrayOf(130.81, 196.0, 261.63 * 1.003, 329.63)
    private val f = Biquad().lowpass(400.0)
    override fun base(): Double {
        val t = clock.toDouble() / SampleRate
        var s = 0.0
        freqs.forEachIndexed { k, fr -> s += sin(Tau * fr * t) * (.5 + .5 * sin(Tau * (.05 + .013 * k) * t + k)) }
        return s * .06 + f.process(noise.brown()) * .08
    }
}

/** Rain: soft pink noise with drops landing on leaves. */
internal class Rain(seed: Int) : Bed(seed) {
    private val high = Biquad().highpass(450.0); private val low = Biquad().lowpass(6500.0)
    override fun base() = low.process(high.process(noise.pink())) * .9
    override fun gap() = .01 + rnd.nextDouble() * .05
    override fun event() = Synth.drop(rnd) to (.04f + rnd.nextFloat() * .1f)
}

/** A café: a murmur of voices that rises and falls, and a cup set down now and then. */
internal class Cafe(seed: Int) : Bed(seed) {
    private val f = Biquad().bandpass(520.0, .7)
    private var swell = .6
    override fun base(): Double {
        if (clock % 256 == 0L) swell = (swell + (rnd.nextDouble() - .5) * .06).coerceIn(.35, 1.0)
        return f.process(noise.brown() + noise.pink() * .3) * 1.3 * swell
    }
    override fun gap() = 2.5 + rnd.nextDouble() * 6
    override fun event() = Synth.clink(rnd) to (.05f + rnd.nextFloat() * .06f)
}

/** A fireplace: a low roar with crackles. */
internal class Fire(seed: Int) : Bed(seed) {
    private val f = Biquad().lowpass(260.0)
    override fun base() = f.process(noise.brown()) * .8
    override fun gap() = .03 + rnd.nextDouble() * .28
    override fun event() = Synth.pop(rnd) to (.1f + rnd.nextFloat() * .35f)
}

/** Plain brown noise: deep and even, for shutting the world out. */
internal class Brown(seed: Int) : Bed(seed) {
    override fun base() = noise.brown() * .55
}

/**
 * The breathing guide: a tone that rises as you breathe in for [inhale] seconds and sinks as you
 * breathe out for [exhale], in step with the flower on screen.
 */
internal class Breath(seed: Int, private val inhale: Double = BreathIn, private val exhale: Double = BreathOut) : Bed(seed) {
    private var phase = 0.0; private var phase2 = 0.0
    override fun base(): Double {
        val t = (clock.toDouble() / SampleRate) % (inhale + exhale)
        val rise = if (t < inhale) .5 - .5 * cos(PI * t / inhale) else .5 + .5 * cos(PI * (t - inhale) / exhale)
        val f = 196.0 * (1 + .5 * rise)
        phase += Tau * f / SampleRate; phase2 += Tau * f * 1.5 / SampleRate
        return (sin(phase) + .3 * sin(phase2)) * (.12 + .5 * rise) * .5
    }
}

internal const val BreathIn = 4.0
internal const val BreathOut = 6.0

/** How far into a breath [seconds] is, 0 (empty) to 1 (full): the same curve the tone follows. */
internal fun breathAt(seconds: Double): Double {
    val t = seconds % (BreathIn + BreathOut)
    return if (t < BreathIn) .5 - .5 * cos(PI * t / BreathIn) else .5 + .5 * cos(PI * (t - BreathIn) / BreathOut)
}

/** Whether a breath is on its way in at [seconds]. */
internal fun breathingIn(seconds: Double) = seconds % (BreathIn + BreathOut) < BreathIn

/**
 * The hum that follows a swipe: it rises with the row as it is dragged; let go short and it slides
 * back down, let go past the line and it gets out of the way of the pluck.
 */
internal class SwipeHum : Voice {
    @Volatile var reach = 0.0
    @Volatile private var released = false
    @Volatile private var committed = false
    private var pitch = 180.0; private var level = 0.0; private var phase = 0.0; private var t = 0
    private var after = 0

    fun release(commit: Boolean) { committed = commit; released = true }

    override fun render(out: FloatArray, from: Int, n: Int): Boolean {
        for (i in 0 until n) {
            val wantPitch = if (released && !committed) 150.0 else 180.0 * 2.0.pow(reach.coerceIn(0.0, 1.2))
            val wantLevel = if (released) 0.0 else .08 + .22 * reach.coerceIn(0.0, 1.0)
            val glide = if (released && !committed) .0004 else .004
            pitch += (wantPitch - pitch) * glide
            level += (wantLevel - level) * (if (released && committed) .01 else .002)
            phase += Tau * pitch * (1 + .004 * sin(Tau * 5 * t / SampleRate)) / SampleRate
            out[from + i] += ((sin(phase) + .25 * sin(2 * phase) + .08 * sin(3 * phase)) * level).toFloat()
            t++
            if (released) after++
        }
        return !(released && (level < .0005 && after > samples(.05)) || after > samples(1.0))
    }
}

/** The loudness of a buffer, as the root mean square of its samples. */
internal fun rms(buffer: FloatArray): Double = if (buffer.isEmpty()) 0.0 else sqrt(buffer.sumOf { it.toDouble() * it } / buffer.size)

/** Decibels for a gain: handy for comparing loudness in tests. */
internal fun decibels(gain: Double) = 20 * ln(gain.coerceAtLeast(1e-9)) / ln(10.0)
