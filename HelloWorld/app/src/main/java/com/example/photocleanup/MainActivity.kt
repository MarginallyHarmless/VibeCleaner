package com.example.photocleanup

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.photocleanup.data.MenuFilter
import com.example.photocleanup.data.MenuMode
import com.example.photocleanup.ui.components.BottomNavigationBar
import com.example.photocleanup.ui.navigation.BottomNavItem
import com.example.photocleanup.ui.screens.MainScreen
import com.example.photocleanup.ui.screens.MenuScreen
import com.example.photocleanup.ui.screens.SettingsTabScreen
import com.example.photocleanup.ui.screens.DuplicatesScreen
import com.example.photocleanup.ui.screens.ToDeleteScreen
import com.example.photocleanup.ui.theme.CleanMyPhotosTheme
import com.example.photocleanup.viewmodel.DuplicatesViewModel
import com.example.photocleanup.viewmodel.MenuViewModel
import com.example.photocleanup.viewmodel.PhotoViewModel
import com.example.photocleanup.viewmodel.ToDeleteViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Set status bar to match dark charcoal background (0xFF1A1A1A)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF1A1A1A.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF1A1A1A.toInt())
        )
        super.onCreate(savedInstanceState)
        setContent {
            CleanMyPhotosTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val photoViewModel: PhotoViewModel = viewModel()
                    val menuViewModel: MenuViewModel = viewModel()

                    // Get current route for bottom nav visibility
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    // Routes where bottom nav should be visible
                    val bottomNavRoutes = listOf(
                        BottomNavItem.Cleanup.route,
                        BottomNavItem.Duplicates.route,
                        BottomNavItem.Settings.route
                    )
                    val showBottomNav = currentRoute in bottomNavRoutes

                    Scaffold(
                        bottomBar = {
                            if (showBottomNav) {
                                BottomNavigationBar(
                                    currentRoute = currentRoute,
                                    onNavigate = { item ->
                                        navController.navigate(item.route) {
                                            // Pop up to the start destination to avoid building
                                            // a large stack of destinations
                                            popUpTo(BottomNavItem.Cleanup.route) {
                                                saveState = true
                                            }
                                            // Avoid multiple copies of the same destination
                                            launchSingleTop = true
                                            // Restore state when reselecting a previously selected item
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = BottomNavItem.Cleanup.route,
                            modifier = if (showBottomNav) Modifier.padding(innerPadding) else Modifier
                        ) {
                            // Cleanup tab - Menu screen
                            composable(BottomNavItem.Cleanup.route) {
                                MenuScreen(
                                    viewModel = menuViewModel,
                                    onNavigateToSwipe = { filter ->
                                        // Encode filter parameters in URL
                                        val modeParam = filter.mode.name
                                        val yearParam = filter.year ?: -1
                                        val monthParam = filter.month ?: -1
                                        val albumIdParam = filter.albumBucketId ?: -1L
                                        val allMediaParam = filter.isAllMedia
                                        val titleParam = Uri.encode(filter.displayTitle)

                                        navController.navigate(
                                            "main?mode=$modeParam&year=$yearParam&month=$monthParam&albumId=$albumIdParam&allMedia=$allMediaParam&title=$titleParam"
                                        )
                                    }
                                )
                            }

                            // Duplicates tab
                            composable(BottomNavItem.Duplicates.route) {
                                val duplicatesViewModel: DuplicatesViewModel = viewModel()
                                DuplicatesScreen(
                                    viewModel = duplicatesViewModel
                                )
                            }

                            // Settings tab
                            composable(BottomNavItem.Settings.route) {
                                SettingsTabScreen(
                                    viewModel = photoViewModel
                                )
                            }

                            // Main swipe screen with optional filter parameters
                            composable(
                                route = "main?mode={mode}&year={year}&month={month}&albumId={albumId}&allMedia={allMedia}&title={title}",
                                arguments = listOf(
                                    navArgument("mode") {
                                        type = NavType.StringType
                                        defaultValue = ""
                                    },
                                    navArgument("year") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    },
                                    navArgument("month") {
                                        type = NavType.IntType
                                        defaultValue = -1
                                    },
                                    navArgument("albumId") {
                                        type = NavType.LongType
                                        defaultValue = -1L
                                    },
                                    navArgument("allMedia") {
                                        type = NavType.BoolType
                                        defaultValue = false
                                    },
                                    navArgument("title") {
                                        type = NavType.StringType
                                        defaultValue = ""
                                    }
                                )
                            ) { backStackEntry ->
                                val modeStr = backStackEntry.arguments?.getString("mode") ?: ""
                                val year = backStackEntry.arguments?.getInt("year") ?: -1
                                val month = backStackEntry.arguments?.getInt("month") ?: -1
                                val albumId = backStackEntry.arguments?.getLong("albumId") ?: -1L
                                val allMedia = backStackEntry.arguments?.getBoolean("allMedia") ?: false
                                val title = backStackEntry.arguments?.getString("title") ?: ""

                                // Build MenuFilter from URL parameters
                                val menuFilter = if (modeStr.isNotEmpty()) {
                                    MenuFilter(
                                        mode = try { MenuMode.valueOf(modeStr) } catch (e: Exception) { MenuMode.BY_DATE },
                                        year = if (year >= 0) year else null,
                                        month = if (month >= 0) month else null,
                                        albumBucketId = if (albumId >= 0) albumId else null,
                                        isAllMedia = allMedia,
                                        displayTitle = title
                                    )
                                } else null

                                MainScreen(
                                    viewModel = photoViewModel,
                                    onNavigateToDelete = {
                                        navController.navigate("to_delete")
                                    },
                                    onNavigateBack = {
                                        // Refresh menu data when returning
                                        menuViewModel.refreshData()
                                        navController.popBackStack()
                                    },
                                    menuFilter = menuFilter
                                )
                            }

                            composable("to_delete") {
                                val toDeleteViewModel: ToDeleteViewModel = viewModel()
                                ToDeleteScreen(
                                    viewModel = toDeleteViewModel,
                                    onNavigateBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
