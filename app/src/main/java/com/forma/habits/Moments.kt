package com.forma.habits

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Larger set pieces: the sky over Today's greeting, the streak milestone badge, and the petal
 * spinner that stands in for a progress bar while Kimi is busy.
 */

private val SunColor = Color(0xFFFFC94D)
private val SunRays = Color(0xFFFFB13D)
private const val SkyTurnMillis = 12_000

/** Where a star sits in the sky box, as fractions of its size, and how its twinkle is offset. */
private val Stars = listOf(
    Triple(.14f, .30f, .0f), Triple(.30f, .10f, .37f), Triple(.62f, .18f, .71f),
    Triple(.86f, .34f, .19f), Triple(.47f, .42f, .55f)
)

/**
 * A little sky that follows the clock: the sun climbs and sets along a dashed arc from 6 am to 6 pm,
 * then a crescent moon takes the same path through the night among twinkling stars. It reads the
 * time on every frame it draws, so it keeps up while visible and costs nothing off screen.
 */
@Composable fun SkyBadge(modifier: Modifier = Modifier) {
    val turn = if (!reducedMotion()) {
        rememberInfiniteTransition(label = "sky")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(SkyTurnMillis, easing = LinearEasing)), label = "sky turn")
    } else null
    val palette = LocalKimiPalette.current
    val trail = palette.quiet.copy(alpha = .3f)
    // By day the moon and stars borrow the theme's accent, softened toward the page.
    val moon = if (palette.dark) Color(0xFFF3E9C6) else lerp(palette.accent, palette.cream, .45f)
    val star = if (palette.dark) Color(0xFFF3E9C6) else lerp(palette.accent, palette.cream, .25f)
    // Monochrome keeps the little sky too: a pale grey sun instead of a golden one.
    val sunColor = if (palette.mono) Color(0xFFB4B4B9) else SunColor
    val sunRays = if (palette.mono) Color(0xFF9A9AA0) else SunRays
    Canvas(modifier) {
        val t = turn?.value ?: 0f
        val now = LocalTime.now()
        val minutes = now.hour * 60 + now.minute
        val daytime = minutes in 360 until 1080
        val along = (if (daytime) minutes - 360 else (minutes - 1080 + 1440) % 1440) / 720f
        val pad = 9.dp.toPx()
        val radius = min(size.width / 2 - pad, size.height - pad * 1.6f)
        val ground = Offset(size.width / 2, size.height - pad * .6f)
        drawArc(trail, 180f, 180f, false, ground - Offset(radius, radius), Size(radius * 2, radius * 2),
            style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))))
        drawLine(trail, ground - Offset(radius + pad * .6f, 0f), ground + Offset(radius + pad * .6f, 0f), 1.4.dp.toPx(), StrokeCap.Round)
        val angle = PI * (1 - along)
        val body = ground + Offset((cos(angle) * radius).toFloat(), (-sin(angle) * radius).toFloat())
        if (daytime) {
            drawCircle(Brush.radialGradient(listOf(sunColor.copy(alpha = .45f), Color.Transparent), body, 20.dp.toPx()), 20.dp.toPx(), body)
            rotate(t * 360f, body) {
                for (i in 0 until 8) {
                    val a = i * PI.toFloat() / 4f
                    val from = body + Offset(cos(a), sin(a)) * 9.5.dp.toPx()
                    val to = body + Offset(cos(a), sin(a)) * 13.dp.toPx()
                    drawLine(sunRays, from, to, 1.8.dp.toPx(), StrokeCap.Round)
                }
            }
            drawCircle(sunColor, 7.dp.toPx(), body)
        } else {
            Stars.forEach { (x, y, phase) ->
                // Whole-number cycles per turn, so the twinkle loops without a jump.
                val glow = .3f + .7f * (.5f + .5f * sin(2f * PI.toFloat() * (t * 4f + phase)))
                drawSparkle(star.copy(alpha = glow), Offset(size.width * x, size.height * y), 3.2.dp.toPx() * (.7f + .3f * glow))
            }
            // A true crescent, cut out of the disc: a page-coloured circle laid over it would show
            // against the living sky behind Today.
            val disc = Path().apply { addOval(Rect(body, 8.dp.toPx())) }
            val bite = Path().apply { addOval(Rect(body + Offset(3.6.dp.toPx(), -2.6.dp.toPx()), 7.5.dp.toPx())) }
            drawPath(Path().apply { op(disc, bite, PathOperation.Difference) }, moon)
        }
    }
}

/** A four-pointed twinkle. */
internal fun DrawScope.drawSparkle(color: Color, at: Offset, radius: Float) {
    val path = Path().apply {
        moveTo(at.x, at.y - radius)
        quadraticTo(at.x, at.y, at.x + radius, at.y)
        quadraticTo(at.x, at.y, at.x, at.y + radius)
        quadraticTo(at.x, at.y, at.x - radius, at.y)
        quadraticTo(at.x, at.y, at.x, at.y - radius)
        close()
    }
    drawPath(path, color)
}

/** Streak lengths worth a moment of their own. */
val StreakMilestones = listOf(7, 30, 100)

private const val MilestoneMillis = 2800
private val Ribbon = Color(0xFFFF8A65)
private val RibbonShade = Color(0xFFD9603F)

/**
 * A streak reached a milestone: a badge springs up, a ribbon unfurls across it and sparkles burst
 * out around it. It never takes touches, so taps go straight through to Today, and it announces
 * itself to screen readers. Fires when [trigger] changes to anything but zero.
 */
@Composable fun MilestoneMoment(trigger: Int, days: Int, habit: String, modifier: Modifier = Modifier) {
    val reduced = reducedMotion()
    val progress = remember { Animatable(1f) }
    val sparkleColors = confettiColors()
    val sparkles = remember(sparkleColors) { confetti(18, seed = 11, colors = sparkleColors) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(MilestoneMillis, easing = LinearEasing))
    }
    val running by remember { derivedStateOf { progress.isRunning } }
    if (!running) return
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!reduced) Canvas(Modifier.fillMaxSize()) {
            val p = progress.value
            val burst = easeOutCubic((p / .4f).coerceIn(0f, 1f))
            val fade = if (p < .8f) 1f else 1f - (p - .8f) / .2f
            sparkles.forEach { s ->
                // They burst from behind the badge and settle around its edges, where they can be seen.
                val reach = (60.dp.toPx() + 130.dp.toPx() * s.speed) * burst
                val at = center + Offset(cos(s.angle) * reach * 1.15f, sin(s.angle) * reach * 1.25f)
                val twinkle = .6f + .4f * sin((p * 6f + s.drift) * 2f * PI.toFloat())
                drawSparkle(s.color.copy(alpha = fade), at, 9.dp.toPx() * s.size * twinkle)
            }
        }
        Column(
            Modifier.width(260.dp)
                .graphicsLayer {
                    val p = progress.value
                    val pop = if (reduced) 1f else easeOutBack((p / .18f).coerceIn(0f, 1f))
                    scaleX = .6f + .4f * pop; scaleY = scaleX
                    alpha = (p / .06f).coerceAtMost(1f) * if (p > .85f) 1f - (p - .85f) / .15f else 1f
                }
                .clip(RoundedCornerShape(28.dp)).background(Purple)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .padding(bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                val unfurl = if (reduced) 1f else easeOutCubic(((progress.value - .08f) / .25f).coerceIn(0f, 1f))
                val width = size.width * .86f * unfurl
                if (width <= 0f) return@Canvas
                val notch = 10.dp.toPx()
                val top = 10.dp.toPx(); val bottom = size.height
                val left = center.x - width / 2; val right = center.x + width / 2
                val band = Path().apply {
                    moveTo(left, top); lineTo(right, top); lineTo(right - notch, (top + bottom) / 2); lineTo(right, bottom)
                    lineTo(left, bottom); lineTo(left + notch, (top + bottom) / 2); close()
                }
                drawPath(band, Ribbon)
                drawLine(RibbonShade, Offset(left + notch, bottom - 2.dp.toPx()), Offset(right - notch, bottom - 2.dp.toPx()), 2.dp.toPx())
            }
            Spacer(Modifier.height(4.dp))
            Flame(lit = true, Modifier.size(34.dp), tint = Highlight)
            Text("$days", color = Highlight, fontWeight = FontWeight.ExtraBold, fontSize = 52.sp, lineHeight = 56.sp)
            Text(pluralStringResource(R.plurals.milestone_title, days, days), color = OnAccent,
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(stringResource(R.string.milestone_caption, habit), color = OnAccent.copy(alpha = .85f),
                fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
}

/**
 * Six petals pulsing in turn around a slowly turning ring: Kimi's stand-in for an indeterminate
 * progress bar. With animations removed it rests as a still ring.
 */
@Composable fun PetalSpinner(modifier: Modifier = Modifier, color: Color = Accent) {
    val spin = if (!reducedMotion()) {
        rememberInfiniteTransition(label = "petal spinner")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "spin")
    } else null
    Canvas(modifier.size(26.dp)) {
        val turn = spin?.value ?: 0f
        val ring = size.minDimension * .31f
        val petal = size.minDimension * .14f
        for (i in 0 until 6) {
            // The lit petal walks round the ring once per turn; the rest fade behind it.
            val behind = ((turn * 6f - i) % 6f + 6f) % 6f
            val glow = 1f - behind / 6f
            val angle = (i * 60f - 90f + turn * 120f) * PI.toFloat() / 180f
            drawCircle(color.copy(alpha = .22f + .78f * glow), petal * (.7f + .45f * glow),
                center + Offset(cos(angle), sin(angle)) * ring)
        }
    }
}
