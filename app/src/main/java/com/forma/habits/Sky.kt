package com.forma.habits

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
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

@Composable fun LivingSky(modifier: Modifier = Modifier, now: () -> LocalTime = LocalTime::now,
    season: Season = remember { seasonFor(LocalDate.now().monthValue) }, shootingStars: Boolean = false, rainbow: Boolean = false) {
    val reduced = reducedMotion()
    val transition = if (!reduced) rememberInfiniteTransition(label = "living sky") else null
    val drift = transition?.animateFloat(0f, 1f, infiniteRepeatable(tween(CloudLoopMillis, easing = LinearEasing)), label = "clouds")
    val twinkle = transition?.animateFloat(0f, 1f, infiniteRepeatable(tween(SkyTwinkleMillis, easing = LinearEasing)), label = "twinkle")
    val fall = transition?.animateFloat(0f, 1f, infiniteRepeatable(tween(SeasonLoopMillis, easing = LinearEasing)), label = "season")
    val palette = LocalKimiPalette.current
    val dark = palette.dark
    val starColor = if (dark) Color(0xFFF3E9C6) else lerp(palette.accent, palette.cream, .2f)
    Canvas(modifier) {
        val time = now()
        val minute = time.hour * 60f + time.minute + time.second / 60f
        val (top, low) = skyColors(minute, palette)
        drawRect(Brush.verticalGradient(0f to top, .5f to low, 1f to low.copy(alpha = 0f)))
        val night = nightness(minute)
        // A garden reward: through the morning after a day with everything done, a soft rainbow.
        if (rainbow) {
            val morning = ((minute - 360f) / 40f).coerceIn(0f, 1f) * ((660f - minute) / 40f).coerceIn(0f, 1f)
            if (morning > 0f) drawRainbow(morning * (if (dark) .16f else .26f) * (1f - night), palette.mono)
        }
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
        // A few things on the air for the time of year. Still frames would only look stuck, so
        // with animations removed there are none.
        val t = fall?.value ?: return@Canvas
        drawSeason(season, t, night, dark, palette.mono)
        // Another garden reward: now and then a star streaks across the night.
        if (shootingStars && night > .4f) drawShootingStars(t, night, starColor)
    }
}

/** The time of year, by the month (northern seasons). */
enum class Season { Spring, Summer, Autumn, Winter }

internal fun seasonFor(month: Int): Season = when (month) {
    3, 4, 5 -> Season.Spring
    6, 7, 8 -> Season.Summer
    9, 10, 11 -> Season.Autumn
    else -> Season.Winter
}

private const val SeasonLoopMillis = 30_000

/**
 * One drifting thing: where it starts across the sky, how many times it crosses per loop (a whole
 * number, so the loop joins up), where in its fall it begins, its size, how far it sways, how many
 * times it tumbles per crossing, and which of its season's colours it takes.
 */
private class Drifter(val x: Float, val laps: Int, val phase: Float, val size: Float, val sway: Float, val turns: Int, val tint: Int)

private fun drifters(count: Int, seed: Int): List<Drifter> {
    val r = kotlin.random.Random(seed)
    return List(count) { i ->
        Drifter((i + r.nextFloat()) / count, 2 + r.nextInt(2), r.nextFloat(), .7f + r.nextFloat() * .6f,
            .02f + r.nextFloat() * .04f, 1 + r.nextInt(2), r.nextInt(4))
    }
}

private val Leaves = drifters(7, seed = 3)
private val Petals = drifters(9, seed = 5)
private val Flakes = drifters(16, seed = 8)
private val Fluff = drifters(5, seed = 13)
private val Fireflies = drifters(7, seed = 21)

private val LeafColors = listOf(Color(0xFFE8833A), Color(0xFFC9503A), Color(0xFFE3B23C), Color(0xFFA8653A))
private val PetalColors = listOf(Color(0xFFF48FB1), Color(0xFFF7A8C4), Color(0xFFEC7AA5), Color(0xFFF5A0BE))
private val FireflyColor = Color(0xFFDDF27A)
/** Deeper, so a firefly still shows against a pale night sky in the light themes. */
private val FireflyDayColor = Color(0xFFB5CC2E)

/** A leaf one unit tall, pointing up, with its midrib. */
private val UnitLeaf: Path by lazy { Path().apply {
    moveTo(0f, -1f); cubicTo(.75f, -.55f, .6f, .55f, 0f, 1f); cubicTo(-.6f, .55f, -.75f, -.55f, 0f, -1f); close()
} }
/** A cherry petal one unit tall: rounder than a leaf, with a little notch at its tip. */
private val UnitPetal: Path by lazy { Path().apply {
    moveTo(0f, 1f); cubicTo(-.85f, .55f, -.8f, -.75f, -.18f, -.95f); lineTo(0f, -.75f); lineTo(.18f, -.95f)
    cubicTo(.8f, -.75f, .85f, .55f, 0f, 1f); close()
} }

private fun DrawScope.drawSeason(season: Season, t: Float, night: Float, dark: Boolean, mono: Boolean) {
    val unit = 6.dp.toPx()
    fun tone(c: Color) = if (mono) grey(c) else c
    // Falling things fade in at the top and out as the sky fades into the page.
    fun edge(p: Float) = (p / .08f).coerceAtMost(1f) * (1f - ((p - .62f) / .38f).coerceIn(0f, 1f))
    fun fallen(d: Drifter): Pair<Float, Offset> {
        val p = (t * d.laps + d.phase) % 1f
        val wind = if (season == Season.Autumn) .12f * p else 0f
        val x = (d.x + wind + d.sway * sin(2f * PI.toFloat() * (p * d.turns + d.phase))) * size.width
        return p to Offset(x, -size.height * .08f + size.height * 1.1f * p)
    }
    when (season) {
        Season.Autumn, Season.Spring -> {
            val shape = if (season == Season.Autumn) UnitLeaf else UnitPetal
            val colors = if (season == Season.Autumn) LeafColors else PetalColors
            (if (season == Season.Autumn) Leaves else Petals).forEach { d ->
                val (p, at) = fallen(d)
                val tumble = 2f * PI.toFloat() * p * d.turns
                val s = unit * d.size * (if (season == Season.Autumn) 1.15f else 1.05f)
                withTransform({
                    translate(at.x, at.y)
                    rotate(sin(tumble) * 55f + d.phase * 360f, Offset.Zero)
                    // Turning over in the air: it narrows to an edge and widens again.
                    scale(s * (.35f + .65f * kotlin.math.abs(cos(tumble))), s, Offset.Zero)
                }) {
                    drawPath(shape, tone(colors[d.tint]).copy(alpha = edge(p) * if (dark) .75f else .9f))
                    if (season == Season.Autumn) drawLine(lerp(tone(colors[d.tint]), Color.Black, .3f).copy(alpha = edge(p) * .6f),
                        Offset(0f, -.85f), Offset(0f, .9f), .12f)
                }
            }
        }
        Season.Winter -> Flakes.forEach { d ->
            val (p, at) = fallen(d)
            val flake = if (dark) Color.White.copy(alpha = .8f) else Color.White
            drawCircle(flake.copy(alpha = flake.alpha * edge(p)), unit * .42f * d.size, at)
        }
        Season.Summer -> {
            // Dandelion fluff floats up and across by day...
            if (night < 1f) Fluff.forEach { d ->
                val p = (t * d.laps + d.phase) % 1f
                val at = Offset((d.x + .25f * p) % 1f * size.width, size.height * (.85f - .75f * p))
                val a = (1f - night) * (p / .1f).coerceAtMost(1f) * (1f - p) * if (dark) .5f else .85f
                val fluff = Color.White.copy(alpha = a)
                // A faint shadow under the white filaments keeps them visible on a pale blue sky.
                val shade = Color.Black.copy(alpha = a * .14f)
                val r = unit * .95f * d.size
                val drop = Offset(.6.dp.toPx(), .6.dp.toPx())
                for (k in 0 until 8) {
                    val angle = (k * 45f + p * 90f) * PI.toFloat() / 180f
                    val tip = at + Offset(cos(angle), sin(angle)) * r
                    drawLine(shade, at + drop, tip + drop, r * .08f, StrokeCap.Round)
                    drawLine(fluff, at, tip, r * .08f, StrokeCap.Round)
                    drawCircle(fluff, r * .07f, tip)
                }
                drawCircle(tone(Color(0xFFA88F6A)).copy(alpha = a), r * .13f, at)
            }
            // ...and fireflies wander and blink by night.
            if (night > 0f) Fireflies.forEach { d ->
                val turn = 2f * PI.toFloat() * (t * d.laps + d.phase)
                val home = Offset(d.x * size.width, size.height * (.12f + .5f * ((d.phase * 7f) % 1f)))
                val at = home + Offset(sin(turn) * unit * 4f, sin(turn * 2f + d.sway * 40f) * unit * 2.5f)
                val blink = (sin(turn * 3f) * .5f + .5f).let { it * it }
                val glow = tone(if (dark) FireflyColor else FireflyDayColor)
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = .55f * blink * night), Color.Transparent), at, unit * 1.4f), unit * 1.4f, at)
                drawCircle(glow.copy(alpha = blink * night), unit * .22f, at)
            }
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

private val RainbowBands = listOf(0xFFFF8A80, 0xFFFFB74D, 0xFFFFE082, 0xFFA5D6A7, 0xFF90CAF9, 0xFFB39DDB).map { Color(it) }

/** A rainbow arching up from the lower right of the sky, each band a soft stroke. */
private fun DrawScope.drawRainbow(alpha: Float, mono: Boolean) {
    val band = 5.dp.toPx()
    val centre = Offset(size.width * .78f, size.height * .98f)
    val outer = size.height * .62f
    RainbowBands.forEachIndexed { i, colour ->
        val r = outer - i * band
        drawArc((if (mono) grey(colour) else colour).copy(alpha = alpha), 180f, 180f, false,
            centre - Offset(r, r), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(band))
    }
}

/** Two shooting stars per [SeasonLoopMillis]: each a bright head with a fading tail, gone in about a second. */
private fun DrawScope.drawShootingStars(t: Float, night: Float, colour: Color) {
    listOf(Triple(.18f, .12f, .14f), Triple(.68f, .62f, .08f)).forEach { (at, x, y) ->
        val q = (t - at) / .035f
        if (q <= 0f || q >= 1f) return@forEach
        val travel = size.width * .38f
        val start = Offset(size.width * x, size.height * y)
        val head = start + Offset(travel * q, travel * .45f * q)
        val tail = head - Offset(travel * .28f, travel * .28f * .45f)
        val fade = night * (if (q < .2f) q / .2f else 1f - (q - .2f) / .8f)
        drawLine(Brush.linearGradient(listOf(Color.Transparent, colour.copy(alpha = fade)), tail, head), tail, head, 2.dp.toPx(), StrokeCap.Round)
        drawCircle(colour.copy(alpha = fade), 2.2.dp.toPx(), head)
    }
}
