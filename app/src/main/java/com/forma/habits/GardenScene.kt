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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The garden itself, as a little living picture: a sky that follows the time of day over hills in
 * the season's colours, every habit's plant growing in a row, and whatever the garden's level has
 * unlocked: wildflowers, butterflies, a fence, a pond, a tree, stepping stones, a birdhouse,
 * lanterns and finally a meadow. The season's collected items lie scattered on the ground, more of
 * them as more are collected. Everything moves gently; with animations off it rests in place.
 */

/** One habit's plant in the scene: how grown it is (0-4) and whether it is drooping. */
class ScenePlant(val stage: Int, val wilting: Boolean)

private const val SceneLoopMillis = 8_000

@Composable fun GardenScene(plants: List<ScenePlant>, report: GameReport, modifier: Modifier = Modifier, now: () -> LocalTime = LocalTime::now) {
    val reduced = reducedMotion()
    val loop = if (!reduced) rememberInfiniteTransition(label = "garden scene")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(SceneLoopMillis, easing = LinearEasing)), label = "garden loop") else null
    val palette = LocalKimiPalette.current
    val colours = remember(palette.mono) { plantColors(palette.mono) }
    val label = pluralStringResource(R.plurals.cd_garden_scene, plants.size, plants.size)
    Canvas(modifier.semantics { contentDescription = label }) {
        val t = loop?.value ?: .25f
        val time = now()
        val minute = time.hour * 60f + time.minute
        drawScene(plants, report, t, minute, palette, colours)
    }
}

private fun mono(c: Color, on: Boolean): Color = if (!on) c else { val l = c.luminance(); Color(l, l, l, c.alpha) }

/** Ground colours for each season: the far hill, then the near one. */
private fun groundFor(season: Season): Pair<Color, Color> = when (season) {
    Season.Spring -> Color(0xFFA8D8A0) to Color(0xFF8CCB82)
    Season.Summer -> Color(0xFF9BCF84) to Color(0xFF7DBE6A)
    Season.Autumn -> Color(0xFFD8C27A) to Color(0xFFC7A85F)
    Season.Winter -> Color(0xFFE3EAF3) to Color(0xFFF6F9FC)
}

private fun DrawScope.drawScene(plants: List<ScenePlant>, report: GameReport, t: Float, minute: Float, palette: KimiPalette, colours: PlantColors) {
    val w = size.width; val h = size.height
    val night = nightness(minute)
    val dark = palette.dark
    val m = palette.mono
    val season = report.season.season
    fun has(unlock: Unlock) = report.unlocked(unlock)
    fun shade(c: Color) = mono(if (dark) lerp(c, Color(0xFF1B2230), .5f) else lerp(c, Color(0xFF2A3350), .32f * night), m)
    val turn = 2f * PI.toFloat() * t
    // The two hills: far and near. Their tops are gentle waves, also used to stand things on.
    fun farTop(x: Float) = h * (.52f + .04f * sin(2f * PI.toFloat() * x / w * .8f + 2.2f))
    fun nearTop(x: Float) = h * (.66f + .035f * sin(2f * PI.toFloat() * x / w + .6f))
    fun hill(top: (Float) -> Float) = Path().apply {
        moveTo(0f, h); lineTo(0f, top(0f))
        for (i in 1..32) { val x = w * i / 32f; lineTo(x, top(x)) }
        lineTo(w, h); close()
    }
    val (far, near) = groundFor(season)

    // Sky, with the sun or the moon and a few stars.
    val (skyTop, skyLow) = skyColors(minute, palette)
    drawRect(Brush.verticalGradient(listOf(skyTop, skyLow), 0f, h * .62f))
    val body = Offset(w * .86f, h * .17f)
    if (night < .5f) {
        drawCircle(Brush.radialGradient(listOf(mono(Color(0xFFFFE08A), m).copy(alpha = .55f), Color.Transparent), body, 26.dp.toPx()), 26.dp.toPx(), body)
        drawCircle(mono(Color(0xFFFFCF5C), m), 11.dp.toPx(), body)
    } else {
        val disc = Path().apply { addOval(Rect(body, 10.dp.toPx())) }
        val bite = Path().apply { addOval(Rect(body + Offset(4.dp.toPx(), -3.dp.toPx()), 9.dp.toPx())) }
        drawPath(Path().apply { op(disc, bite, PathOperation.Difference) }, Color(0xFFF3E9C6))
        listOf(.12f to .1f, .3f to .22f, .5f to .08f, .64f to .26f, .42f to .34f).forEachIndexed { i, (x, y) ->
            val glow = .4f + .6f * (.5f + .5f * sin(turn * 2f + i))
            drawSparkle(Color(0xFFF3E9C6).copy(alpha = glow * night), Offset(w * x, h * y), 3.dp.toPx())
        }
    }

    drawPath(hill(::farTop), shade(far))

    // The fence along the far hill, with lanterns strung between its posts.
    if (has(Unlock.Fence)) {
        val wood = shade(Color(0xFFA9805A))
        val posts = 9
        val postH = 20.dp.toPx()
        val tops = (0..posts).map { i -> val x = w * (.04f + .92f * i / posts); Offset(x, farTop(x) + 6.dp.toPx() - postH) }
        tops.forEach { p -> drawLine(wood, p, p + Offset(0f, postH), 3.dp.toPx(), StrokeCap.Round) }
        for (rail in listOf(.3f, .7f)) {
            for (i in 0 until posts) drawLine(wood, tops[i] + Offset(0f, postH * rail), tops[i + 1] + Offset(0f, postH * rail), 2.dp.toPx())
        }
        if (has(Unlock.Lanterns)) {
            val warm = mono(Color(0xFFFFC861), m)
            for (i in 0 until posts) {
                val a = tops[i]; val b = tops[i + 1]
                val sag = Offset((a.x + b.x) / 2, (a.y + b.y) / 2 + 7.dp.toPx())
                drawPath(Path().apply { moveTo(a.x, a.y); quadraticTo(sag.x, sag.y, b.x, b.y) }, wood.copy(alpha = .7f), style = Stroke(1.dp.toPx()))
                val lamp = Offset(sag.x, (a.y + b.y) / 2 + 4.dp.toPx())
                val flicker = .75f + .25f * sin(turn * 3f + i * 1.7f)
                val glow = (.25f + .75f * night) * flicker
                drawCircle(Brush.radialGradient(listOf(warm.copy(alpha = .6f * glow), Color.Transparent), lamp, 11.dp.toPx()), 11.dp.toPx(), lamp)
                drawCircle(warm.copy(alpha = .6f + .4f * glow), 3.dp.toPx(), lamp)
            }
        }
    }

    drawPath(hill(::nearTop), shade(near))

    // The season's collected items lying about: leaves, petals, sunbeams or snow sparkle.
    val scattered = report.collected.coerceAtMost(40)
    val items = Random(report.season.year * 4 + season.ordinal)
    repeat(scattered) {
        val x = w * (.02f + .96f * items.nextFloat())
        val y = nearTop(x) + (h - nearTop(x)) * (.15f + .8f * items.nextFloat())
        val turnBy = items.nextFloat() * 360f
        val size = (3.5f + 2f * items.nextFloat()).dp.toPx()
        when (season) {
            Season.Autumn -> rotate(turnBy, Offset(x, y)) {
                drawOval(shade(listOf(Color(0xFFE8833A), Color(0xFFC9503A), Color(0xFFE3B23C))[it % 3]), Offset(x - size, y - size / 2), Size(size * 2, size))
            }
            Season.Spring -> rotate(turnBy, Offset(x, y)) {
                drawOval(shade(Color(0xFFF7A8C4)), Offset(x - size * .8f, y - size / 2), Size(size * 1.6f, size))
            }
            Season.Summer -> drawSparkle(shade(Color(0xFFFFD54F)).copy(alpha = .9f), Offset(x, y), size)
            Season.Winter -> drawSparkle(Color(0xFFB9D7F0).copy(alpha = .9f), Offset(x, y), size * .9f)
        }
    }

    // Stepping stones winding in from the front.
    if (has(Unlock.StonePath)) {
        val stone = shade(Color(0xFFBFB8AE))
        listOf(.36f to .97f, .41f to .9f, .38f to .83f, .44f to .77f).forEach { (x, y) ->
            drawOval(stone, Offset(w * x - 11.dp.toPx(), h * y - 4.dp.toPx()), Size(22.dp.toPx(), 8.dp.toPx()))
        }
    }

    // Wildflowers, then a whole meadow of them.
    val flowers = (if (has(Unlock.Wildflowers)) 14 else 0) + (if (has(Unlock.Meadow)) 40 else 0)
    val bloom = Random(7)
    val petals = listOf(Color(0xFFFFFFFF), Color(0xFFFFE696), Color(0xFFF7A8C4), Color(0xFFD5C4F5), Color(0xFFFFC3A0))
    repeat(flowers) {
        val x = w * (.02f + .96f * bloom.nextFloat())
        val y = nearTop(x) + (h - nearTop(x)) * (.08f + .85f * bloom.nextFloat())
        val r = (2f + 1.2f * bloom.nextFloat()).dp.toPx()
        val c = shade(petals[it % petals.size])
        val stem = Offset(x, y)
        drawLine(shade(Color(0xFF4F8F3E)), stem, stem + Offset(0f, r * 2.2f), r * .45f)
        for (k in 0 until 5) {
            val a = k * 72f * PI.toFloat() / 180f + it
            drawCircle(c, r * .62f, stem + Offset(cos(a), sin(a)) * r * .75f)
        }
        drawCircle(shade(Color(0xFFFFB347)), r * .45f, stem)
    }

    // The pond, frozen over in winter, rippling the rest of the year.
    val pondAt = Offset(w * .78f, h * .86f)
    if (has(Unlock.Pond)) {
        val water = shade(if (season == Season.Winter) Color(0xFFCFE6F5) else Color(0xFF8EC9EE))
        drawOval(water, pondAt - Offset(w * .14f, h * .06f), Size(w * .28f, h * .12f))
        drawOval(shade(Color(0xFFFFFFFF)).copy(alpha = .35f), pondAt - Offset(w * .08f, h * .035f), Size(w * .09f, h * .022f))
        if (season != Season.Winter) for (k in 0..1) {
            val ripple = (t * 2f + k * .5f) % 1f
            drawOval(Color.White.copy(alpha = .5f * (1f - ripple)), pondAt - Offset(w * .05f * ripple + 2f, h * .02f * ripple + 1f),
                Size(w * .1f * ripple + 4f, h * .04f * ripple + 2f), style = Stroke(1.2.dp.toPx()))
        }
    }

    // The birdhouse on its pole, with a bird that hops on the roof by day.
    if (has(Unlock.Birdhouse)) {
        val wood = shade(Color(0xFFB98A5E))
        val base = Offset(w * .93f, nearTop(w * .93f) + 4.dp.toPx())
        val top = base - Offset(0f, h * .3f)
        drawLine(wood, base, top, 3.dp.toPx())
        val box = 18.dp.toPx()
        drawRoundRect(wood, top - Offset(box / 2, box), Size(box, box), CornerRadius(3.dp.toPx()))
        drawPath(Path().apply {
            moveTo(top.x - box * .7f, top.y - box); lineTo(top.x, top.y - box * 1.6f); lineTo(top.x + box * .7f, top.y - box); close()
        }, shade(Color(0xFFC9503A)))
        drawCircle(shade(Color(0xFF5B4636)), box * .16f, top - Offset(0f, box * .55f))
        if (night < .5f) {
            val hop = abs(sin(turn * 2f)) * 4.dp.toPx()
            val bird = top - Offset(-box * .1f, box * 1.62f + hop + 4.dp.toPx())
            drawCircle(mono(Color(0xFF6FA8DC), m), 4.dp.toPx(), bird)
            drawCircle(mono(Color(0xFF6FA8DC), m), 2.6.dp.toPx(), bird + Offset(3.5.dp.toPx(), -2.dp.toPx()))
            drawCircle(mono(Color(0xFFFFB347), m), 1.dp.toPx(), bird + Offset(6.dp.toPx(), -2.dp.toPx()))
        }
    }

    // The tree on the left, dressed for the season.
    if (has(Unlock.Tree)) {
        val foot = Offset(w * .1f, nearTop(w * .1f) + 6.dp.toPx())
        val crown = Offset(w * .1f, h * .3f)
        drawLine(shade(Color(0xFF8B6244)), foot, crown + Offset(0f, 14.dp.toPx()), 9.dp.toPx(), StrokeCap.Round)
        val leaves = when (season) {
            Season.Spring -> listOf(Color(0xFF8CCB82), Color(0xFFF7B6C8))
            Season.Summer -> listOf(Color(0xFF5FA84E), Color(0xFF79BF63))
            Season.Autumn -> listOf(Color(0xFFE8873A), Color(0xFFD8653A))
            Season.Winter -> emptyList()
        }
        val sway = sin(turn) * 1.5.dp.toPx()
        if (leaves.isEmpty()) {
            // Bare branches with snow along them.
            val wood = shade(Color(0xFF8B6244))
            listOf(-35f, -10f, 20f, 40f).forEachIndexed { i, a ->
                val end = crown + Offset(sin(a * PI.toFloat() / 180f), -cos(a * PI.toFloat() / 180f)) * (26f + i * 4).dp.toPx()
                drawLine(wood, crown + Offset(0f, 14.dp.toPx()), end, 3.dp.toPx(), StrokeCap.Round)
                drawCircle(Color.White, 3.dp.toPx(), end)
            }
        } else listOf(Offset(0f, 0f) to 24f, Offset(-16f, 10f) to 17f, Offset(16f, 10f) to 17f, Offset(0f, -16f) to 16f).forEachIndexed { i, (o, r) ->
            drawCircle(shade(leaves[i % leaves.size]), r.dp.toPx(), crown + Offset(o.x.dp.toPx() + sway, o.y.dp.toPx()))
        }
    }

    // Every habit's plant, in a row across the near hill, dimmed with the rest of the scene after dark.
    val plantTones = if (dark || night > 0f) PlantColors(shade(colours.soil), shade(colours.stem), shade(colours.leaf),
        shade(colours.bud), shade(colours.bloom), shade(colours.core)) else colours
    if (plants.isNotEmpty()) {
        val from = w * (if (has(Unlock.Tree)) .2f else .06f)
        val to = w * (if (has(Unlock.Pond)) .64f else .9f)
        val slot = (to - from) / plants.size
        val pw = slot.coerceIn(26.dp.toPx(), 48.dp.toPx())
        val ph = pw * 1.35f
        plants.forEachIndexed { i, plant ->
            val x = from + slot * (i + .5f)
            val ground = nearTop(x) + 10.dp.toPx() + (i % 2) * 6.dp.toPx()
            withTransform({ translate(x - pw / 2, ground - ph * .9f) }) {
                drawPlant(plant.stage.toFloat(), if (plant.wilting && plant.stage > 0) 1f else 0f, sin(turn + i * 1.3f), plantTones, pw, ph, soilShown = true)
            }
        }
    }

    // Butterflies by day, fireflies on nights outside winter.
    if (has(Unlock.Butterflies) && (night < .5f || season != Season.Winter)) {
        if (night < .5f) listOf(Color(0xFFF48FB1), Color(0xFFFFD54F)).forEachIndexed { i, c ->
            val phase = turn + i * PI.toFloat()
            val at = Offset(w * (.35f + .22f * sin(phase) + .1f * i), h * (.44f + .1f * sin(phase * 2f)))
            val flap = abs(sin(turn * 9f + i))
            val wing = 5.dp.toPx()
            val tone = mono(c, m)
            drawOval(tone, at - Offset(wing * (.2f + .9f * flap), wing * .8f), Size(wing * (.2f + .9f * flap), wing * 1.4f))
            drawOval(tone, at - Offset(0f, wing * .8f), Size(wing * (.2f + .9f * flap), wing * 1.4f))
            drawLine(Color(0xFF33283E), at - Offset(0f, wing * .6f), at + Offset(0f, wing * .5f), 1.2.dp.toPx())
        } else repeat(6) { i ->
            val phase = turn + i
            val at = Offset(w * (.2f + .12f * i + .03f * sin(phase * 2f)), h * (.5f + .08f * sin(phase + i)))
            val blink = (sin(turn * 4f + i * 1.9f) * .5f + .5f).let { it * it }
            val glow = mono(Color(0xFFDDF27A), m)
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = .6f * blink), Color.Transparent), at, 8.dp.toPx()), 8.dp.toPx(), at)
            drawCircle(glow.copy(alpha = blink), 1.6.dp.toPx(), at)
        }
    }
}
