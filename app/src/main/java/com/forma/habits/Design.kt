package com.forma.habits

import android.content.Context
import androidx.core.content.edit
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every colour the app draws with, resolved per theme. Screens keep referring to the token
 * names (`Cream`, `Ink`, `Purple`…) so a light/dark swap never touches a call site.
 */
@Immutable
data class KimiPalette(
    val cream: Color, val surface: Color, val ink: Color, val onInk: Color, val quiet: Color,
    val purple: Color, val accent: Color, val onAccent: Color, val highlight: Color,
    val overlay: Color, val petal: Color, val danger: Color,
    val lavender: Color, val blue: Color, val yellow: Color, val mint: Color, val pink: Color, val peach: Color,
    val verified: Color,
    /** The slightly lifted surface Material uses for dialogs and sheets. */
    val raised: Color,
    val dark: Boolean,
    /** Monochrome: the mascot, the sun and the confetti drop their colour too, not just the cards. */
    val mono: Boolean = false
) {
    val tiles: List<Color> get() = listOf(lavender, blue, yellow, mint, pink, peach)
}

internal val LightPalette = KimiPalette(
    cream = Color(0xFFEDE7DF), surface = Color(0xFFF8F4EE), ink = Color(0xFF30243A), onInk = Color.White,
    quiet = Color(0xFF514459), purple = Color(0xFF6847D6), accent = Color(0xFF6847D6),
    onAccent = Color.White, highlight = Color(0xFFFFE696), overlay = Color(0xFFF8F4EE).copy(alpha = .50f),
    petal = Color.White.copy(alpha = .85f), danger = Color(0xFF9C463A),
    lavender = Color(0xFFCDBDEB), blue = Color(0xFFAED1E5), yellow = Color(0xFFF1CE69),
    mint = Color(0xFFA8D2BB), pink = Color(0xFFE5B5CD), peach = Color(0xFFEDBC9E),
    verified = Color(0xFF1D9BF0), raised = Color(0xFFE2D8EB), dark = false
)

/** Same personality after dark: the tiles keep their hue, they just carry light text instead. */
private val DarkPalette = KimiPalette(
    cream = Color(0xFF14101A), surface = Color(0xFF211A2B), ink = Color(0xFFF5EFFA), onInk = Color(0xFF1A1422),
    quiet = Color(0xFFC0B3CC), purple = Color(0xFF744FE0), accent = Color(0xFFC7B1FF),
    onAccent = Color.White, highlight = Color(0xFFFFE696), overlay = Color.White.copy(alpha = .12f),
    petal = Color(0xFFE9B8D4), danger = Color(0xFFFF9E8F),
    lavender = Color(0xFF3A2F57), blue = Color(0xFF1E3A4C), yellow = Color(0xFF4A3A15),
    mint = Color(0xFF1F4434), pink = Color(0xFF46243A), peach = Color(0xFF4E3020),
    verified = Color(0xFF4FB5F7), raised = Color(0xFF2C2338), dark = true
)

/** Ink on paper: six greys of different lightness keep habits apart without any hue. */
internal val MonochromeLightPalette = KimiPalette(
    cream = Color(0xFFEFEFED), surface = Color(0xFFFAFAF9), ink = Color(0xFF1C1C1E), onInk = Color.White,
    quiet = Color(0xFF4C4C51), purple = Color(0xFF2B2B2E), accent = Color(0xFF2B2B2E),
    onAccent = Color.White, highlight = Color(0xFFE4E4E6), overlay = Color(0xFFFAFAF9).copy(alpha = .55f),
    petal = Color.White.copy(alpha = .85f), danger = Color(0xFF8E3B33),
    lavender = Color(0xFFDCDCDE), blue = Color(0xFFCFCFD2), yellow = Color(0xFFE6E6E3),
    mint = Color(0xFFC6C7C6), pink = Color(0xFFD7D5D7), peach = Color(0xFFBDBDBF),
    verified = Color(0xFF3A3A3D), raised = Color(0xFFE2E2E4), dark = false, mono = true
)

private val MonochromeDarkPalette = KimiPalette(
    cream = Color(0xFF111113), surface = Color(0xFF1D1D20), ink = Color(0xFFF2F2F3), onInk = Color(0xFF141416),
    quiet = Color(0xFFB4B4B9), purple = Color(0xFF3E3E43), accent = Color(0xFFD9D9DD),
    onAccent = Color.White, highlight = Color(0xFFE8E8EA), overlay = Color.White.copy(alpha = .10f),
    petal = Color(0xFFDADADD), danger = Color(0xFFE39A90),
    lavender = Color(0xFF2C2C30), blue = Color(0xFF333337), yellow = Color(0xFF3B3B3F),
    mint = Color(0xFF2F3031), pink = Color(0xFF38363A), peach = Color(0xFF424246),
    verified = Color(0xFFBDBDC2), raised = Color(0xFF2A2A2E), dark = true, mono = true
)

/** Sea glass and deep water: teal fills, aqua petals, sky and sand tiles. */
internal val OceanLightPalette = KimiPalette(
    cream = Color(0xFFE5EEF1), surface = Color(0xFFF5F9FA), ink = Color(0xFF14303A), onInk = Color.White,
    quiet = Color(0xFF3B5761), purple = Color(0xFF1F6F8B), accent = Color(0xFF1C6884),
    onAccent = Color.White, highlight = Color(0xFFBFF0E6), overlay = Color(0xFFF5F9FA).copy(alpha = .50f),
    petal = Color.White.copy(alpha = .85f), danger = Color(0xFFA1433A),
    lavender = Color(0xFFB9D3EA), blue = Color(0xFFA6DBE3), yellow = Color(0xFFF0DEA4),
    mint = Color(0xFFA6D7C2), pink = Color(0xFFC5C8EE), peach = Color(0xFFF1C6B3),
    verified = Color(0xFF1D9BF0), raised = Color(0xFFD3E2E8), dark = false
)

private val OceanDarkPalette = KimiPalette(
    cream = Color(0xFF0C161B), surface = Color(0xFF15242B), ink = Color(0xFFEAF6F9), onInk = Color(0xFF0E1A1F),
    quiet = Color(0xFFA7C2CA), purple = Color(0xFF237995), accent = Color(0xFF8ED2E7),
    onAccent = Color.White, highlight = Color(0xFFBFF0E6), overlay = Color.White.copy(alpha = .12f),
    petal = Color(0xFF9ED9E6), danger = Color(0xFFFF9E8F),
    lavender = Color(0xFF1B3A4F), blue = Color(0xFF15404A), yellow = Color(0xFF45401F),
    mint = Color(0xFF1C4336), pink = Color(0xFF2D3156), peach = Color(0xFF493026),
    verified = Color(0xFF4FB5F7), raised = Color(0xFF1D2F37), dark = true
)

/** The warm end of the day: terracotta fills, a peach-gold glow, rose and apricot tiles. */
internal val SunsetLightPalette = KimiPalette(
    cream = Color(0xFFF3E8E1), surface = Color(0xFFFBF5F1), ink = Color(0xFF3A1F1A), onInk = Color.White,
    quiet = Color(0xFF64443C), purple = Color(0xFFBF4E2E), accent = Color(0xFFAE4329),
    onAccent = Color.White, highlight = Color(0xFFFFE0A8), overlay = Color(0xFFFBF5F1).copy(alpha = .50f),
    petal = Color.White.copy(alpha = .85f), danger = Color(0xFF9C2F2F),
    lavender = Color(0xFFE9C3CF), blue = Color(0xFFF2C9A8), yellow = Color(0xFFF3D88B),
    mint = Color(0xFFD8D2A2), pink = Color(0xFFF0B6A3), peach = Color(0xFFDCC0E7),
    verified = Color(0xFF1D9BF0), raised = Color(0xFFEAD8CE), dark = false
)

private val SunsetDarkPalette = KimiPalette(
    cream = Color(0xFF19110F), surface = Color(0xFF261A17), ink = Color(0xFFFBEDE7), onInk = Color(0xFF1F1412),
    quiet = Color(0xFFD0B5AB), purple = Color(0xFFB84B2C), accent = Color(0xFFFFAF8E),
    onAccent = Color.White, highlight = Color(0xFFFFE0A8), overlay = Color.White.copy(alpha = .12f),
    petal = Color(0xFFF5B79E), danger = Color(0xFFFF9E8F),
    lavender = Color(0xFF4A2733), blue = Color(0xFF4E3020), yellow = Color(0xFF4C3D16),
    mint = Color(0xFF3A3920), pink = Color(0xFF523026), peach = Color(0xFF3B2A4A),
    verified = Color(0xFF4FB5F7), raised = Color(0xFF33231F), dark = true
)

/** Kimi's colour themes. Each has a light and a dark palette, and the mode picks between them. */
enum class KimiTheme { Kimi, Monochrome, Ocean, Sunset }

internal fun paletteFor(theme: KimiTheme, dark: Boolean): KimiPalette = when (theme) {
    KimiTheme.Kimi -> if (dark) DarkPalette else LightPalette
    KimiTheme.Monochrome -> if (dark) MonochromeDarkPalette else MonochromeLightPalette
    KimiTheme.Ocean -> if (dark) OceanDarkPalette else OceanLightPalette
    KimiTheme.Sunset -> if (dark) SunsetDarkPalette else SunsetLightPalette
}

val LocalKimiPalette = staticCompositionLocalOf { LightPalette }

val Cream: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.cream
val Paper: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.surface
val Ink: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.ink
val OnInk: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.onInk
val Quiet: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.quiet
val Purple: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.purple
val Accent: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.accent
val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.onAccent
val Highlight: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.highlight
val Overlay: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.overlay
val Petal: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.petal
val Danger: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.danger
val Lavender: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.lavender
val Mint: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.mint
val Peach: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.peach
val Yellow: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.yellow
val Pink: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.pink
val Blue: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.blue
/** The badge blue. Named apart from [Blue], which is a pale card tint, and from the `Verified` icon. */
val VerifiedBlue: Color @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.verified
val TileColors: List<Color> @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.tiles

// Neutral cards need an edge in light mode; hue alone is enough for the pastel cards.
internal val LightCardOutline = Color(0xFFC1B5C2)
internal val LightControlOutline = Color(0xFF88768F)
@Composable internal fun Modifier.lightCardBorder(shape: Shape): Modifier =
    if (LocalKimiPalette.current.dark) this else border(1.dp, LightCardOutline, shape)

enum class ThemeMode { System, Light, Dark }

/** A device-level choice, deliberately kept out of per-account stores and backups. */
object ThemeSetting {
    private const val PREFS = "kimi_appearance"
    var mode by mutableStateOf(ThemeMode.System)
        private set
    var theme by mutableStateOf(KimiTheme.Kimi)
        private set
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mode = runCatching { ThemeMode.valueOf(prefs.getString("mode", null)!!) }.getOrDefault(ThemeMode.System)
        theme = runCatching { KimiTheme.valueOf(prefs.getString("theme", null)!!) }.getOrDefault(KimiTheme.Kimi)
    }
    fun set(context: Context, value: ThemeMode) {
        mode = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString("mode", value.name) }
    }
    fun set(context: Context, value: KimiTheme) {
        theme = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString("theme", value.name) }
    }
}

@Composable fun kimiDarkTheme(): Boolean = when (ThemeSetting.mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/**
 * Every icon a habit can wear. A habit stores its icon as a position in this list, in backups and in
 * the synced space, so new icons only ever go on the END: inserting or reordering would silently
 * swap the icon of every habit already saved. Names, in the same order: `habit_icon_names`.
 */
val HabitSymbols = listOf(Icons.Rounded.SelfImprovement, Icons.Rounded.WaterDrop, Icons.AutoMirrored.Rounded.MenuBook,
    Icons.AutoMirrored.Rounded.DirectionsWalk, Icons.Rounded.Bedtime, Icons.Rounded.FitnessCenter, Icons.Rounded.Brush,
    Icons.Rounded.Spa, Icons.Rounded.SportsEsports,
    // Food and drink
    Icons.Rounded.Restaurant, Icons.Rounded.SoupKitchen, Icons.Rounded.LocalCafe, Icons.Rounded.EmojiFoodBeverage,
    Icons.Rounded.BakeryDining, Icons.Rounded.NoFood,
    // Health
    Icons.Rounded.Medication, Icons.Rounded.MonitorHeart, Icons.Rounded.Mood,
    // Movement
    Icons.AutoMirrored.Rounded.DirectionsRun, Icons.AutoMirrored.Rounded.DirectionsBike, Icons.Rounded.Pool,
    Icons.Rounded.Hiking, Icons.Rounded.SportsSoccer,
    // Mind and learning
    Icons.Rounded.Psychology, Icons.Rounded.School, Icons.Rounded.Code, Icons.Rounded.MusicNote,
    Icons.Rounded.Translate, Icons.Rounded.EditNote,
    // Home and life
    Icons.Rounded.CleaningServices, Icons.Rounded.LocalFlorist, Icons.Rounded.Pets, Icons.Rounded.Savings,
    Icons.Rounded.Alarm, Icons.Rounded.WbSunny,
    // People
    Icons.Rounded.Groups, Icons.Rounded.Call, Icons.Rounded.VolunteerActivism, Icons.Rounded.CameraAlt,
    // Letting go
    Icons.Rounded.SmokeFree, Icons.Rounded.NoDrinks, Icons.Rounded.PhonelinkErase)
private val RoundedFont = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_extra_bold, FontWeight.Bold),
    Font(R.font.nunito_extra_bold, FontWeight.ExtraBold)
)

// Short and eased, so little time is spent at the greyish midpoint where both palettes meet.
private const val ThemeBlendMillis = 380

/**
 * Light and dark blend into each other instead of snapping: every token glides to its new colour.
 * [KimiPalette.dark] flips at once, since it picks between layouts rather than colours.
 */
@Composable private fun blendedPalette(target: KimiPalette): KimiPalette {
    val spec = if (reducedMotion()) snap() else tween<Color>(ThemeBlendMillis, easing = FastOutSlowInEasing)
    @Composable fun blend(color: Color, label: String) = animateColorAsState(color, spec, label = label).value
    return KimiPalette(
        cream = blend(target.cream, "cream"), surface = blend(target.surface, "surface"), ink = blend(target.ink, "ink"),
        onInk = blend(target.onInk, "onInk"), quiet = blend(target.quiet, "quiet"), purple = blend(target.purple, "purple"),
        accent = blend(target.accent, "accent"), onAccent = blend(target.onAccent, "onAccent"),
        highlight = blend(target.highlight, "highlight"), overlay = blend(target.overlay, "overlay"),
        petal = blend(target.petal, "petal"), danger = blend(target.danger, "danger"),
        lavender = blend(target.lavender, "lavender"), blue = blend(target.blue, "blue"), yellow = blend(target.yellow, "yellow"),
        mint = blend(target.mint, "mint"), pink = blend(target.pink, "pink"), peach = blend(target.peach, "peach"),
        verified = blend(target.verified, "verified"), raised = blend(target.raised, "raised"),
        dark = target.dark, mono = target.mono
    )
}

@Composable fun FormaTheme(content: @Composable () -> Unit) {
    val palette = blendedPalette(paletteFor(ThemeSetting.theme, kimiDarkTheme()))
    val defaults = Typography()
    val base = defaults.copy(
        bodyLarge = defaults.bodyLarge.copy(fontFamily = RoundedFont),
        bodySmall = defaults.bodySmall.copy(fontFamily = RoundedFont),
        labelMedium = defaults.labelMedium.copy(fontFamily = RoundedFont),
        labelSmall = defaults.labelSmall.copy(fontFamily = RoundedFont)
    )
    val colors = if (palette.dark) darkColorScheme(
        primary = palette.accent, onPrimary = palette.onInk, secondary = palette.accent,
        background = palette.cream, surface = palette.cream, onBackground = palette.ink, onSurface = palette.ink,
        onSurfaceVariant = palette.quiet,
        surfaceContainer = palette.surface, surfaceContainerHigh = palette.raised, outline = palette.quiet,
        inverseSurface = palette.ink, inverseOnSurface = palette.cream,
        error = palette.danger
    ) else lightColorScheme(
        primary = palette.purple, onPrimary = palette.onAccent, secondary = palette.purple,
        background = palette.cream, surface = palette.cream, onBackground = palette.ink, onSurface = palette.ink,
        onSurfaceVariant = palette.quiet,
        surfaceContainer = palette.surface, surfaceContainerHigh = palette.raised,
        outline = palette.quiet, outlineVariant = LightControlOutline,
        inverseSurface = palette.ink, inverseOnSurface = palette.surface,
        error = palette.danger
    )
    // Material only establishes a content colour inside a Surface. Kimi draws its first-run
    // screen and its dialogs outside the Scaffold's Surface, so they inherited Material's
    // default black and vanished against the dark palette. Anchoring it to the theme fixes
    // every such surface at once, including any added later.
    CompositionLocalProvider(LocalKimiPalette provides palette, LocalContentColor provides palette.ink) {
        MaterialTheme(
            colorScheme = colors,
            typography = base.copy(
                headlineLarge = TextStyle(fontFamily = RoundedFont, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.ExtraBold),
                headlineMedium = TextStyle(fontFamily = RoundedFont, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
                titleLarge = TextStyle(fontFamily = RoundedFont, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
                titleMedium = TextStyle(fontFamily = RoundedFont, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold),
                bodyMedium = TextStyle(fontFamily = RoundedFont, fontSize = 13.sp, lineHeight = 19.sp),
                labelLarge = TextStyle(fontFamily = RoundedFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            ), content = content)
    }
}

/** Mouth curve per journal mood, Low to Amazing: below zero frowns, above one grins. */
private val MoodSmiles = listOf(-.8f, -.15f, .6f, .9f, 1.25f)

private class FlowerIdle(val sway: State<Float>, val breath: State<Float>, val eyes: State<Float>)

@Composable private fun rememberFlowerIdle(): FlowerIdle {
    val idle = rememberInfiniteTransition(label = "flower idle")
    val sway = idle.animateFloat(-5f, 5f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val breath = idle.animateFloat(.97f, 1.03f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val eyes = idle.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 4600
        1f at 0; 1f at 4300; .12f at 4380; 1f at 4480
    }), label = "blink")
    return remember(sway, breath, eyes) { FlowerIdle(sway, breath, eyes) }
}

/**
 * A resolution-independent flower mascot, drawn with native Compose Canvas.
 *
 * [mood] (the journal's 0-4 scale) shapes the mouth and glides between moods; without it, [happy]
 * picks a smile or a straight line. [progress] (0-1) fills the six petals in turn, so the mascot can
 * double as the day's progress. [bloom] is how many petals are open (0-6), for an opening animation.
 * [alive] adds a gentle sway, breath and blink while the flower is on screen.
 */
@Composable fun Flower(
    modifier: Modifier = Modifier,
    happy: Boolean = true,
    petal: Color = Highlight,
    mood: Int? = null,
    progress: Float? = null,
    bloom: Float = 6f,
    alive: Boolean = false
) {
    // The mascot is a fixed illustration, not themed UI: a dark face on the orange centre reads
    // correctly on light and dark alike, whereas the theme ink would invert it after dark.
    val face = Color(0xFF33283E)
    // Monochrome drains the orange centre and rosy cheeks too, leaving a grey flower with the same face.
    val mono = LocalKimiPalette.current.mono
    val core = if (mono) Color(0xFFB9B9BD) else Color(0xFFFFAD73)
    val cheek = if (mono) Color(0xFFA3A3A8) else Color(0xFFF88972)
    val idle = if (alive && !reducedMotion()) rememberFlowerIdle() else null
    val smileTarget = mood?.let { MoodSmiles[it.coerceIn(0, 4)] } ?: if (happy) 1f else 0f
    val smile = animateFloatAsState(smileTarget, spring(dampingRatio = .55f, stiffness = Spring.StiffnessLow), label = "smile")
    val filled = animateFloatAsState((progress ?: 1f).coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "petals")
    val mouth = remember { Path() }
    Canvas(modifier) {
        val unit = size.minDimension
        val center = Offset(size.width / 2, size.height / 2)
        val sway = idle?.sway?.value ?: 0f
        scale(idle?.breath?.value ?: 1f, center) {
            for (i in 0..5) {
                // Petals open and fill clockwise from the upper right.
                val order = (i + 1) % 6
                val open = easeOutBack((bloom - order).coerceIn(0f, 1f))
                if (open <= 0f) continue
                val color = if (progress == null) petal
                    else lerp(petal.copy(alpha = petal.alpha * .32f), petal, (filled.value * 6f - order).coerceIn(0f, 1f))
                val angle = ((i * 60f + sway) * PI / 180).toFloat()
                val reach = unit * .245f * open
                drawCircle(color, unit * .205f * open, center + Offset(cos(angle) * reach, sin(angle) * reach))
            }
            drawCircle(core, unit * .25f, center)
            val stroke = unit * .027f
            val eyes = idle?.eyes?.value ?: 1f
            for (x in listOf(-.085f, .085f)) {
                drawLine(face, center + Offset(unit * x, -unit * (.03f + .025f * eyes)), center + Offset(unit * x, -unit * (.03f - .025f * eyes)), stroke, StrokeCap.Round)
            }
            drawCircle(cheek, unit * .032f, center + Offset(-unit * .15f, unit * .045f))
            drawCircle(cheek, unit * .032f, center + Offset(unit * .15f, unit * .045f))
            val curve = smile.value
            val bend = abs(curve).coerceAtMost(1f)
            val half = unit * (.065f + .02f * bend)
            // A frown sits a little lower, so the face does not look surprised.
            val corners = center.y + unit * (.09f - .005f * bend + (-curve).coerceAtLeast(0f) * .03f)
            mouth.reset()
            mouth.moveTo(center.x - half, corners)
            mouth.quadraticTo(center.x, corners + unit * .104f * curve, center.x + half, corners)
            drawPath(mouth, face, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable fun BubbleIcon(icon: ImageVector, background: Color, modifier: Modifier = Modifier, size: Int = 48, iconModifier: Modifier = Modifier) {
    val shape = RoundedCornerShape((size * .32).dp)
    Box(modifier.size(size.dp).clip(shape).background(background)
        .then(if (background == Paper) Modifier.lightCardBorder(shape) else Modifier), contentAlignment = Alignment.Center) {
        Icon(icon, null, iconModifier.size((size * .51).dp), tint = Ink)
    }
}
@Composable fun Pill(text: String, color: Color = Paper, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    val press = rememberSquish(.93f)
    Row(Modifier.squish(press).clip(CircleShape).background(color)
        .then(if (color == Paper) Modifier.lightCardBorder(CircleShape) else Modifier)
        .then(if (onClick != null) Modifier.clickable(press.interaction, LocalIndication.current, role = Role.Button, onClick = onClick) else Modifier)
        .padding(horizontal = 13.dp, vertical = if (onClick != null) 13.dp else 7.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, Modifier.size(15.dp), tint = Ink)
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}
@Composable fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}
@Composable fun PageTitle(title: String, description: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(7.dp))
    Text(description, color = Quiet, style = MaterialTheme.typography.bodyMedium)
}
@Composable fun PlayCard(color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    // Only tappable cards dip when pressed; a static card has nothing to say to a finger.
    val press = rememberSquish(.97f)
    Column(modifier.fillMaxWidth().then(if (onClick != null) Modifier.squish(press) else Modifier)
        .clip(RoundedCornerShape(25.dp)).background(color)
        .then(if (color == Paper) Modifier.lightCardBorder(RoundedCornerShape(25.dp)) else Modifier)
        .then(if (onClick != null) Modifier.clickable(press.interaction, LocalIndication.current, role = Role.Button, onClick = onClick) else Modifier)
        .padding(20.dp), content = content)
}
@Composable fun EmptySpace(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Flower(Modifier.size(100.dp), alive = true)
        Spacer(Modifier.height(10.dp)); Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp)); Text(text, color = Quiet, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable fun MainButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    // The button dips and its arrow leans forward while pressed, as if it were about to go.
    val press = rememberSquish(.95f)
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 54.dp).squish(press), shape = RoundedCornerShape(18.dp), enabled = enabled,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp), interactionSource = press.interaction) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.width(10.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(19.dp).graphicsLayer {
            translationX = (1f - press.scale.value) * 90.dp.toPx()
        })
    }
}
