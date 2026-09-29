package com.forma.habits

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class LightThemeTest {
    private fun contrast(a: Color, b: Color): Float {
        val first = a.luminance()
        val second = b.luminance()
        return (maxOf(first, second) + .05f) / (minOf(first, second) + .05f)
    }

    @Test fun textRemainsReadableOnEveryLightSurface() {
        val palette = LightPalette
        (palette.tiles + palette.cream + palette.surface).forEach { background ->
            assertTrue("Primary text on $background", contrast(palette.ink, background) >= 4.5f)
            assertTrue("Secondary text on $background", contrast(palette.quiet, background) >= 4.5f)
        }
        assertTrue(contrast(palette.onAccent, palette.purple) >= 4.5f)
    }

    @Test fun controlsStayDistinctOnNeutralSurfaces() {
        listOf(LightPalette.cream, LightPalette.surface).forEach { background ->
            assertTrue(contrast(LightControlOutline, background) >= 3f)
            assertTrue(contrast(LightPalette.accent, background) >= 3f)
        }
    }

    @Test fun largeSurfacesAvoidPureWhiteGlare() {
        assertTrue(LightPalette.cream.luminance() < .85f)
        assertTrue(LightPalette.surface.luminance() < .92f)
        assertTrue(LightPalette.surface.luminance() > LightPalette.cream.luminance())
    }
}
