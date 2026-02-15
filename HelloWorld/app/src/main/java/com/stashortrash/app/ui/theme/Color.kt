package com.stashortrash.app.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
// Stash or Trash - Dark Theme Color Palette
// ============================================

// Background & Surface (Carbon Black base)
val CarbonBlack = Color(0xFF1E1B18)             // Main background
val DarkBackground = CarbonBlack                 // Alias for compatibility
val DarkSurfaceSubtle = Color(0xFF252220)       // Subtle elevation (bottom nav)
val DarkSurface = Color(0xFF302D2A)             // Cards, elevated surfaces
val DarkSurfaceVariant = Color(0xFF3D3A37)      // Containers, dividers
val DarkSurfaceHigh = Color(0xFF4A4744)         // Highlighted surfaces

// Primary Accent - Seagrass (#61988E)
val Seagrass = Color(0xFF61988E)                // Main seagrass accent
val SeagrassMuted = Color(0xFF4E7A72)           // Muted seagrass for secondary uses
val SeagrassDim = Color(0xFF3D5C56)             // Dim seagrass for containers
val AccentPrimary = Seagrass                    // Alias for compatibility
val AccentPrimaryMuted = SeagrassMuted          // Alias for compatibility
val AccentPrimaryDim = SeagrassDim              // Alias for compatibility

// Destructive Action - Dusty Mauve (#A64253)
val DustyMauve = Color(0xFFA64253)              // Destructive actions
val DustyMauveMuted = Color(0xFF853542)         // Muted mauve for secondary uses
val DustyMauveDim = Color(0xFF642832)           // Dim mauve for containers

// Premium - Honey Bronze (#F6AE2D)
val HoneyBronze = Color(0xFFF6AE2D)             // Premium features
val HoneyBronzeMuted = Color(0xFFC58B24)        // Muted bronze for secondary uses
val HoneyBronzeDim = Color(0xFF94681B)          // Dim bronze for containers

// Action Colors
val ActionKeep = Seagrass                       // Seagrass - swipe right/keep
val ActionDelete = DustyMauve                   // Dusty Mauve - swipe left/delete
val ActionRefresh = Seagrass                    // Seagrass - refresh/rescan actions

// Text Colors
val TextPrimary = Color(0xFFFFFFFF)             // White - main text
val TextSecondary = Color(0xFFB0B0B0)           // Light gray - subtitles
val TextMuted = Color(0xFF707070)               // Muted gray - hints

// ============================================
// Legacy names mapped to new palette
// ============================================

// Primary - Seagrass (mapped to new accent)
val VibeCoral = AccentPrimary
val VibeCoralLight = AccentPrimaryMuted
val VibeCoralDark = AccentPrimaryDim

// Action Colors (mapped to new palette)
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
