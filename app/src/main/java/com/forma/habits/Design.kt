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
    val dark: Boolean
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
    verified = Color(0xFF1D9BF0), dark = false
)

/** Same personality after dark: the tiles keep their hue, they just carry light text instead. */
private val DarkPalette = KimiPalette(
    cream = Color(0xFF14101A), surface = Color(0xFF211A2B), ink = Color(0xFFF5EFFA), onInk = Color(0xFF1A1422),
    quiet = Color(0xFFC0B3CC), purple = Color(0xFF744FE0), accent = Color(0xFFC7B1FF),
    onAccent = Color.White, highlight = Color(0xFFFFE696), overlay = Color.White.copy(alpha = .12f),
    petal = Color(0xFFE9B8D4), danger = Color(0xFFFF9E8F),
    lavender = Color(0xFF3A2F57), blue = Color(0xFF1E3A4C), yellow = Color(0xFF4A3A15),
    mint = Color(0xFF1F4434), pink = Color(0xFF46243A), peach = Color(0xFF4E3020),
    verified = Color(0xFF4FB5F7), dark = true
)

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
    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mode", null)
        mode = runCatching { ThemeMode.valueOf(saved!!) }.getOrDefault(ThemeMode.System)
    }
    fun set(context: Context, value: ThemeMode) {
        mode = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString("mode", value.name) }
    }
}

@Composable fun kimiDarkTheme(): Boolean = when (ThemeSetting.mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

val HabitSymbols = listOf(Icons.Rounded.SelfImprovement, Icons.Rounded.WaterDrop, Icons.AutoMirrored.Rounded.MenuBook,
    Icons.AutoMirrored.Rounded.DirectionsWalk, Icons.Rounded.Bedtime, Icons.Rounded.FitnessCenter, Icons.Rounded.Brush,
    Icons.Rounded.Spa, Icons.Rounded.SportsEsports)
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
        verified = blend(target.verified, "verified"), dark = target.dark
    )
}

@Composable fun FormaTheme(content: @Composable () -> Unit) {
    val palette = blendedPalette(if (kimiDarkTheme()) DarkPalette else LightPalette)
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
        surfaceContainer = palette.surface, surfaceContainerHigh = Color(0xFF2C2338), outline = palette.quiet,
        inverseSurface = palette.ink, inverseOnSurface = palette.cream,
        error = palette.danger
    ) else lightColorScheme(
        primary = palette.purple, onPrimary = palette.onAccent, secondary = palette.purple,
        background = palette.cream, surface = palette.cream, onBackground = palette.ink, onSurface = palette.ink,
        onSurfaceVariant = palette.quiet,
        surfaceContainer = palette.surface, surfaceContainerHigh = Color(0xFFE2D8EB),
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
            drawCircle(Color(0xFFFFAD73), unit * .25f, center)
            val stroke = unit * .027f
            val eyes = idle?.eyes?.value ?: 1f
            for (x in listOf(-.085f, .085f)) {
                drawLine(face, center + Offset(unit * x, -unit * (.03f + .025f * eyes)), center + Offset(unit * x, -unit * (.03f - .025f * eyes)), stroke, StrokeCap.Round)
            }
            drawCircle(Color(0xFFF88972), unit * .032f, center + Offset(-unit * .15f, unit * .045f))
            drawCircle(Color(0xFFF88972), unit * .032f, center + Offset(unit * .15f, unit * .045f))
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
