package com.example.photocleanup.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VibeDarkColorScheme = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = DarkBackground,
    primaryContainer = AccentPrimaryDim,
    onPrimaryContainer = AccentPrimary,
    secondary = AccentPrimaryMuted,
    onSecondary = DarkBackground,
    secondaryContainer = AccentPrimaryDim,
    onSecondaryContainer = AccentPrimaryMuted,
    tertiary = AccentPrimaryMuted,
    onTertiary = DarkBackground,
    tertiaryContainer = AccentPrimaryDim,
    onTertiaryContainer = AccentPrimaryMuted,
    error = ActionDelete,
    onError = DarkBackground,
    errorContainer = ActionDelete.copy(alpha = 0.2f),
    onErrorContainer = ActionDelete,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = TextMuted,
    outlineVariant = DarkSurfaceHigh
)

@Composable
fun VibeCleanerTheme(
    content: @Composable () -> Unit
) {
    // Always use dark theme - no system detection
    MaterialTheme(
        colorScheme = VibeDarkColorScheme,
        typography = Typography,
        content = content
    )
}
