package com.example.photocleanup.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================
// Dark Theme Color Palette
// ============================================

// Background & Surface (Smooth Dark Grays with subtle cool undertone)
val DarkBackground = Color(0xFF0D0D12)      // Near-black with cool tint
val DarkSurface = Color(0xFF1A1A23)         // Elevated surface
val DarkSurfaceVariant = Color(0xFF252532)  // Cards, containers
val DarkSurfaceHigh = Color(0xFF2F2F3D)     // Highlighted surfaces

// Primary Accent (Soft Coral - adjusted for dark backgrounds)
val AccentPrimary = Color(0xFFFF9B7A)       // Soft peach coral
val AccentPrimaryMuted = Color(0xFFCC7B61)  // Muted coral
val AccentPrimaryDim = Color(0xFF8B5A47)    // Dim for containers

// Action Colors (Adjusted for Dark)
val ActionKeep = Color(0xFF6EE7A0)          // Soft mint green
val ActionDelete = Color(0xFFFF8A8A)        // Soft coral red

// Text Colors
val TextPrimary = Color(0xFFF5F5F7)         // Near-white, slightly warm
val TextSecondary = Color(0xFF9999A5)       // Muted gray
val TextMuted = Color(0xFF666673)           // Very muted

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
