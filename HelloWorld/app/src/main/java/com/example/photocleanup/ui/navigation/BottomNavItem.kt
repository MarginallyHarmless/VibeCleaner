package com.example.photocleanup.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Cleanup : BottomNavItem(
        route = "cleanup",
        title = "Cleanup",
        icon = Icons.Filled.Photo
    )

    object Settings : BottomNavItem(
        route = "settings_tab",
        title = "Settings",
        icon = Icons.Filled.Settings
    )

    companion object {
        val items: List<BottomNavItem> by lazy { listOf(Cleanup, Settings) }
    }
}
