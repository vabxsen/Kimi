package com.forma.habits

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/** Grows from 0 for a row that is [fresh], with a little springy overshoot; 1 at once for any other. */
@Composable fun rememberSprout(fresh: Boolean): State<Float> {
    val reduced = reducedMotion()
    val grow = remember { Animatable(if (fresh && !reduced) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (grow.value < 1f) grow.animateTo(1f, spring(dampingRatio = .5f, stiffness = Spring.StiffnessLow))
    }
    return grow.asState()
}

/** A new row sprouts up from its bottom edge, like a seedling. */
fun Modifier.sprouted(grow: State<Float>): Modifier = graphicsLayer {
    val g = grow.value
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
