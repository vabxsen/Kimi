package com.forma.habits

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * Kimi's app icon, the Dot k, drawing itself: the cream tile appears, the stem is drawn from the
 * top down, the arm reaches out along its 45-degree line, the leg kicks out, and then the coral dot
 * drops in from above, bounces once and settles where the arm points. Tap it to watch again. It is
 * the icon, so it keeps the icon's own colours in every theme. Decoration only: the name is always
 * written beside it.
 */

private const val LogoMillis = 1500
private val Paper1 = Color(0xFFFCF8F2)
private val Paper2 = Color(0xFFEEE4D6)
private val Edge = Color(0xFFE2D6C6)
private val KInk = Color(0xFF2A1F33)
private val DotLight = Color(0xFFFFC59A)
private val DotDeep = Color(0xFFF2743A)

/** Falls and bounces to rest, like a ball dropped onto a table. */
internal fun easeOutBounce(t: Float): Float {
    val n = 7.5625f; val d = 2.75f
    return when {
        t < 1f / d -> n * t * t
        t < 2f / d -> { val x = t - 1.5f / d; n * x * x + .75f }
        t < 2.5f / d -> { val x = t - 2.25f / d; n * x * x + .9375f }
        else -> { val x = t - 2.625f / d; n * x * x + .984375f }
    }
}

@Composable fun DotKLogo(modifier: Modifier = Modifier) {
    val reduced = reducedMotion()
    val drawn = remember { Animatable(if (reduced) 1f else 0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (drawn.value < 1f) drawn.animateTo(1f, tween(LogoMillis, easing = LinearEasing)) }
    Canvas(modifier.pointerInput(reduced) {
        detectTapGestures {
            if (!reduced) scope.launch { drawn.snapTo(0f); drawn.animateTo(1f, tween(LogoMillis, easing = LinearEasing)) }
        }
    }) { drawLogo(drawn.value) }
}

/** Where [t] (0-1 over the whole drawing) is within the step from [from] to [to], eased. */
private fun step(t: Float, from: Float, to: Float): Float = easeOutCubic(((t - from) / (to - from)).coerceIn(0f, 1f))

private fun DrawScope.drawLogo(t: Float) {
    val u = size.minDimension / 100f
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    // The tile pops up first.
    val tile = step(t, 0f, .18f)
    scale(.8f + .2f * easeOutBack(tile), Offset(size.width / 2, size.height / 2)) {
        drawRoundRect(Brush.linearGradient(listOf(Paper1, Paper2), p(15f, 0f), p(85f, 100f)), alpha = tile, cornerRadius = CornerRadius(22f * u))
        drawRoundRect(Edge.copy(alpha = tile), cornerRadius = CornerRadius(22f * u), style = Stroke(1.2f * u))
    }
    val stroke = Stroke(9.5f * u, cap = StrokeCap.Round)
    fun line(from: Offset, to: Offset, k: Float) {
        if (k <= 0f) return
        drawLine(KInk, from, from + (to - from) * k, stroke.width, StrokeCap.Round)
    }
    // Stem, then arm, then leg, each picking up a little before the last one finishes.
    line(p(33f, 26f), p(33f, 74f), step(t, .14f, .38f))
    line(p(34f, 57f), p(53f, 38f), step(t, .34f, .52f))
    line(p(43f, 48f), p(58f, 74f), step(t, .48f, .66f))
    // The dot drops onto the end of the arm's line and bounces once, squashing as it lands.
    val fall = ((t - .62f) / .38f).coerceIn(0f, 1f)
    if (fall <= 0f) return
    val y = -12f + 38f * easeOutBounce(fall)
    val landed = fall > .36f
    val squash = if (landed) 1f + .14f * (1f - ((fall - .36f) / .3f).coerceIn(0f, 1f)) else 1f
    val centre = p(65f, y)
    val r = 7f * u
    drawOval(Brush.radialGradient(listOf(DotLight, DotDeep), centre - Offset(r * .3f, r * .35f), r * 1.5f),
        topLeft = centre - Offset(r * squash, r / squash), size = Size(2f * r * squash, 2f * r / squash))
}
