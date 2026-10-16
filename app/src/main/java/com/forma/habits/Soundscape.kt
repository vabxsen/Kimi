package com.forma.habits

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/** The parts of the day the soundscape changes with. */
internal enum class SkyPhase { Dawn, Day, Dusk, Night }

internal fun skyPhase(hour: Int): SkyPhase = when (hour) {
    in 5..8 -> SkyPhase.Dawn
    in 9..16 -> SkyPhase.Day
    in 17..19 -> SkyPhase.Dusk
    else -> SkyPhase.Night
}

/**
 * Today's soundscape, following the sky: birds at dawn, a warm hum through the day with the odd
 * bird, the hum and the first crickets at dusk, and crickets in the night. In winter the birds and
 * crickets have gone, and a soft wind takes their place.
 */
internal fun skyBed(phase: SkyPhase, season: Season): Bed {
    val winter = season == Season.Winter
    val layers = when (phase) {
        SkyPhase.Dawn -> if (winter) listOf(Wind(31, .5)) else listOf(Birds(32, 2.2), Wind(31, .2))
        SkyPhase.Day -> if (winter) listOf(Hum(33), Wind(31, .4)) else listOf(Hum(33), Birds(32, .45, .35f))
        SkyPhase.Dusk -> if (winter) listOf(Hum(33), Wind(31, .5)) else listOf(Hum(33), Crickets(34, .5))
        SkyPhase.Night -> if (winter) listOf(Wind(31, .6)) else listOf(Crickets(34), Wind(31, .15))
    }
    return Layers(layers)
}

/**
 * What the garden sounds like: always a little breeze (a real wind in winter), birdsong once the
 * birdhouse is up, the pond's trickle once it is dug (frozen quiet in winter), and crickets at night.
 */
internal fun gardenBed(report: GameReport, night: Boolean): Bed {
    val winter = report.season.season == Season.Winter
    val layers = mutableListOf<Bed>(Wind(41, if (winter) .9 else .3))
    if (report.unlocked(Unlock.Birdhouse) && !night && !winter) layers += Birds(42, 1.3)
    if (report.unlocked(Unlock.Pond) && !winter) layers += Pond(43)
    if (night && !winter) layers += Crickets(44)
    return Layers(layers)
}

/** Anything with its own sound (the garden, a focus session) quiets Today's soundscape while it is open. */
internal object Soundscape {
    var ducks by mutableIntStateOf(0)
}

@Composable internal fun DuckSoundscape() {
    DisposableEffect(Unit) {
        Soundscape.ducks++
        onDispose { Soundscape.ducks-- }
    }
}

/** The hour, kept current once a minute, for sounds that follow the time of day. */
@Composable internal fun currentHour(): Int {
    val hour by produceState(LocalTime.now().hour) { while (true) { delay(60_000); value = LocalTime.now().hour } }
    return hour
}

/** Today's soundscape, playing under the whole app while it is switched on. */
@Composable fun TodaySoundscape(today: LocalDate) {
    val phase = skyPhase(currentHour())
    val season = seasonFor(today.monthValue)
    AmbientBed(phase to season, SoundSetting.soundscape && Soundscape.ducks == 0 && Sound.ringerOn()) { skyBed(phase, season) }
}
