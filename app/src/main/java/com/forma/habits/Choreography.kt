package com.forma.habits

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * How things move in response to touch and to lists changing: the springy press, rows that slide
 * into place, new habits that sprout, and cards that cascade in as a screen opens.
 */

/** A press that dips an element and springs it back. Pass [interaction] to the clickable it belongs to. */
@Stable class Squish internal constructor(val interaction: MutableInteractionSource, internal val scale: State<Float>)

/**
 * Even a quick tap reaches the full dip before springing back: a press that only lasts a frame
 * would otherwise barely move, and the whole point is that every tap feels like it landed.
 */
@Composable fun rememberSquish(pressedScale: Float = .96f): Squish {
    val reduced = reducedMotion()
    val interaction = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(interaction, reduced) {
        if (reduced) return@LaunchedEffect
        var motion: Job? = null
        interaction.interactions.collect { event ->
            when (event) {
                is PressInteraction.Press -> {
                    motion?.cancel()
                    motion = launch { scale.animateTo(pressedScale, spring(stiffness = Spring.StiffnessHigh)) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    motion?.cancel()
                    motion = launch {
                        if (scale.value > pressedScale + .005f) scale.animateTo(pressedScale, tween(70))
                        scale.animateTo(1f, spring(dampingRatio = .4f, stiffness = Spring.StiffnessMediumLow))
                    }
                }
            }
        }
    }
    return remember(interaction) { Squish(interaction, scale.asState()) }
}

/** Applies [squish]'s press scale. Put it before any clip so the whole element dips together. */
fun Modifier.squish(squish: Squish): Modifier = graphicsLayer {
    scaleX = squish.scale.value
    scaleY = squish.scale.value
}

/**
 * How strongly a magnifying lens at the middle of this list's viewport sits over the item at [index]:
 * 1 dead centre, easing smoothly to 0 at either edge (or for an item not on screen). Reads layout
 * state, so call it inside a layer or draw block and scrolling only redraws.
 */
fun LazyListState.lensAt(index: Int): Float {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
    val half = (info.viewportEndOffset - info.viewportStartOffset) / 2f
    if (half <= 0f) return 0f
    val middle = info.viewportStartOffset + half
    val near = (1f - abs(item.offset + item.size / 2f - middle) / half).coerceIn(0f, 1f)
    return near * near * (3f - 2f * near)
}

/** The visible item whose centre is closest to the middle of the viewport, if any. */
fun LazyListState.centredIndex(): Int? {
    val info = layoutInfo
    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - middle) }?.index
}

/** Rows slide to their new places and fade in and out as a lazy list changes. */
@Composable fun LazyItemScope.listMotion(): Modifier = if (reducedMotion()) Modifier else Modifier.animateItem()

/** Remembers which keys a list has already shown, so only genuinely new rows sprout in. */
class Arrivals internal constructor(initial: Collection<String>) {
    private val shown = initial.toHashSet()
    /** True the first time [key] is offered, false ever after. */
    fun isNew(key: String): Boolean = shown.add(key)
}

/** Rows present when the list first appears never sprout; only ones added afterwards do. */
@Composable fun rememberArrivals(initial: () -> Collection<String>): Arrivals = remember { Arrivals(initial()) }

/**
 * How a new row arrives. [grow] goes from 0 for a row that is fresh, with a little springy overshoot,
 * and is 1 at once for any other. With a [seed], a seed first drops from above and lands where the
 * row will stand ([seed] runs 0 to 1 over the fall), and the row sprouts up out of it.
 */
@Stable class Sprout internal constructor(internal val grow: State<Float>, internal val seed: State<Float>?)

private const val SeedFallMillis = 460
/** New habits usually arrive as the editor sheet slides away; the seed waits until it has gone. */
internal const val SeedWaitMillis = 350L

@Composable fun rememberSprout(fresh: Boolean, seed: Boolean = false): Sprout {
    val reduced = reducedMotion()
    val growing = fresh && !reduced
    val grow = remember { Animatable(if (growing) 0f else 1f) }
    val fall = remember { Animatable(if (growing && seed) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (fall.value < 1f) {
            delay(SeedWaitMillis)
            fall.animateTo(1f, tween(SeedFallMillis, easing = LinearEasing))
        }
        if (grow.value < 1f) grow.animateTo(1f, spring(dampingRatio = .5f, stiffness = Spring.StiffnessLow))
    }
    return remember { Sprout(grow.asState(), if (seed) fall.asState() else null) }
}

/**
 * Brings a just-added row with [key] into view if it is not wholly on screen. New habits join the
 * end of their list, so scrolling to the end shows them; a row already in full view stays put.
 */
suspend fun LazyListState.revealArrival(key: Any) {
    // Give the new row a frame to be laid out before judging whether it can be seen.
    withFrameNanos { }
    val info = layoutInfo
    val row = info.visibleItemsInfo.firstOrNull { it.key == key }
    if (row != null && row.offset >= info.viewportStartOffset && row.offset + row.size <= info.viewportEndOffset) return
    animateScrollToItem((info.totalItemsCount - 1).coerceAtLeast(0))
}

private val SeedColor = Color(0xFF7A5A3E)

/** A new row sprouts up from its bottom edge, like a seedling, after its seed (if any) has landed. */
fun Modifier.sprouted(sprout: Sprout): Modifier = drawWithContent {
    drawContent()
    val fall = sprout.seed?.value ?: return@drawWithContent
    val g = sprout.grow.value
    // Gone once the row has sprouted a little way.
    val fade = 1f - (g / .35f).coerceIn(0f, 1f)
    if (fall <= 0f || fade <= 0f) return@drawWithContent
    // Falls under gravity for most of its time, then bounces once on landing.
    val landAt = .78f
    val ground = size.height - 7.dp.toPx()
    val drop = 110.dp.toPx()
    val y = if (fall < landAt) ground - drop * (1f - (fall / landAt).let { it * it })
        else ground - 9.dp.toPx() * sin(PI.toFloat() * (fall - landAt) / (1f - landAt))
    val r = 5.dp.toPx()
    val at = Offset(size.width / 2, y)
    drawOval(SeedColor.copy(alpha = fade * (fall / .12f).coerceAtMost(1f)), Offset(at.x - r * .75f, at.y - r), Size(r * 1.5f, r * 2f))
}.graphicsLayer {
    val g = sprout.grow.value
    transformOrigin = TransformOrigin(.5f, 1f)
    scaleX = .55f + .45f * g
    scaleY = .25f + .75f * g
    alpha = g.coerceIn(0f, 1f)
}

private const val CascadeStepMillis = 45L
private const val CascadeWindowMillis = 650L

/**
 * Staggers a screen's cards in as it opens. Anything composed after the opening moment, such as a
 * card scrolled into view later, simply appears: a cascade on every scroll would get tiring fast.
 */
class Cascade internal constructor(internal val openedAt: Long, internal val enabled: Boolean)

@Composable fun rememberCascade(): Cascade {
    val reduced = reducedMotion()
    return remember { Cascade(SystemClock.uptimeMillis(), !reduced) }
}

@Composable fun Cascade.entrance(index: Int): State<Float> {
    val elapsed = SystemClock.uptimeMillis() - openedAt
    val entry = remember { Animatable(if (!enabled || elapsed > CascadeWindowMillis) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (entry.value >= 1f) return@LaunchedEffect
        delay((index * CascadeStepMillis - elapsed).coerceAtLeast(0L))
        entry.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    return entry.asState()
}

/** Rises a little and fades in with its [entrance]. */
fun Modifier.cascaded(entrance: State<Float>): Modifier = graphicsLayer {
    val e = entrance.value
    alpha = e
    translationY = (1f - e) * 26.dp.toPx()
}
