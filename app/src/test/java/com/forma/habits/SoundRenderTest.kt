package com.forma.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Renders every sound Kimi makes and checks it is well formed: never clipping, never silent,
 * starting and ending without a click, and as loud as it was designed to be. Each one is also
 * written out as a WAV under `app/build/sound-previews`, for listening to on a computer; the
 * reminder sounds in `res/raw` are copied from there.
 */
class SoundRenderTest {
    private val out = File("build/sound-previews").apply { mkdirs() }

    private val cues: Map<String, FloatArray> by lazy {
        buildMap {
            for (s in 0 until Synth.ScaleSteps) put("tick_%02d".format(s), Synth.tick(s))
            put("untick_03", Synth.untick(3))
            put("day_done_4", Synth.dayDone(4)); put("day_done_9", Synth.dayDone(9))
            put("swipe_catch", Synth.swipeCatch())
            for (l in 0..3) put("crackle_$l", Synth.crackle(l))
            put("freeze", Synth.freeze()); put("whoosh", Synth.whoosh())
            put("level_up", Synth.levelUp()); put("petals_3", Synth.petals(3)); put("petals_12", Synth.petals(12))
            Season.entries.forEach { put("keepsake_${it.name.lowercase()}", Synth.keepsake(it)) }
            put("story_bloom", Synth.storyBloom()); put("story_drumroll", Synth.drumroll())
            put("story_flourish_3", Synth.flourish(3)); put("story_end", Synth.storyEnd(2))
            put("mascot_giggle", Synth.giggle()); put("mascot_yawn", Synth.yawn()); put("mascot_wheee", Synth.wheee())
            put("focus_bell", Synth.focusBell())
            put("reminder_chime", Synth.reminderChime()); put("reminder_birds", Synth.reminderBirds())
            put("reminder_knock", Synth.reminderKnock()); put("streak_nudge", Synth.streakNudge())
        }
    }

    @Test fun everyCueIsCleanAndAudible() {
        cues.forEach { (name, data) ->
            assertTrue(name, data.size > samples(.1))
            assertTrue("$name has NaN", data.none { it.isNaN() })
            val peak = data.maxOf { abs(it) }
            assertTrue("$name peaks at $peak", peak in .25f..1f)
            assertTrue("$name is near silent", rms(data) > .01)
            assertTrue("$name starts with a click", abs(data.first()) < .01f)
            assertTrue("$name ends with a click", abs(data.last()) < .01f)
            write(name, data)
        }
    }

    @Test fun theTicksClimbTheScale() {
        val pitches = (0 until Synth.ScaleSteps).map { Synth.scaleNote(it) }
        assertEquals(pitches.sorted(), pitches)
        assertEquals(pitches.size, pitches.distinct().size)
        // Past the top of the tune, extra ticks hold the top note.
        assertEquals(pitches.last(), Synth.scaleNote(40), 0.0)
    }

    @Test fun bedsPlayEvenlyAndFadeOut() {
        val beds = mapOf<String, () -> Bed>(
            "birds" to { Birds(1) }, "wind" to { Wind(2) }, "pond" to { Pond(3) }, "crickets" to { Crickets(4) },
            "hum" to { Hum(5) }, "rain" to { Rain(6) }, "cafe" to { Cafe(7) }, "fire" to { Fire(8) },
            "brown" to { Brown(9) }, "breath" to { Breath(10) },
            "garden_full" to { Layers(listOf(Wind(11, .5), Birds(12), Pond(13), Crickets(14))) }
        )
        beds.forEach { (name, make) ->
            val bed = make()
            bed.fadeTo(1f, .2)
            val buffer = FloatArray(samples(12.0))
            var at = 0
            while (at < buffer.size) { val n = minOf(512, buffer.size - at); bed.render(buffer, at, n); at += n }
            val peak = buffer.maxOf { abs(it) }
            val loud = rms(buffer.copyOfRange(samples(1.0), buffer.size))
            println("bed %-12s rms %.3f (%.1f dB) peak %.2f".format(name, loud, decibels(loud), peak))
            assertTrue("$name peaks at $peak", peak < 1f)
            assertTrue("$name rms $loud", loud in .015..0.3)
            write("bed_$name", buffer)
            bed.stop(.3)
            val tail = FloatArray(samples(1.0))
            var playing = true
            at = 0
            while (playing && at < tail.size) { playing = bed.render(tail, at, 512.coerceAtMost(tail.size - at)); at += 512 }
            assertTrue("$name never finished", !playing)
        }
    }

    @Test fun theSwipeHumRisesAndSlidesAwayWhenLetGo() {
        val hum = SwipeHum()
        val buffer = FloatArray(samples(2.0))
        var at = 0
        while (at < samples(1.0)) { hum.reach = at.toDouble() / samples(1.0); hum.render(buffer, at, 512); at += 512 }
        hum.release(commit = false)
        var playing = true
        while (playing && at < buffer.size) { playing = hum.render(buffer, at, 512.coerceAtMost(buffer.size - at)); at += 512 }
        assertTrue("hum never stopped", !playing)
        assertTrue(rms(buffer) > .01)
        write("swipe_hum", Synth.finish(buffer, .4))
    }

    @Test fun theBreathingGuideFollowsTheBreath() {
        assertEquals(0.0, breathAt(0.0), 1e-9)
        assertEquals(1.0, breathAt(BreathIn), 1e-9)
        assertEquals(0.0, breathAt(BreathIn + BreathOut), 1e-9)
        assertTrue(breathingIn(1.0)); assertTrue(!breathingIn(BreathIn + 1))
    }

    /** 16-bit mono WAV. */
    private fun write(name: String, data: FloatArray) {
        DataOutputStream(FileOutputStream(File(out, "$name.wav"))).use { o ->
            fun le32(v: Int) { o.write(v and 255); o.write(v shr 8 and 255); o.write(v shr 16 and 255); o.write(v shr 24 and 255) }
            fun le16(v: Int) { o.write(v and 255); o.write(v shr 8 and 255) }
            o.writeBytes("RIFF"); le32(36 + data.size * 2); o.writeBytes("WAVEfmt ")
            le32(16); le16(1); le16(1); le32(SampleRate); le32(SampleRate * 2); le16(2); le16(16)
            o.writeBytes("data"); le32(data.size * 2)
            data.forEach { le16((it.coerceIn(-1f, 1f) * 32767).roundToInt()) }
        }
    }
}
