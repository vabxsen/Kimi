package com.forma.habits

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Picking a theme or a light/dark mode spreads the new colours out in a circle from the finger,
 * instead of the whole app fading at once. At the tap, the screen is drawn once into a picture in its
 * old colours; the new theme is applied at once underneath; then the picture is cut away by a growing
 * circle, so the new look spreads outward. Nothing is recorded at any other time.
 */
@Stable class ThemeRipple internal constructor(private val scope: CoroutineScope, private val enabled: Boolean) {
    internal var capturing by mutableStateOf(false)
    internal var old by mutableStateOf<ImageBitmap?>(null)
    internal var origin = Offset.Zero
    internal val progress = Animatable(0f)
    internal var layer: GraphicsLayer? = null
    private var job: Job? = null

    /** Applies [change] (a theme or mode switch), rippling it out from [at] in root coordinates. */
    fun change(at: Offset, change: () -> Unit) {
        val target = layer
        if (!enabled || target == null) { change(); return }
        job?.cancel()
        job = scope.launch {
            // Two frames: the first draws the screen into the layer, the second finds it recorded.
            capturing = true
            val shot = try { withFrameNanos { }; withFrameNanos { }; target.toImageBitmap() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
                finally { capturing = false }
            if (shot == null) { change(); return@launch }
            old = shot; origin = at
            progress.snapTo(0f)
            ThemeBlend.instant = true
            change()
            try { progress.animateTo(1f, tween(RippleMillis, easing = FastOutSlowInEasing)) }
            finally { old = null; ThemeBlend.instant = false }
        }
    }
}

private const val RippleMillis = 650

internal val LocalThemeRipple = staticCompositionLocalOf<ThemeRipple?> { null }

/** Hosts the ripple over the whole app. Put it inside the theme, around everything else. */
@Composable fun ThemeRippleHost(content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val reduced = reducedMotion()
    val ripple = remember(reduced) { ThemeRipple(scope, !reduced) }
    val layer = rememberGraphicsLayer()
    ripple.layer = layer
    val corner = remember { arrayOf(Offset.Zero) }
    val edge = Accent
    Box(Modifier.fillMaxSize()
        .onGloballyPositioned { corner[0] = it.positionInRoot() }
        .drawWithContent {
            if (ripple.capturing) {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            } else drawContent()
            val picture = ripple.old ?: return@drawWithContent
            val at = ripple.origin - corner[0]
            val reach = maxOf(hypot(at.x, at.y), hypot(size.width - at.x, at.y), hypot(at.x, size.height - at.y), hypot(size.width - at.x, size.height - at.y))
            val radius = reach * ripple.progress.value
            val hole = Path().apply { addOval(androidx.compose.ui.geometry.Rect(at, radius)) }
            clipPath(hole, ClipOp.Difference) { drawImage(picture) }
            // A soft line in the new accent rides the edge of the spreading colour.
            drawCircle(edge.copy(alpha = .35f * (1f - ripple.progress.value)), radius, at, style = Stroke(3.dp.toPx()))
        }) {
        CompositionLocalProvider(LocalThemeRipple provides ripple) { content() }
    }
}
