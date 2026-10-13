package com.forma.habits

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Kimi's motion vocabulary: the check-in pop, the day's celebration, count-ups, the odometer, the
 * flickering flame and the tab hop. Everything is plain Compose, drawn on Canvas where it moves a lot,
 * and reads animated values in the draw or layer phase so a running effect does not recompose a screen.
 *
 * Loops use infinite transitions rather than hand-written `while` loops: Compose pauses them with the
 * frame clock when Kimi leaves the screen, and UI tests know not to wait on them.
 */

/** True when Android's "Remove animations" is on. Kimi then drops loops, confetti and transitions. */
@Composable fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Overshoots a little and settles: the "pop" of a petal opening or a tick landing. */
internal fun easeOutBack(t: Float): Float {
    val c = 1.70158f
    val x = t - 1f
    return 1f + (c + 1f) * x * x * x + c * x * x
}

internal fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3)

/** Fixed, saturated colours: pastel confetti would vanish against Kimi's pastel cards in either theme. */
internal val ConfettiColors = listOf(
    Color(0xFFFFC94D), Color(0xFFFF8A65), Color(0xFFF06292),
    Color(0xFF7C5CE0), Color(0xFF4DB6AC), Color(0xFF64B5F6)
)

internal class Confetto(
    val angle: Float, val speed: Float, val size: Float, val spin: Float,
    val color: Color, val round: Boolean, val drift: Float
)

internal fun confetti(count: Int, seed: Int): List<Confetto> {
    val random = Random(seed)
    return List(count) { i ->
        Confetto(
            angle = i.toFloat() / count * 2f * PI.toFloat() + random.nextFloat() * .5f,
            speed = .55f + random.nextFloat() * .45f,
            size = .7f + random.nextFloat() * .6f,
            spin = (random.nextFloat() - .5f) * 3f,
            color = ConfettiColors[i % ConfettiColors.size],
            round = random.nextInt(3) == 0,
            drift = random.nextFloat()
        )
    }
}

/** A dot, or a petal-shaped sliver turned by [rotation]. */
private fun DrawScope.drawConfetto(piece: Confetto, at: Offset, unit: Float, rotation: Float, alpha: Float) {
    val color = piece.color.copy(alpha = alpha.coerceIn(0f, 1f))
    val width = unit * piece.size
    if (piece.round) drawCircle(color, width * .5f, at)
    else rotate(rotation, at) {
        drawRoundRect(color, Offset(at.x - width * .5f, at.y - width * .9f), Size(width, width * 1.8f), CornerRadius(width * .5f))
    }
}

private const val BurstMillis = 720

/** A small confetti pop fired from a tap. Draw it with [drawBurst]. */
@Stable class Burst internal constructor(internal val pieces: List<Confetto>, private val enabled: Boolean) {
    internal val progress = Animatable(1f)
    suspend fun fire() {
        if (!enabled) return
        progress.snapTo(0f)
        progress.animateTo(1f, tween(BurstMillis, easing = LinearEasing))
    }
}

@Composable fun rememberBurst(count: Int = 14): Burst {
    val reduced = reducedMotion()
    return remember(reduced) { Burst(confetti(count, Random.nextInt()), !reduced) }
}

/**
 * Draws [burst] over this content, spraying out from [origin] with a touch of gravity. Put it before
 * any clip in the chain so the pieces can leave the card. Only draws while the burst is running, so a
 * pop interrupted by another tap never leaves pieces frozen in place.
 */
fun Modifier.drawBurst(burst: Burst, reach: Dp = 60.dp, origin: DrawScope.() -> Offset): Modifier = drawWithContent {
    drawContent()
    if (!burst.progress.isRunning) return@drawWithContent
    val p = burst.progress.value
    val from = origin()
    val distance = reach.toPx()
    val unit = 6.dp.toPx()
    val travel = easeOutCubic(p)
    val fade = if (p < .55f) 1f else 1f - (p - .55f) / .45f
    burst.pieces.forEach { piece ->
        val d = distance * piece.speed * travel
        val at = Offset(from.x + cos(piece.angle) * d, from.y + sin(piece.angle) * d + distance * .4f * p * p)
        drawConfetto(piece, at, unit, piece.spin * 360f * p, fade)
    }
}

private const val CelebrationMillis = 1500

/**
 * The day's last habit is ticked: a confetti shower and the flower spinning into full bloom. It takes
 * no touches and no focus, so it never blocks the screen, and it is gone in a second and a half.
 * Fires when [trigger] changes to anything but zero.
 */
@Composable fun DayCompleteCelebration(trigger: Int, modifier: Modifier = Modifier) {
    if (reducedMotion()) return
    val progress = remember { Animatable(1f) }
    val pieces = remember { confetti(48, seed = 7) }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(CelebrationMillis, easing = LinearEasing))
    }
    val running by remember { derivedStateOf { progress.isRunning } }
    if (!running) return
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val p = progress.value
            val unit = 7.dp.toPx()
            val fade = if (p < .75f) 1f else 1f - (p - .75f) / .25f
            pieces.forEachIndexed { i, piece ->
                val lane = (i * .618034f + piece.drift * .3f) % 1f
                val x = size.width * lane + sin((p * 3f + piece.drift) * 2f * PI.toFloat()) * unit * 2f
                val y = -unit * 3f + (size.height + unit * 6f) * (p * (.75f + piece.speed * .5f) - piece.drift * .25f)
                drawConfetto(piece, Offset(x, y), unit, piece.spin * 540f * p, fade)
            }
        }
        Flower(Modifier.size(150.dp).graphicsLayer {
            val p = progress.value
            val pop = easeOutBack((p / .35f).coerceIn(0f, 1f))
            scaleX = pop; scaleY = pop
            rotationZ = 360f * easeOutCubic(p)
            alpha = if (p < .7f) 1f else 1f - (p - .7f) / .3f
        })
    }
}

/**
 * A value that grows from zero the first time it is shown, then glides to each new target. Count-ups,
 * chart bars and progress fills all use it, so a screen arrives with its numbers already moving.
 */
@Composable fun rememberGrow(target: Float, durationMillis: Int = 900, delayMillis: Int = 0): State<Float> {
    val reduced = reducedMotion()
    val value = remember { Animatable(if (reduced) target else 0f) }
    LaunchedEffect(target, reduced) {
        if (reduced) value.snapTo(target)
        else value.animateTo(target, tween(durationMillis, delayMillis, FastOutSlowInEasing))
    }
    return value.asState()
}

/** Swaps a number's text like an odometer: rolling up when it grows, down when it shrinks. */
@Composable fun Rolling(value: Int, modifier: Modifier = Modifier, content: @Composable (Int) -> Unit) {
    val reduced = reducedMotion()
    AnimatedContent(value, modifier, transitionSpec = {
        if (reduced) EnterTransition.None togetherWith ExitTransition.None
        else {
            val up = targetState > initialState
            val motion = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = .7f, stiffness = Spring.StiffnessMediumLow)
            (slideInVertically(motion) { if (up) it else -it } + fadeIn()) togetherWith
                (slideOutVertically(motion) { if (up) -it else it } + fadeOut()) using SizeTransform(clip = true)
        }
    }, label = "rolling") { content(it) }
}

/** The streak flame. It flickers while there is a streak to keep alive and rests when there is none. */
@Composable fun Flame(lit: Boolean, modifier: Modifier = Modifier, tint: Color = Ink) {
    val flicker = if (lit && !reducedMotion()) {
        rememberInfiniteTransition(label = "flame")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "flicker")
    } else null
    Icon(Icons.Rounded.LocalFireDepartment, null, modifier.graphicsLayer {
        val f = flicker?.value ?: return@graphicsLayer
        val turn = 2f * PI.toFloat() * f
        // Whole-number frequencies only, so the loop joins up without a visible jump.
        val wave = sin(turn) * .6f + sin(turn * 3f) * .4f
        transformOrigin = TransformOrigin(.5f, 1f)
        scaleY = 1f + wave * .08f
        scaleX = 1f - wave * .03f
        rotationZ = sin(turn * 2f) * 4f
    }, tint = tint)
}

/** An icon that gives a little hop when it becomes selected, but not when the screen first appears. */
@Composable fun HopIcon(icon: ImageVector, selected: Boolean, modifier: Modifier = Modifier, contentDescription: String? = null,
    tint: Color = LocalContentColor.current) {
    val reduced = reducedMotion()
    val scale = remember { Animatable(1f) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (selected && settled && !reduced) {
            scale.snapTo(.7f)
            scale.animateTo(1f, spring(dampingRatio = .4f, stiffness = Spring.StiffnessMedium))
        }
        settled = true
    }
    Icon(icon, contentDescription, modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }, tint = tint)
}

/** The check-in circle: it squishes when tapped, fills, and the tick draws itself in one stroke. */
@Composable fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier) {
    val reduced = reducedMotion()
    val fill by animateColorAsState(if (checked) Ink else Overlay, label = "check fill")
    val edge by animateColorAsState(if (checked) Ink else Quiet, label = "check edge")
    val tick by animateFloatAsState(if (checked) 1f else 0f,
        tween(if (checked) 320 else 140, delayMillis = if (checked) 60 else 0, easing = FastOutSlowInEasing), label = "tick")
    val squish = remember { Animatable(1f) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        if (settled && !reduced) {
            squish.snapTo(if (checked) .72f else .88f)
            squish.animateTo(1f, spring(dampingRatio = .38f, stiffness = Spring.StiffnessMediumLow))
        }
        settled = true
    }
    val ink = OnInk
    val path = remember { Path() }
    val segment = remember { Path() }
    val measure = remember { PathMeasure() }
    Box(modifier.size(29.dp).graphicsLayer { scaleX = squish.value; scaleY = squish.value }
        .clip(CircleShape).background(fill).border(1.5.dp, edge, CircleShape)) {
        Canvas(Modifier.fillMaxSize()) {
            if (tick <= 0f) return@Canvas
            val s = size.minDimension
            path.reset()
            path.moveTo(s * .29f, s * .52f)
            path.lineTo(s * .44f, s * .67f)
            path.lineTo(s * .72f, s * .37f)
            measure.setPath(path, false)
            segment.reset()
            measure.getSegment(0f, measure.length * tick, segment, true)
            drawPath(segment, ink, style = Stroke(s * .1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
