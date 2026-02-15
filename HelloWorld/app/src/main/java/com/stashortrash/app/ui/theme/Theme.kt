package com.stashortrash.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CleanMyPhotosDarkColorScheme = darkColorScheme(
    primary = Seagrass,
    onPrimary = CarbonBlack,
    primaryContainer = SeagrassDim,
    onPrimaryContainer = Seagrass,
    secondary = SeagrassMuted,
    onSecondary = CarbonBlack,
    secondaryContainer = SeagrassDim,
    onSecondaryContainer = SeagrassMuted,
    tertiary = HoneyBronze,
    onTertiary = CarbonBlack,
    tertiaryContainer = HoneyBronzeDim,
    onTertiaryContainer = HoneyBronze,
    error = DustyMauve,
    onError = TextPrimary,
    errorContainer = DustyMauveDim,
    onErrorContainer = DustyMauve,
    background = CarbonBlack,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = TextMuted,
    outlineVariant = DarkSurfaceHigh
)

@Composable
fun CleanMyPhotosTheme(
    content: @Composable () -> Unit
) {
    // Always use dark theme - Stash or Trash brand
    MaterialTheme(
        colorScheme = CleanMyPhotosDarkColorScheme,
        typography = Typography,
        content = content
    )
}
