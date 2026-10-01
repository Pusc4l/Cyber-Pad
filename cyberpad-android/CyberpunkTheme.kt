package com.cyberpad.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ObsidianDark = Color(0xFF0D0E15)
val CarbonCard = Color(0xFF141622)
val NeonCyan = Color(0xFF00F0FF)
val ElectricPink = Color(0xFFFF0055)
val CyberYellow = Color(0xFFFFE600)
val GridCyan = Color(0x0D00F0FF) // #00F0FF @ 5% alpha
val TextPrimary = Color(0xFFE6FDFF)
val TextMuted = Color(0xFF6C7A8C)

private val CyberColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = ObsidianDark,
    secondary = ElectricPink,
    onSecondary = ObsidianDark,
    tertiary = CyberYellow,
    onTertiary = ObsidianDark,
    background = ObsidianDark,
    onBackground = TextPrimary,
    surface = CarbonCard,
    onSurface = TextPrimary,
    error = ElectricPink,
    onError = ObsidianDark
)

/** Tipografi monospace teknis (font sistem; ganti ke JetBrains Mono/Orbitron dengan menambah res/font). */
val CyberFont: FontFamily = FontFamily.Monospace

private val CyberTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = CyberFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 2.sp
    ),
    titleMedium = TextStyle(
        fontFamily = CyberFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = CyberFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = 0.5.sp
    ),
    labelLarge = TextStyle(
        fontFamily = CyberFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = CyberFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp
    )
)

@Composable
fun CyberpunkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CyberColorScheme,
        typography = CyberTypography,
        content = content
    )
}
