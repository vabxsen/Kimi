package com.forma.habits

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** Taps this close together count towards making the flower dizzy. */
private const val TapWindowMillis = 1500L
/** This many quick taps in a row and the flower spins itself dizzy. */
internal const val DizzyTaps = 4

/**
 * Keeps the last quick taps, so a burst of them can be told apart from the odd poke. Returns true
 * when [now] completes a burst of [DizzyTaps], and starts counting afresh after one.
 */
internal class TapBurst {
    private val times = ArrayDeque<Long>()
    fun tap(now: Long): Boolean {
        while (times.isNotEmpty() && now - times.first() > TapWindowMillis) times.removeFirst()
        times.addLast(now)
        if (times.size < DizzyTaps) return false
        times.clear()
        return true
    }
}

/**
 * The flower on Today's banner, with a personality. Poke it and it giggles: a wobble, a blush and a
 * twirl of its petals. Poke it four times quickly and it spins itself dizzy, spiral-eyed, for a
 * moment. On a day with nothing scheduled it naps, Z's drifting up, and when a habit arrives for
 * the day it blinks awake with a little hop.
 *
 * The taps carry no meaning, so they are not announced as a button; the flower stays decoration.
 */
@Composable fun Mascot(modifier: Modifier = Modifier, progress: Float? = null, sleeping: Boolean = false,
    petal: Color = Highlight, petals: List<Color>? = null) {
    val reduced = reducedMotion()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val giggle = remember { Animatable(1f) }
    val spin = remember { Animatable(0f) }
    val dizzy = remember { Animatable(0f) }
    val asleep = remember { Animatable(if (sleeping) 1f else 0f) }
    val hop = remember { Animatable(1f) }
    val burst = remember { TapBurst() }
    var wasSleeping by remember { mutableStateOf(sleeping) }
    LaunchedEffect(sleeping) {
        val woke = wasSleeping && !sleeping
        wasSleeping = sleeping
        if (reduced) { asleep.snapTo(if (sleeping) 1f else 0f); return@LaunchedEffect }
        if (woke) {
            launch { hop.snapTo(0f); hop.animateTo(1f, tween(520, easing = LinearEasing)) }
            asleep.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
        } else asleep.animateTo(if (sleeping) 1f else 0f, tween(700, easing = FastOutSlowInEasing))
    }
    val zzz = if (sleeping && !reduced) rememberInfiniteTransition(label = "zzz")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "zzz drift").value
        else if (sleeping) .35f else null
    val g = giggle.value
    Flower(
        modifier
            .pointerInput(reduced, sleeping) {
                detectTapGestures {
                    haptics.play(Feel.Tick)
                    val dizzyNow = burst.tap(SystemClock.uptimeMillis())
                    // Its voice: a giggle when poked, a yawn when poked awake, a "wheee" when spun.
                    when {
                        dizzyNow && !reduced -> Sound.wheee()
                        sleeping -> Sound.yawn()
                        else -> Sound.giggle()
                    }
                    if (reduced) return@detectTapGestures
                    if (dizzyNow) {
                        haptics.play(Feel.Thud)
                        scope.launch { spin.animateTo(spin.value + 720f, tween(1300, easing = FastOutSlowInEasing)) }
                        scope.launch {
                            dizzy.animateTo(1f, tween(160))
                            delay(1900)
                            dizzy.animateTo(0f, tween(320))
                        }
                    } else {
                        scope.launch { giggle.snapTo(0f); giggle.animateTo(1f, tween(760, easing = LinearEasing)) }
                        scope.launch { spin.animateTo(spin.value + 60f, spring(dampingRatio = .45f, stiffness = Spring.StiffnessLow)) }
                    }
                }
            }
            .graphicsLayer {
                // A wobble that dies away, with a squash and stretch on the same beat.
                val fade = 1f - g
                val wave = sin(g * 4f * PI.toFloat()) * fade
                transformOrigin = TransformOrigin(.5f, .9f)
                rotationZ = wave * 13f + sin(dizzy.value * PI.toFloat() * 6f) * 6f * dizzy.value
                scaleX = 1f + .07f * wave
                scaleY = 1f - .07f * wave
                translationY = -sin(hop.value * PI.toFloat()) * 10.dp.toPx()
            },
        petal = petal,
        petals = petals,
        progress = progress,
        alive = true,
        asleep = asleep.value,
        zzz = zzz,
        dizzy = dizzy.value,
        blush = maxOf(sin(g * PI.toFloat()), dizzy.value * .6f),
        spin = spin.value
    )
}
