package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF00363B),
    primaryContainer = CyberCyanDark,
    onPrimaryContainer = CyberCyanLight,
    secondary = NeonEmerald,
    onSecondary = Color(0xFF00381B),
    secondaryContainer = NeonEmeraldDark,
    onSecondaryContainer = Color(0xFF6EE7B7),
    tertiary = ElectricPurple,
    background = SlateBackground,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = SlateOutline,
    error = CrimsonError
)

private val LightColorScheme = DarkColorScheme // Workbench defaults to sleek pro developer dark theme

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}


