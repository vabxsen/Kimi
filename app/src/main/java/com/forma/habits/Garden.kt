package com.forma.habits

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The plant beside each habit on the Habits tab. It grows with how often the habit has been done in
 * the last month, from a seed to a sprout, a seedling, a bud and finally a flower in bloom, and it
 * droops when yesterday was missed, standing up again once the habit is done. Growing and drooping
 * both animate, and it sways a little in a breeze. Decoration only: the card says it in words.
 */

private val Soil = Color(0xFF9C7A5B)
private val Stem = Color(0xFF4F8F3E)
private val Leaf = Color(0xFF79BF63)
private val Bud = Color(0xFFF0789F)
private val Bloom = Color(0xFFFFD86B)
private val BloomCore = Color(0xFFFF9A57)

private fun grey(c: Color): Color { val l = c.luminance(); return Color(l, l, l, c.alpha) }

@Composable fun HabitPlant(stage: Int, wilting: Boolean, modifier: Modifier = Modifier) {
    val reduced = reducedMotion()
    val growth by animateFloatAsState(stage.toFloat(), if (reduced) tween(0) else spring(dampingRatio = .55f, stiffness = Spring.StiffnessVeryLow), label = "plant growth")
    val droop by animateFloatAsState(if (wilting && stage > 0) 1f else 0f, if (reduced) tween(0) else tween(900, easing = FastOutSlowInEasing), label = "plant droop")
    val breeze = if (!reduced) rememberInfiniteTransition(label = "plant breeze")
        .animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway") else null
    val mono = LocalKimiPalette.current.mono
    val colors = remember(mono) { plantColors(mono) }
    Canvas(modifier) { drawPlant(growth, droop, breeze?.value ?: 0f, colors) }
}

internal class PlantColors(val soil: Color, val stem: Color, val leaf: Color, val bud: Color, val bloom: Color, val core: Color)

internal fun plantColors(mono: Boolean) = if (mono) PlantColors(grey(Soil), grey(Stem), grey(Leaf), grey(Bud), grey(Bloom), grey(BloomCore))
    else PlantColors(Soil, Stem, Leaf, Bud, Bloom, BloomCore)

/** Where along 0..4 growth [g] sits between stages [from] and [from]+1, as 0..1. */
private fun part(g: Float, from: Float) = (g - from).coerceIn(0f, 1f)

/** Draws the plant in a [w] by [h] box at the drawing origin: the whole canvas unless told otherwise. */
internal fun DrawScope.drawPlant(g: Float, droop: Float, sway: Float, colors: PlantColors, w: Float = size.width, h: Float = size.height, soilShown: Boolean = true) {
    val soil = colors.soil; val stem = colors.stem; val leaf = colors.leaf
    val bud = colors.bud; val bloom = colors.bloom; val core = colors.core
    val ground = Offset(w / 2, h * .9f)
    // A little mound of earth, always there.
    if (soilShown) drawOval(soil, Offset(w * .18f, h * .84f), Size(w * .64f, h * .14f))
    // The seed sinks into the soil as the sprout takes over.
    val seed = 1f - part(g, 0f)
    if (seed > 0f) drawOval(lerpColor(soil, Color.Black, .25f).copy(alpha = seed), Offset(w * .42f, h * .8f), Size(w * .16f, h * .09f))
    if (g <= 0f) return
    // The stem grows taller with each stage and bends over when drooping.
    val height = h * (.14f + .2f * part(g, 0f) + .17f * part(g, 1f) + .13f * part(g, 2f) + .06f * part(g, 3f))
    // It droops to the left, away from the edit button beside it on the card.
    val lean = (sway * .06f - droop * .5f) * height
    val tip = ground + Offset(lean, -height * (1f - .28f * droop))
    val bend = ground + Offset(lean * .15f, -height * .55f)
    val stemPath = Path().apply { moveTo(ground.x, ground.y); quadraticTo(bend.x, bend.y, tip.x, tip.y) }
    drawPath(stemPath, stem, style = Stroke(w * .06f, cap = StrokeCap.Round))
    // Two leaves, unfolding from the sprout onwards, a little lower on the stem as it grows.
    val leafSize = w * (.2f + .1f * part(g, 1f) + .06f * part(g, 2f)) * part(g, 0f).coerceAtLeast(.4f)
    val leafAt = lerpOffset(ground, tip, .45f)
    // Angles are clockwise from pointing right: each leaf reaches up and out to its own side, and
    // sags towards the ground as the plant droops.
    val lift = 50f - 15f * part(g, 1f)
    for (angle in listOf(-lift + droop * 65f, 180f + lift - droop * 65f)) {
        withTransform({ translate(leafAt.x, leafAt.y); rotate(angle, Offset.Zero) }) {
            drawLeaf(leaf, leafSize)
        }
    }
    // A bud swells at the tip, then opens into a flower.
    val budSize = w * .13f * part(g, 2f)
    val open = part(g, 3f)
    val nod = -droop * 70f + sway * 4f
    if (budSize > 0f) rotate(nod, tip) {
        if (open < 1f) drawOval(bud.copy(alpha = 1f - open), Offset(tip.x - budSize * .7f, tip.y - budSize * 1.6f), Size(budSize * 1.4f, budSize * 1.9f))
        if (open > 0f) {
            val petal = w * .11f * open
            val reach = w * .125f * open
            val centre = tip + Offset(0f, -reach * .9f)
            for (i in 0 until 6) {
                val a = (i * 60f + sway * 6f) * PI.toFloat() / 180f
                drawCircle(bloom, petal, centre + Offset(cos(a), sin(a)) * reach)
            }
            drawCircle(core, petal * 1.05f, centre)
        }
    }
}

/** A pointed leaf [length] long, growing from the origin along the x axis. */
private fun DrawScope.drawLeaf(color: Color, length: Float) {
    val path = Path().apply {
        moveTo(0f, 0f)
        quadraticTo(length * .5f, -length * .42f, length, 0f)
        quadraticTo(length * .5f, length * .42f, 0f, 0f)
        close()
    }
    drawPath(path, color)
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)
private fun lerpOffset(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

