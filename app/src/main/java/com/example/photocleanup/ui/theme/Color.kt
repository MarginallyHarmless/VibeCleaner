package com.example.photocleanup.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
// Clean My Photos - Dark Theme Color Palette
// ============================================

// Background & Surface (Dark charcoal)
val DarkBackground = Color(0xFF1A1A1A)      // Main background
val DarkSurface = Color(0xFF2D2D2D)         // Cards, elevated surfaces
val DarkSurfaceVariant = Color(0xFF3D3D3D)  // Containers, dividers
val DarkSurfaceHigh = Color(0xFF4A4A4A)     // Highlighted surfaces

// Primary Accent (Coral #FF6B6B)
val AccentPrimary = Color(0xFFFF6B6B)       // Main coral accent
val AccentPrimaryMuted = Color(0xFFCC5555)  // Muted coral for secondary uses
val AccentPrimaryDim = Color(0xFF8B3D3D)    // Dim coral for containers

// Action Colors
val ActionKeep = Color(0xFF51D88A)          // Mint green - swipe right/keep
val ActionDelete = Color(0xFFFF6B6B)        // Coral - swipe left/delete

// Text Colors
val TextPrimary = Color(0xFFFFFFFF)         // White - main text
val TextSecondary = Color(0xFFB0B0B0)       // Light gray - subtitles
val TextMuted = Color(0xFF707070)           // Muted gray - hints

// ============================================
// Legacy names mapped to new palette
// ============================================

// Primary - Warm Coral (mapped to new accent)
val VibeCoral = AccentPrimary
val VibeCoralLight = AccentPrimaryMuted
val VibeCoralDark = AccentPrimaryDim

// Action Colors (new softer versions for dark theme)
val VibeKeep = ActionKeep
val VibeDelete = ActionDelete

// Surface Colors (mapped to new dark surfaces)
val VibeSurface = DarkSurface
val VibeSurfaceDark = DarkSurface
val VibeBackground = DarkBackground
val VibeBackgroundDark = DarkBackground

// Text Colors (mapped to new dark-friendly text)
val VibeTextPrimary = TextPrimary
val VibeTextSecondary = TextSecondary
val VibeTextPrimaryDark = TextPrimary
val VibeTextSecondaryDark = TextSecondary
