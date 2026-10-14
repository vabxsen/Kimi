package com.forma.habits

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A deleted habit does not just vanish: its card comes apart into petals of its own colour that a
 * gust of wind carries off to the right, sweeping across from its left edge. Rows say where they are
 * with [gustTracked]; [PetalGust.blow] starts the gust once the editor sheet has slid out of the way.
 */
@Stable class PetalGust {
    private class Seen(val bounds: Rect, val color: Color) { var goneAt = 0L }
    private val seen = mutableMapOf<String, Seen>()
    internal val runs = mutableStateListOf<GustRun>()

    internal fun track(id: String, bounds: Rect, color: Color) { seen[id] = Seen(bounds, color) }
    internal fun gone(id: String) { seen[id]?.goneAt = SystemClock.uptimeMillis() }

    /** Blows away the habit [id], if its row was on screen just now; otherwise there is nothing to see. */
    fun blow(id: String) {
        val last = seen.remove(id) ?: return
        if (last.goneAt != 0L && SystemClock.uptimeMillis() - last.goneAt > GustMemoryMillis) return
        runs += GustRun(last.bounds, last.color, Random.nextInt())
    }
}

/** A row that left the screen longer ago than this was not what the person was looking at. */
private const val GustMemoryMillis = 3_000L
/** Waits for the editor sheet and delete dialog to clear before the gust starts. */
private const val GustWaitMillis = 380L
private const val GustMillis = 1_250

internal class GustRun(val bounds: Rect, val color: Color, seed: Int) {
    val progress = Animatable(0f)
    val petals: List<GustPetal> = Random(seed).let { r ->
        // A loose grid over the card, so at the start the petals still read as its shape.
        val columns = 9; val rows = 4
        List(columns * rows) { i ->
            val c = i % columns; val row = i / columns
            GustPetal(
                x = (c + .2f + r.nextFloat() * .6f) / columns, y = (row + .2f + r.nextFloat() * .6f) / rows,
                lift = .6f + r.nextFloat() * .9f, reach = .7f + r.nextFloat() * .6f,
                spin = (r.nextFloat() - .5f) * 4f, size = .8f + r.nextFloat() * .5f, highlight = r.nextInt(5) == 0
            )
        }
    }
}

internal class GustPetal(val x: Float, val y: Float, val lift: Float, val reach: Float, val spin: Float, val size: Float, val highlight: Boolean)

internal val LocalPetalGust = staticCompositionLocalOf<PetalGust?> { null }

/** Lets the gust know where this habit's row is drawn and in what colour, while it is on screen. */
@Composable fun Modifier.gustTracked(id: String, color: Color): Modifier {
    val gust = LocalPetalGust.current ?: return this
    DisposableEffect(gust, id) { onDispose { gust.gone(id) } }
    return onGloballyPositioned { gust.track(id, it.boundsInRoot(), color) }
}

private val UnitGustPetal: Path by lazy { Path().apply {
    moveTo(0f, 1f); cubicTo(-.85f, .55f, -.8f, -.75f, 0f, -1f); cubicTo(.8f, -.75f, .85f, .55f, 0f, 1f); close()
} }

/** Draws every gust in flight, over the whole app. It takes no touches. */
@Composable fun PetalGustOverlay(gust: PetalGust, modifier: Modifier = Modifier) {
    if (reducedMotion()) { gust.runs.clear(); return }
    val haptics = rememberHaptics()
    gust.runs.toList().forEach { run ->
        key(run) {
            LaunchedEffect(run) {
                delay(GustWaitMillis)
                haptics.play(Feel.Off)
                run.progress.animateTo(1f, tween(GustMillis, easing = LinearEasing))
                gust.runs.remove(run)
            }
        }
    }
    if (gust.runs.isEmpty()) return
    val origin = remember { arrayOf(Offset.Zero) }
    val highlight = Highlight
    Canvas(modifier.fillMaxSize().onGloballyPositioned { origin[0] = it.positionInRoot() }) {
        val unit = 7.dp.toPx()
        gust.runs.forEach { run ->
            val t = run.progress.value
            if (t <= 0f) return@forEach
            val box = run.bounds.translate(-origin[0])
            run.petals.forEach { p ->
                // The wind reaches each petal as it sweeps from the card's left edge to its right.
                val local = ((t - p.x * .35f) / .65f).coerceIn(0f, 1f)
                if (local <= 0f) {
                    // Not yet lifted: still part of the card's outline.
                    drawPetal(run.color, box.left + box.width * p.x, box.top + box.height * p.y, unit * p.size * 1.15f, 0f, 1f)
                    return@forEach
                }
                val ease = local * local
                val x = box.left + box.width * p.x + box.width * .55f * p.reach * ease + sin(local * 3f * PI.toFloat() + p.spin) * unit * 2f
                val y = box.top + box.height * p.y - box.height * 1.4f * p.lift * ease + abs(cos(local * 2f * PI.toFloat())) * unit
                val fade = 1f - ((local - .55f) / .45f).coerceIn(0f, 1f)
                drawPetal(if (p.highlight) highlight else run.color, x, y, unit * p.size * (1.15f - .35f * local), p.spin * 220f * local, fade)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPetal(color: Color, x: Float, y: Float, size: Float, turn: Float, alpha: Float) {
    withTransform({ translate(x, y); rotate(turn, Offset.Zero); scale(size * .75f, size, Offset.Zero) }) {
        drawPath(UnitGustPetal, color.copy(alpha = color.alpha * alpha))
    }
}
