package com.sitesnap.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitesnap.app.R

// Tokens mirror the "SiteSnap v2" variable collection in the Figma file (page "v2 · Modern").
object SiteSnapColors {
    val Bg = Color(0xFFF5F5F3)
    val Surface = Color(0xFFFFFFFF)
    val Fill = Color(0xFFEDEDEA)
    val FillStrong = Color(0xFFE2E2DE)
    val Ink = Color(0xFF111113)
    val InkMuted = Color(0xFF6E6E73)
    val InkSubtle = Color(0xFFA1A1A6)
    val Hairline = Color(0xFFE8E8E4)
    val OnInk = Color(0xFFFFFFFF)
    val Accent = Color(0xFFFF5B1F)
    val AccentSoft = Color(0xFFFFE9E0)
    val Green = Color(0xFF12A150)
    val GreenSoft = Color(0xFFE3F5EA)
    val Amber = Color(0xFFC46A00)
    val AmberSoft = Color(0xFFFCF0DC)
    val Blue = Color(0xFF2E6BFF)
    val BlueSoft = Color(0xFFE6EDFF)
    val CameraBg = Color(0xFF0A0A0B)
}

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val PlusJakartaSans = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

private fun style(weight: Int, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

/** Named styles from the Figma text styles (v2/Display … v2/Overline). */
object SiteSnapType {
    val Display = style(800, 34, 40, -1.0)
    val Headline = style(700, 26, 32, -0.6)
    val Title = style(700, 18, 24, -0.3)
    val Subtitle = style(600, 15, 20, -0.1)
    val Body = style(500, 15, 22)
    val BodySmall = style(500, 13, 18)
    val Label = style(600, 14, 20)
    val Caption = style(600, 12, 16, 0.1)
    val Overline = style(700, 11, 14, 0.8)
}

private val Colors = lightColorScheme(
    primary = SiteSnapColors.Ink,
    onPrimary = SiteSnapColors.OnInk,
    primaryContainer = SiteSnapColors.Fill,
    onPrimaryContainer = SiteSnapColors.Ink,
    secondary = SiteSnapColors.Accent,
    onSecondary = SiteSnapColors.OnInk,
    secondaryContainer = SiteSnapColors.Fill,
    onSecondaryContainer = SiteSnapColors.Ink,
    background = SiteSnapColors.Bg,
    onBackground = SiteSnapColors.Ink,
    surface = SiteSnapColors.Surface,
    onSurface = SiteSnapColors.Ink,
    surfaceVariant = SiteSnapColors.Fill,
    onSurfaceVariant = SiteSnapColors.InkMuted,
    surfaceContainerLowest = SiteSnapColors.Surface,
    surfaceContainerLow = SiteSnapColors.Surface,
    surfaceContainer = SiteSnapColors.Surface,
    surfaceContainerHigh = SiteSnapColors.Surface,
    surfaceContainerHighest = SiteSnapColors.Fill,
    outline = SiteSnapColors.InkSubtle,
    outlineVariant = SiteSnapColors.Hairline,
    scrim = SiteSnapColors.Ink,
)

private val Type = Typography(
    displaySmall = SiteSnapType.Display,
    headlineMedium = SiteSnapType.Headline,
    headlineSmall = SiteSnapType.Headline,
    titleLarge = SiteSnapType.Title,
    titleMedium = SiteSnapType.Subtitle,
    titleSmall = SiteSnapType.Label,
    bodyLarge = SiteSnapType.Body,
    bodyMedium = SiteSnapType.BodySmall,
    bodySmall = SiteSnapType.Caption,
    labelLarge = SiteSnapType.Label,
    labelMedium = SiteSnapType.Caption,
    labelSmall = SiteSnapType.Overline,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SiteSnapTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, shapes = AppShapes, content = content)
}
