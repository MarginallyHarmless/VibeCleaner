package com.example.photocleanup.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val VibeLightColorScheme = lightColorScheme(
    primary = VibeCoral,
    onPrimary = VibeSurface,
    primaryContainer = VibeCoralLight,
    onPrimaryContainer = VibeCoralDark,
    secondary = VibePeach,
    onSecondary = VibeTextPrimary,
    secondaryContainer = VibePeachLight,
    onSecondaryContainer = VibePeachDark,
    tertiary = VibePeachDark,
    onTertiary = VibeSurface,
    tertiaryContainer = VibePeachLight,
    onTertiaryContainer = VibePeachDark,
    error = VibeDelete,
    onError = VibeSurface,
    errorContainer = VibeDelete.copy(alpha = 0.1f),
    onErrorContainer = VibeDelete,
    background = VibeBackground,
    onBackground = VibeTextPrimary,
    surface = VibeSurface,
    onSurface = VibeTextPrimary,
    surfaceVariant = VibeCoralLight.copy(alpha = 0.3f),
    onSurfaceVariant = VibeTextSecondary,
    outline = VibeTextSecondary.copy(alpha = 0.5f),
    outlineVariant = VibeCoralLight
)

private val VibeDarkColorScheme = darkColorScheme(
    primary = VibeCoralLight,
    onPrimary = VibeCoralDark,
    primaryContainer = VibeCoralDark,
    onPrimaryContainer = VibeCoralLight,
    secondary = VibePeachLight,
    onSecondary = VibePeachDark,
    secondaryContainer = VibePeachDark,
    onSecondaryContainer = VibePeachLight,
    tertiary = VibePeachLight,
    onTertiary = VibePeachDark,
    tertiaryContainer = VibePeachDark,
    onTertiaryContainer = VibePeachLight,
    error = VibeDelete,
    onError = VibeSurfaceDark,
    errorContainer = VibeDelete.copy(alpha = 0.2f),
    onErrorContainer = VibeDelete,
    background = VibeBackgroundDark,
    onBackground = VibeTextPrimaryDark,
    surface = VibeSurfaceDark,
    onSurface = VibeTextPrimaryDark,
    surfaceVariant = VibeCoralDark.copy(alpha = 0.2f),
    onSurfaceVariant = VibeTextSecondaryDark,
    outline = VibeTextSecondaryDark.copy(alpha = 0.5f),
    outlineVariant = VibeCoralDark.copy(alpha = 0.3f)
)

@Composable
fun VibeCleanerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VibeDarkColorScheme else VibeLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
