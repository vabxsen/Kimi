package com.forma.habits

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

/**
 * Kimi's haptic vocabulary, one feel per kind of moment, so a buzz always means the same thing:
 * a light tick for passing something, a crisp click for switching a habit on, a softer one for
 * switching it off, a catch when a swipe goes far enough, and a thud for the big moments.
 *
 * It goes through [View.performHapticFeedback], so it follows the phone's own touch-feedback
 * setting and needs no vibration permission. Newer feels fall back to the nearest older one.
 */
enum class Feel { Tick, On, Off, Threshold, Confirm, Thud }

@Stable class KimiHaptics internal constructor(private val view: View) {
    fun play(feel: Feel) { view.performHapticFeedback(constantFor(feel)) }

    /** Plays each feel at its offset in milliseconds from now, in order, timed to an animation. */
    suspend fun pattern(vararg beats: Pair<Long, Feel>) {
        var at = 0L
        beats.sortedBy { it.first }.forEach { (offset, feel) ->
            delay(offset - at); at = offset
            play(feel)
        }
    }

    private fun constantFor(feel: Feel): Int {
        val sdk = Build.VERSION.SDK_INT
        return when (feel) {
            Feel.Tick -> if (sdk >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK
            Feel.On -> if (sdk >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.VIRTUAL_KEY
            Feel.Off -> if (sdk >= 34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
            Feel.Threshold -> when {
                sdk >= 34 -> HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                sdk >= 30 -> HapticFeedbackConstants.GESTURE_START
                else -> HapticFeedbackConstants.CONTEXT_CLICK
            }
            Feel.Confirm -> if (sdk >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            Feel.Thud -> HapticFeedbackConstants.LONG_PRESS
        }
    }
}

@Composable fun rememberHaptics(): KimiHaptics {
    val view = LocalView.current
    return remember(view) { KimiHaptics(view) }
}

/** The day's last habit ticked: a confirm as the confetti starts, then a thud as the flower pops. */
internal val DayCompleteBeats = arrayOf(0L to Feel.Confirm, 170L to Feel.Tick, 470L to Feel.Thud)

/** A streak milestone: a thud as the badge lands, ticks as the ribbon unrolls, a confirm to finish. */
internal val MilestoneBeats = arrayOf(0L to Feel.Thud, 300L to Feel.Tick, 420L to Feel.Tick, 540L to Feel.Tick, 900L to Feel.Confirm)
