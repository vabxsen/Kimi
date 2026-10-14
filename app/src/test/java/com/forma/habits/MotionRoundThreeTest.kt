package com.forma.habits

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The living sky and the growing flame: readable at every hour, smooth round the clock, tiers in the right places. */
class MotionRoundThreeTest {
    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }

    private val palettes = KimiTheme.entries.flatMap { theme -> listOf(false, true).map { dark -> "$theme ${if (dark) "dark" else "light"}" to paletteFor(theme, dark) } }

    @Test fun greetingStaysReadableOnTheSkyAtEveryHourInEveryTheme() {
        palettes.forEach { (name, p) ->
            for (minute in 0 until 1440 step 10) {
                val (top, low) = skyColors(minute.toFloat(), p)
                assertTrue("$name at $minute: text on top of sky", contrast(p.ink, top) >= 4.5f)
                assertTrue("$name at $minute: text lower in sky", contrast(p.ink, low) >= 4.5f)
                assertTrue("$name at $minute: quiet text lower in sky", contrast(p.quiet, low) >= 4.5f)
            }
        }
    }

    @Test fun skyJoinsUpAtMidnightAndNeverJumps() {
        listOf(LightSky, DarkSky).forEach { stops ->
            val (lateTop, _) = skyAt(1439.99f, stops)
            val (earlyTop, _) = skyAt(0f, stops)
            assertEquals(earlyTop.luminance(), lateTop.luminance(), .001f)
            var previous = skyAt(0f, stops).first
            for (step in 1..1440) {
                val now = skyAt(step.toFloat(), stops).first
                assertTrue("jump at minute $step", kotlin.math.abs(now.luminance() - previous.luminance()) < .02f)
                previous = now
            }
        }
    }

    @Test fun starsShowAtNightOnlyAndFadeAcrossDawnAndDusk() {
        assertEquals(1f, nightness(120f), 0f)
        assertEquals(0f, nightness(720f), 0f)
        assertEquals(1f, nightness(1380f), 0f)
        val dawn = nightness(360f)
        assertTrue(dawn > 0f && dawn < 1f)
        val dusk = nightness(1230f)
        assertTrue(dusk > 0f && dusk < 1f)
    }

    @Test fun flameGrowsAtTheStreakMilestones() {
        assertEquals(0, flameTier(0))
        assertEquals(0, flameTier(6))
        assertEquals(1, flameTier(7))
        assertEquals(1, flameTier(29))
        assertEquals(2, flameTier(30))
        assertEquals(2, flameTier(99))
        assertEquals(3, flameTier(100))
        assertEquals(3, flameTier(365))
    }
}
