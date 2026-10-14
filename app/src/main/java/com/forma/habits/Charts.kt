package com.forma.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Insights' charts drawing themselves: a ring that sweeps round to the score, a trend line that
 * traces across the week's bars with a dot popping onto each, and progress bars that catch a glint
 * of light once they have filled. Every value they read is already animated by [rememberGrow], so
 * with animations removed they simply appear finished.
 */

/** A ring sweeping clockwise from the top to [fraction] of a turn, with a bright head, round [content]. */
@Composable fun ScoreRing(fraction: Float, modifier: Modifier = Modifier, track: Color = OnAccent.copy(alpha = .2f),
    color: Color = Highlight, content: @Composable BoxScope.() -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = 7.dp.toPx()
            val radius = size.minDimension / 2 - width
            val topLeft = center - Offset(radius, radius)
            val ring = Size(radius * 2, radius * 2)
            drawArc(track, 0f, 360f, false, topLeft, ring, style = Stroke(width))
            val sweep = 360f * fraction.coerceIn(0f, 1f)
            if (sweep <= 0f) return@Canvas
            drawArc(color, -90f, sweep, false, topLeft, ring, style = Stroke(width, cap = StrokeCap.Round))
            val angle = (sweep - 90f) * PI.toFloat() / 180f
            val head = center + Offset(cos(angle), sin(angle)) * radius
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .55f), Color.Transparent), head, width * 2.2f), width * 2.2f, head)
            drawCircle(color, width * .62f, head)
        }
        content()
    }
}

/**
 * Where the week chart's line runs: the top centre of each charted bar, measured from the bars
 * themselves so it follows them as they grow. Rest days have no bar and are left out.
 */
class TrendPoints(count: Int) {
    internal val bars = arrayOfNulls<LayoutCoordinates>(count)
    internal var chart: LayoutCoordinates? = null
    internal fun points(): List<Offset?> = bars.map { bar ->
        val chart = chart ?: return@map null
        if (bar == null || !bar.isAttached || !chart.isAttached) null
        else chart.localPositionOf(bar, Offset(bar.size.width / 2f, 0f))
    }
}

@Composable fun rememberTrendPoints(count: Int) = remember(count) { TrendPoints(count) }

/** A smooth curve through [points], easing horizontally into and out of each one. */
private fun curve(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { i, p ->
        if (i == 0) moveTo(p.x, p.y)
        else {
            val before = points[i - 1]
            val mid = (before.x + p.x) / 2
            cubicTo(mid, before.y, mid, p.y, p.x, p.y)
        }
    }
}

/**
 * The trend line, drawn behind the bars so their labels stay on top of it. It traces from left to
 * right as [trace] goes from 0 to 1. [rises] are the bars' growing heights, read here only so the
 * line redraws while they move.
 */
@Composable fun TrendLine(points: TrendPoints, trace: State<Float>, rises: List<State<Float>>, modifier: Modifier = Modifier) {
    val color = Ink.copy(alpha = .5f)
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }
    Canvas(modifier) {
        rises.forEach { it.value }
        val shown = points.points().filterNotNull()
        if (shown.size < 2) return@Canvas
        measure.setPath(curve(shown), false)
        segment.reset()
        measure.getSegment(0f, measure.length * trace.value.coerceIn(0f, 1f), segment, true)
        drawPath(segment, color, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** A dot pops onto each bar as the line reaches it. Drawn over the bars. */
@Composable fun TrendDots(points: TrendPoints, trace: State<Float>, rises: List<State<Float>>, modifier: Modifier = Modifier) {
    val ring = Ink
    val fill = Paper
    Canvas(modifier) {
        rises.forEach { it.value }
        val all = points.points()
        val shown = all.filterNotNull()
        if (shown.isEmpty()) return@Canvas
        val t = trace.value
        shown.forEachIndexed { i, p ->
            // Each dot's moment is where along the line it sits; it springs up as the line arrives,
            // and the last one still has time to land before the trace ends.
            val at = if (shown.size == 1) 0f else i.toFloat() / (shown.size - 1)
            val pop = ((t * 1.15f - at) / .15f).coerceIn(0f, 1f).let { if (it >= 1f) 1f else easeOutBack(it) }
            if (pop <= 0f) return@forEachIndexed
            val r = 4.5.dp.toPx() * pop
            drawCircle(fill, r, p)
            drawCircle(ring, r, p, style = Stroke(1.8.dp.toPx() * pop))
        }
    }
}

/**
 * A progress bar that fills to [progress] and then catches a glint of light sweeping across the
 * filled part, timed by [shine] running from 0 to 1.
 */
@Composable fun ShineBar(progress: Float, shine: State<Float>, modifier: Modifier = Modifier) {
    val track = Overlay
    val fill = Ink
    val glint = OnInk
    Canvas(modifier.progressSemantics(progress.coerceIn(0f, 1f))) {
        val radius = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val filled = size.width * progress.coerceIn(0f, 1f)
        if (filled <= 0f) return@Canvas
        drawRoundRect(fill, size = Size(filled, size.height), cornerRadius = radius)
        val s = shine.value
        if (s <= 0f || s >= 1f) return@Canvas
        val band = size.height * 6f
        val x = -band + (filled + band * 2) * s
        clipRect(right = filled) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, glint.copy(alpha = .45f), Color.Transparent), x - band / 2, x + band / 2),
                Offset(x - band / 2, 0f), Size(band, size.height))
        }
    }
}
