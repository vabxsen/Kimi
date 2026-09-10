package com.forma.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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

val Cream = Color(0xFFFFF9F2)
val Ink = Color(0xFF33283E)
val Quiet = Color(0xFF776B7E)
val Purple = Color(0xFF7255D9)
val Lavender = Color(0xFFE5DCFF)
val Mint = Color(0xFFCBECDD)
val Peach = Color(0xFFFFD8C3)
val Yellow = Color(0xFFFFE696)
val Pink = Color(0xFFF7D6E8)
val Blue = Color(0xFFCEE9FA)
val TileColors = listOf(Lavender, Blue, Yellow, Mint, Pink, Peach)
val HabitSymbols = listOf(Icons.Rounded.SelfImprovement, Icons.Rounded.WaterDrop, Icons.AutoMirrored.Rounded.MenuBook,
    Icons.AutoMirrored.Rounded.DirectionsWalk, Icons.Rounded.Bedtime, Icons.Rounded.FitnessCenter, Icons.Rounded.Brush, Icons.Rounded.Spa)
private val RoundedFont = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_extra_bold, FontWeight.Bold),
    Font(R.font.nunito_extra_bold, FontWeight.ExtraBold)
)

@Composable fun FormaTheme(content: @Composable () -> Unit) {
    val defaults = Typography()
    val base = defaults.copy(
        bodyLarge = defaults.bodyLarge.copy(fontFamily = RoundedFont),
        bodySmall = defaults.bodySmall.copy(fontFamily = RoundedFont),
        labelMedium = defaults.labelMedium.copy(fontFamily = RoundedFont),
        labelSmall = defaults.labelSmall.copy(fontFamily = RoundedFont)
    )
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Purple, onPrimary = Color.White, secondary = Purple,
            background = Cream, surface = Cream, onBackground = Ink, onSurface = Ink,
            surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF1EAF8), outline = Quiet),
        typography = base.copy(
            headlineLarge = TextStyle(fontFamily = RoundedFont, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.ExtraBold, color = Ink),
            headlineMedium = TextStyle(fontFamily = RoundedFont, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Ink),
            titleLarge = TextStyle(fontFamily = RoundedFont, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, color = Ink),
            titleMedium = TextStyle(fontFamily = RoundedFont, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = Ink),
            bodyMedium = TextStyle(fontFamily = RoundedFont, fontSize = 13.sp, lineHeight = 19.sp, color = Ink),
            labelLarge = TextStyle(fontFamily = RoundedFont, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        ), content = content)
}

/** A resolution-independent flower mascot, drawn with native Compose Canvas. */
@Composable fun Flower(modifier: Modifier = Modifier, happy: Boolean = true, petal: Color = Yellow) {
    Canvas(modifier.semantics { contentDescription = "Happy flower mascot" }) {
        val unit = size.minDimension
        val center = Offset(size.width / 2, size.height / 2)
        for (i in 0..5) {
            val angle = (i * Math.PI / 3).toFloat()
            drawCircle(petal, unit * .205f, center + Offset(cos(angle) * unit * .245f, sin(angle) * unit * .245f))
        }
        drawCircle(Color(0xFFFFAD73), unit * .25f, center)
        val stroke = unit * .027f
        for (x in listOf(-.085f, .085f)) {
            drawLine(Ink, center + Offset(unit * x, -unit * .055f), center + Offset(unit * x, -unit * .005f), stroke, StrokeCap.Round)
        }
        drawCircle(Color(0xFFF88972), unit * .032f, center + Offset(-unit * .15f, unit * .045f))
        drawCircle(Color(0xFFF88972), unit * .032f, center + Offset(unit * .15f, unit * .045f))
        if (happy) drawArc(Ink, 12f, 156f, false, center + Offset(-unit * .085f, unit * .005f), Size(unit * .17f, unit * .13f), style = Stroke(stroke, cap = StrokeCap.Round))
        else drawLine(Ink, center + Offset(-unit * .065f, unit * .09f), center + Offset(unit * .065f, unit * .09f), stroke, StrokeCap.Round)
    }
}

@Composable fun BubbleIcon(icon: ImageVector, background: Color, modifier: Modifier = Modifier, size: Int = 48) {
    Box(modifier.size(size.dp).clip(RoundedCornerShape((size * .32).dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * .51).dp), tint = Ink)
    }
}
@Composable fun Pill(text: String, color: Color = Color.White, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
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
