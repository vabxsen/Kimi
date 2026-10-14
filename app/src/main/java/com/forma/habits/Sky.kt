package com.forma.habits

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.sin

/**
 * The sky behind Today: a soft wash that follows the clock, pink at dawn, blue by day, gold and
 * orange towards evening, and a starry violet at night, with a few clouds drifting across. It fades
 * into the page, so it tints the top of the screen rather than sitting there as a block.
 *
 * Light themes keep it pale enough for dark text at every hour; dark themes keep it deep. The hour
 * is read on every frame it draws, so it moves on by itself and costs nothing while off screen.
 */

/** One stop in the day: the minute it is reached, the colour at the top and lower down. */
internal class SkyStop(val minute: Int, val top: Color, val low: Color)

private fun stops(night: Pair<Long, Long>, dawn: Pair<Long, Long>, morning: Pair<Long, Long>, noon: Pair<Long, Long>,
    golden: Pair<Long, Long>, dusk: Pair<Long, Long>, twilight: Pair<Long, Long>): List<SkyStop> {
    fun stop(minute: Int, c: Pair<Long, Long>) = SkyStop(minute, Color(c.first), Color(c.second))
    return listOf(stop(0, night), stop(300, night), stop(390, dawn), stop(540, morning), stop(780, noon),
        stop(1020, golden), stop(1125, dusk), stop(1215, twilight), stop(1290, night), stop(1440, night))
}

internal val LightSky = stops(
    night = 0xFFCBC8EE to 0xFFE4E0F6, dawn = 0xFFFFC6D0 to 0xFFFFE1C6, morning = 0xFFC6E5FA to 0xFFE6F3FC,
    noon = 0xFFB5DDF8 to 0xFFE0F0FC, golden = 0xFFFFDDAE to 0xFFFDEBD3, dusk = 0xFFFFBC98 to 0xFFF8D3C8,
    twilight = 0xFFD3C2EE to 0xFFE9DFF6)

internal val DarkSky = stops(
    night = 0xFF151A3E to 0xFF1C1F45, dawn = 0xFF4B2D4F to 0xFF3A2843, morning = 0xFF1E3A58 to 0xFF20314A,
    noon = 0xFF1C4468 to 0xFF20364F, golden = 0xFF4A3726 to 0xFF33281F, dusk = 0xFF5B2E2B to 0xFF3B2527,
    twilight = 0xFF33285C to 0xFF261E42)

/** The sky's two colours at [minute] past midnight, blended smoothly between the stops either side. */
internal fun skyAt(minute: Float, stops: List<SkyStop>): Pair<Color, Color> {
    val m = ((minute % 1440f) + 1440f) % 1440f
    val after = stops.indexOfFirst { it.minute > m }.coerceAtLeast(1)
    val a = stops[after - 1]; val b = stops[after]
    val t = ((m - a.minute) / (b.minute - a.minute)).coerceIn(0f, 1f)
    // Smoothstep, so each hour eases into the next instead of turning a corner at every stop.
    val s = t * t * (3f - 2f * t)
    return lerp(a.top, b.top, s) to lerp(a.low, b.low, s)
}

/** 1 through the night, 0 through the day, easing across dawn and dusk: how much of the stars show. */
internal fun nightness(minute: Float): Float {
    val m = ((minute % 1440f) + 1440f) % 1440f
    fun ease(from: Float, to: Float, x: Float): Float { val t = ((x - from) / (to - from)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
    return when {
        m < 720f -> 1f - ease(300f, 420f, m)
        else -> ease(1170f, 1290f, m)
    }
}

private fun grey(c: Color): Color { val l = c.luminance(); return Color(l, l, l, c.alpha) }

/** The sky as [palette] paints it at [minute]: a little of the page colour in every hour keeps it in step with the theme. */
internal fun skyColors(minute: Float, palette: KimiPalette): Pair<Color, Color> {
    val (top, low) = skyAt(minute, if (palette.dark) DarkSky else LightSky)
    val tinted = lerp(top, palette.cream, .12f) to lerp(low, palette.cream, .3f)
    return if (palette.mono) grey(tinted.first) to grey(tinted.second) else tinted
}

private const val CloudLoopMillis = 120_000
private const val SkyTwinkleMillis = 6_000

/** Where a cloud floats, as fractions of the sky, how big it is, how many times it crosses per loop, and where it starts. */
private class Cloud(val y: Float, val scale: Float, val laps: Int, val phase: Float)
private val Clouds = listOf(Cloud(.16f, 1f, 1, .1f), Cloud(.33f, .66f, 2, .55f), Cloud(.5f, .82f, 1, .72f))

/** Star positions as fractions of the sky, with a twinkle offset each. */
private val SkyStars = listOf(
    Triple(.06f, .08f, .1f), Triple(.18f, .22f, .6f), Triple(.27f, .05f, .3f), Triple(.38f, .3f, .85f),
    Triple(.52f, .12f, .45f), Triple(.61f, .36f, .15f), Triple(.72f, .07f, .7f), Triple(.83f, .25f, .4f),
    Triple(.93f, .14f, .9f), Triple(.12f, .4f, .25f), Triple(.45f, .46f, .65f), Triple(.88f, .44f, .05f)
)

@Composable fun LivingSky(modifier: Modifier = Modifier, now: () -> LocalTime = LocalTime::now) {
    val reduced = reducedMotion()
    val transition = if (!reduced) rememberInfiniteTransition(label = "living sky") else null
    val drift = transition?.animateFloat(0f, 1f, infiniteRepeatable(tween(CloudLoopMillis, easing = LinearEasing)), label = "clouds")
    val twinkle = transition?.animateFloat(0f, 1f, infiniteRepeatable(tween(SkyTwinkleMillis, easing = LinearEasing)), label = "twinkle")
    val palette = LocalKimiPalette.current
    val dark = palette.dark
    val starColor = if (dark) Color(0xFFF3E9C6) else lerp(palette.accent, palette.cream, .2f)
    Canvas(modifier) {
        val time = now()
        val minute = time.hour * 60f + time.minute + time.second / 60f
        val (top, low) = skyColors(minute, palette)
        drawRect(Brush.verticalGradient(0f to top, .5f to low, 1f to low.copy(alpha = 0f)))
        val night = nightness(minute)
        if (night > 0f) {
            val t = twinkle?.value ?: 0f
            SkyStars.forEach { (x, y, phase) ->
                // Whole-number cycles per loop, so the twinkle never jumps when it starts over.
                val glow = .35f + .65f * (.5f + .5f * sin(2f * PI.toFloat() * (t * 3f + phase)))
                drawSparkle((if (palette.mono) grey(starColor) else starColor).copy(alpha = night * glow * (1f - y)),
                    Offset(size.width * x, size.height * y), 2.6.dp.toPx() * (.7f + .3f * glow))
            }
        }
        val cloudAlpha = if (dark) .4f - .15f * night else .45f - .28f * night
        // At dawn and dusk the clouds catch the colour of the sky. In the dark they are the sky's own
        // colour lifted a little: a faint white there only greys it, and reads as smoke.
        val cloudColor = if (dark) lerp(top, Color.White, .22f) else lerp(Color.White, top, .25f + .2f * night)
        Clouds.forEach { cloud ->
            val width = 96.dp.toPx() * cloud.scale
            val lap = ((drift?.value ?: 0f) * cloud.laps + cloud.phase) % 1f
            val x = -width + (size.width + width * 2f) * lap
            drawCloud(cloudColor.copy(alpha = cloudAlpha), Offset(x, size.height * cloud.y), width)
        }
    }
}

/**
 * A cloud one unit wide, centred on the origin: a flat base with three rounded bumps. Merged into a
 * single outline, so a see-through cloud has no darker seams where its parts overlap.
 */
private val UnitCloud: Path by lazy {
    val r = 1f / 4.6f
    fun oval(cx: Float, cy: Float, radius: Float) = Path().apply { addOval(Rect(Offset(cx, cy), radius)) }
    val base = Path().apply { addRoundRect(RoundRect(Rect(-.5f, -r * .2f, .5f, r), CornerRadius(r * .6f))) }
    listOf(oval(-.2f, 0f, r * 1.05f), oval(.05f, -r * .35f, r * 1.45f), oval(.28f, r * .1f, r))
        .fold(base) { cloud, bump -> Path().apply { op(cloud, bump, PathOperation.Union) } }
}

/** A cloud [width] wide, centred on [at]. */
private fun DrawScope.drawCloud(color: Color, at: Offset, width: Float) {
    withTransform({ translate(at.x, at.y); scale(width, width, Offset.Zero) }) { drawPath(UnitCloud, color) }
}
