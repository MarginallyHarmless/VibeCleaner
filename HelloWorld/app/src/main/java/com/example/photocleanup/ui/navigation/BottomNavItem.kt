package com.example.photocleanup.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DocumentScanner
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

    object PhotoScanner : BottomNavItem(
        route = "photo_scanner",
        title = "Scanner",
        icon = Icons.Filled.DocumentScanner
    )

    object Stats : BottomNavItem(
        route = "stats",
        title = "Stats",
        icon = Icons.Filled.BarChart
    )

    object Settings : BottomNavItem(
        route = "settings_tab",
        title = "Settings",
        icon = Icons.Filled.Settings
    )

    companion object {
        val items: List<BottomNavItem>
            get() = listOf(Cleanup, PhotoScanner, Stats, Settings)
    }
}
