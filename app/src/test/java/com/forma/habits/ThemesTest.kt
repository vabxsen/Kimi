package com.forma.habits

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every colour theme, in light and in dark, has to stay as readable as the original one. */
class ThemesTest {
    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }

    private val palettes = KimiTheme.entries.flatMap { theme -> listOf(false, true).map { dark -> "$theme ${if (dark) "dark" else "light"}" to paletteFor(theme, dark) } }

    @Test fun textIsReadableOnEveryCardInEveryTheme() {
        palettes.forEach { (name, p) ->
            (p.tiles + p.cream + p.surface).forEach { background ->
                assertTrue("$name: primary text on $background", contrast(p.ink, background) >= 4.5f)
                assertTrue("$name: secondary text on $background", contrast(p.quiet, background) >= 4.5f)
            }
        }
    }

    @Test fun accentFillsAndControlsStandOut() {
        palettes.forEach { (name, p) ->
            assertTrue("$name: button text on the accent fill", contrast(p.onAccent, p.purple) >= 4.5f)
            // The big highlighted numbers and the selected-day dot sit on the accent fill.
            assertTrue("$name: highlight on the accent fill", contrast(p.highlight, p.purple) >= 3f)
            listOf(p.cream, p.surface).forEach { background ->
                assertTrue("$name: accent controls on $background", contrast(p.accent, background) >= 3f)
            }
        }
    }

    @Test fun eachThemeHasALightAndADarkSide() {
        KimiTheme.entries.forEach { theme ->
            assertEquals(false, paletteFor(theme, false).dark)
            assertEquals(true, paletteFor(theme, true).dark)
            assertTrue("$theme dark background is darker", paletteFor(theme, true).cream.luminance() < paletteFor(theme, false).cream.luminance())
        }
        assertEquals(LightPalette, paletteFor(KimiTheme.Kimi, false))
        assertTrue(paletteFor(KimiTheme.Monochrome, false).mono && paletteFor(KimiTheme.Monochrome, true).mono)
    }
}
