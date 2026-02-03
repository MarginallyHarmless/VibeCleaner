package com.example.photocleanup

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.photocleanup.data.MenuFilter
import com.example.photocleanup.data.MenuMode
import com.example.photocleanup.ui.screens.MainScreen
import com.example.photocleanup.ui.screens.MenuScreen
import com.example.photocleanup.ui.screens.SettingsScreen
import com.example.photocleanup.ui.screens.ToDeleteScreen
import com.example.photocleanup.ui.theme.VibeCleanerTheme
import com.example.photocleanup.viewmodel.MenuViewModel
import com.example.photocleanup.viewmodel.PhotoViewModel
import com.example.photocleanup.viewmodel.ToDeleteViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VibeCleanerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val photoViewModel: PhotoViewModel = viewModel()
                    val menuViewModel: MenuViewModel = viewModel()

                    NavHost(
                        navController = navController,
                        startDestination = "menu"
                    ) {
                        // Menu screen - new entry point
                        composable("menu") {
                            MenuScreen(
                                viewModel = menuViewModel,
                                onNavigateToSwipe = { filter ->
                                    // Encode filter parameters in URL
                                    val modeParam = filter.mode.name
                                    val yearParam = filter.year ?: -1
                                    val monthParam = filter.month ?: -1
                                    val albumIdParam = filter.albumBucketId ?: -1L
                                    val recentParam = filter.isRecentPhotos
                                    val titleParam = Uri.encode(filter.displayTitle)

                                    navController.navigate(
                                        "main?mode=$modeParam&year=$yearParam&month=$monthParam&albumId=$albumIdParam&recent=$recentParam&title=$titleParam"
                                    )
                                }
                            )
                        }

                        // Main swipe screen with optional filter parameters
                        composable(
                            route = "main?mode={mode}&year={year}&month={month}&albumId={albumId}&recent={recent}&title={title}",
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
                                navArgument("recent") {
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
                            val recent = backStackEntry.arguments?.getBoolean("recent") ?: false
                            val title = backStackEntry.arguments?.getString("title") ?: ""

                            // Build MenuFilter from URL parameters
                            val menuFilter = if (modeStr.isNotEmpty()) {
                                MenuFilter(
                                    mode = try { MenuMode.valueOf(modeStr) } catch (e: Exception) { MenuMode.BY_DATE },
                                    year = if (year >= 0) year else null,
                                    month = if (month >= 0) month else null,
                                    albumBucketId = if (albumId >= 0) albumId else null,
                                    isRecentPhotos = recent,
                                    displayTitle = title
                                )
                            } else null

                            MainScreen(
                                viewModel = photoViewModel,
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
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

                        composable("settings") {
                            SettingsScreen(
                                viewModel = photoViewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
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
