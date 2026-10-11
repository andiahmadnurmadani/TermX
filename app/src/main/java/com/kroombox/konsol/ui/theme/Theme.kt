package com.kroombox.konsol.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFF0E1116)
    val Surface = Color(0xFF151A22)
    val SurfaceHigh = Color(0xFF1C2230)
    val SurfaceHighest = Color(0xFF242B3A)
    val Outline = Color(0xFF2C3446)
    val Text = Color(0xFFE6EDF3)
    val TextDim = Color(0xFF9AA4B2)
    val Accent = Color(0xFF3DDC97)
    val AccentDim = Color(0xFF1F6F4E)
    val Blue = Color(0xFF5AA9FF)
    val Warn = Color(0xFFF7C948)
    val Danger = Color(0xFFFF6B6B)
}

private val colors = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Color(0xFF04140D),
    primaryContainer = Palette.AccentDim,
    onPrimaryContainer = Color(0xFFCFFBE6),
    secondary = Palette.Blue,
    onSecondary = Color(0xFF041427),
    secondaryContainer = Color(0xFF1B3556),
    onSecondaryContainer = Color(0xFFD6E8FF),
    tertiary = Color(0xFFC792EA),
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Bg,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLowest = Palette.Bg,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHighest,
    outline = Palette.Outline,
    outlineVariant = Color(0xFF222938),
    error = Palette.Danger,
    onError = Color(0xFF2B0606),
)

val Mono = FontFamily.Monospace

private val typography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val MonoSmall = TextStyle(fontFamily = Mono, fontSize = 12.sp, color = Palette.TextDim)

@Composable
fun KonsolTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
