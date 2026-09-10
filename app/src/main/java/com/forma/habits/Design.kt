package com.forma.habits

import android.content.Context
import androidx.core.content.edit
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
    val dark: Boolean
) {
    val tiles: List<Color> get() = listOf(lavender, blue, yellow, mint, pink, peach)
}

private val LightPalette = KimiPalette(
    cream = Color(0xFFFFF8F0), surface = Color.White, ink = Color(0xFF30243A), onInk = Color.White,
    quiet = Color(0xFF615567), purple = Color(0xFF6847D6), accent = Color(0xFF6847D6),
    onAccent = Color.White, highlight = Color(0xFFFFE696), overlay = Color.White.copy(alpha = .65f),
    petal = Color.White.copy(alpha = .85f), danger = Color(0xFF9C463A),
    lavender = Color(0xFFDCCFFF), blue = Color(0xFFBFE1F6), yellow = Color(0xFFFFDC70),
    mint = Color(0xFFB8E3CD), pink = Color(0xFFF1C4D9), peach = Color(0xFFFFC9AB), dark = false
)

/** Same personality after dark: the tiles keep their hue, they just carry light text instead. */
private val DarkPalette = KimiPalette(
    cream = Color(0xFF14101A), surface = Color(0xFF211A2B), ink = Color(0xFFF5EFFA), onInk = Color(0xFF1A1422),
    quiet = Color(0xFFC0B3CC), purple = Color(0xFF744FE0), accent = Color(0xFFC7B1FF),
    onAccent = Color.White, highlight = Color(0xFFFFE696), overlay = Color.White.copy(alpha = .12f),
    petal = Color(0xFFE9B8D4), danger = Color(0xFFFF9E8F),
    lavender = Color(0xFF3A2F57), blue = Color(0xFF1E3A4C), yellow = Color(0xFF4A3A15),
    mint = Color(0xFF1F4434), pink = Color(0xFF46243A), peach = Color(0xFF4E3020), dark = true
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
val TileColors: List<Color> @Composable @ReadOnlyComposable get() = LocalKimiPalette.current.tiles

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
    Icons.AutoMirrored.Rounded.DirectionsWalk, Icons.Rounded.Bedtime, Icons.Rounded.FitnessCenter, Icons.Rounded.Brush, Icons.Rounded.Spa)
private val RoundedFont = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_extra_bold, FontWeight.Bold),
    Font(R.font.nunito_extra_bold, FontWeight.ExtraBold)
)

@Composable fun FormaTheme(content: @Composable () -> Unit) {
    val palette = if (kimiDarkTheme()) DarkPalette else LightPalette
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
        surfaceContainer = palette.surface, surfaceContainerHigh = Color(0xFFF1EAF8), outline = palette.quiet,
        inverseSurface = palette.ink, inverseOnSurface = palette.surface,
        error = palette.danger
    )
    CompositionLocalProvider(LocalKimiPalette provides palette) {
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

/** A resolution-independent flower mascot, drawn with native Compose Canvas. */
@Composable fun Flower(modifier: Modifier = Modifier, happy: Boolean = true, petal: Color = Highlight) {
    // The mascot is a fixed illustration, not themed UI: a dark face on the orange centre reads
    // correctly on light and dark alike, whereas the theme ink would invert it after dark.
    val face = Color(0xFF33283E)
    val label = stringResource(R.string.cd_mascot)
    Canvas(modifier.semantics { contentDescription = label }) {
        val unit = size.minDimension
        val center = Offset(size.width / 2, size.height / 2)
        for (i in 0..5) {
            val angle = (i * Math.PI / 3).toFloat()
            drawCircle(petal, unit * .205f, center + Offset(cos(angle) * unit * .245f, sin(angle) * unit * .245f))
        }
        drawCircle(Color(0xFFFFAD73), unit * .25f, center)
        val stroke = unit * .027f
        for (x in listOf(-.085f, .085f)) {
            drawLine(face, center + Offset(unit * x, -unit * .055f), center + Offset(unit * x, -unit * .005f), stroke, StrokeCap.Round)
        }
        drawCircle(Color(0xFFF88972), unit * .032f, center + Offset(-unit * .15f, unit * .045f))
        drawCircle(Color(0xFFF88972), unit * .032f, center + Offset(unit * .15f, unit * .045f))
        if (happy) drawArc(face, 12f, 156f, false, center + Offset(-unit * .085f, unit * .005f), Size(unit * .17f, unit * .13f), style = Stroke(stroke, cap = StrokeCap.Round))
        else drawLine(face, center + Offset(-unit * .065f, unit * .09f), center + Offset(unit * .065f, unit * .09f), stroke, StrokeCap.Round)
    }
}

@Composable fun BubbleIcon(icon: ImageVector, background: Color, modifier: Modifier = Modifier, size: Int = 48) {
    Box(modifier.size(size.dp).clip(RoundedCornerShape((size * .32).dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * .51).dp), tint = Ink)
    }
}
@Composable fun Pill(text: String, color: Color = Paper, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.clip(CircleShape).background(color).then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
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
@Composable fun PlayCard(color: Color, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp)).background(color).padding(20.dp), content = content)
}
@Composable fun EmptySpace(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Flower(Modifier.size(100.dp))
        Spacer(Modifier.height(10.dp)); Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp)); Text(text, color = Quiet, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable fun MainButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(18.dp), enabled = enabled,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.width(10.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(19.dp))
    }
}
