package com.stashortrash.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.stashortrash.app.data.MenuFilter
import com.stashortrash.app.data.MenuMode
import com.stashortrash.app.ui.components.BottomNavigationBar
import com.stashortrash.app.ui.navigation.BottomNavItem
import com.stashortrash.app.ui.screens.MainScreen
import com.stashortrash.app.ui.screens.MenuScreen
import com.stashortrash.app.ui.screens.SettingsTabScreen
import com.stashortrash.app.ui.screens.PhotoScannerScreen
import com.stashortrash.app.ui.screens.StatsScreen
import com.stashortrash.app.ui.screens.ToDeleteScreen
import com.stashortrash.app.ui.theme.CleanMyPhotosTheme
import com.stashortrash.app.viewmodel.PhotoScannerViewModel
import com.stashortrash.app.viewmodel.MenuViewModel
import com.stashortrash.app.viewmodel.PhotoViewModel
import com.stashortrash.app.viewmodel.StatsViewModel
import com.stashortrash.app.viewmodel.ToDeleteViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Set status bar to match dark charcoal background (0xFF1A1A1A)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF1A1A1A.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF1A1A1A.toInt())
        )
        super.onCreate(savedInstanceState)

        // Connect to Google Play Billing to check for existing purchases
        (application as PhotoCleanupApp).billingManager.startConnection()

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
                        BottomNavItem.PhotoScanner.route,
                        BottomNavItem.Stats.route,
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
                        // Smooth transition duration
                        val transitionDuration = 300

                        NavHost(
                            navController = navController,
                            startDestination = BottomNavItem.Cleanup.route,
                            modifier = if (showBottomNav) Modifier.padding(innerPadding) else Modifier,
                            enterTransition = {
                                slideIntoContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                    animationSpec = tween(transitionDuration)
                                ) + fadeIn(animationSpec = tween(transitionDuration))
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                                    animationSpec = tween(transitionDuration)
                                ) + fadeOut(animationSpec = tween(transitionDuration))
                            },
                            popEnterTransition = {
                                slideIntoContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                                    animationSpec = tween(transitionDuration)
                                ) + fadeIn(animationSpec = tween(transitionDuration))
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                                    animationSpec = tween(transitionDuration)
                                ) + fadeOut(animationSpec = tween(transitionDuration))
                            }
                        ) {
                            // Cleanup tab - Menu screen (fade only for tab switches)
                            composable(
                                route = BottomNavItem.Cleanup.route,
                                enterTransition = { fadeIn(animationSpec = tween(transitionDuration)) },
                                exitTransition = { fadeOut(animationSpec = tween(transitionDuration)) },
                                popEnterTransition = { fadeIn(animationSpec = tween(transitionDuration)) },
                                popExitTransition = { fadeOut(animationSpec = tween(transitionDuration)) }
                            ) {
                                MenuScreen(
                                    viewModel = menuViewModel,
                                    onNavigateToSwipe = { filter ->
                                        // Encode filter parameters in URL
                                        val modeParam = filter.mode.name
                                        val yearParam = filter.year ?: -1
                                        val monthParam = filter.month ?: -1
                                        val albumIdParam = filter.albumBucketId ?: -1L
                                        val allMediaParam = filter.isAllMedia
                                        val randomParam = filter.isRandom
                                        val randomStartUriParam = Uri.encode(filter.randomStartUri ?: "")
                                        val titleParam = Uri.encode(filter.displayTitle)

                                        navController.navigate(
                                            "main?mode=$modeParam&year=$yearParam&month=$monthParam&albumId=$albumIdParam&allMedia=$allMediaParam&random=$randomParam&randomStartUri=$randomStartUriParam&title=$titleParam"
                                        )
                                    }
                                )
                            }

                            // Photo Scanner tab (fade only for tab switches)
                            composable(
                                route = BottomNavItem.PhotoScanner.route,
                                enterTransition = { fadeIn(animationSpec = tween(transitionDuration)) },
                                exitTransition = { fadeOut(animationSpec = tween(transitionDuration)) },
                                popEnterTransition = { fadeIn(animationSpec = tween(transitionDuration)) },
                                popExitTransition = { fadeOut(animationSpec = tween(transitionDuration)) }
                            ) {
                                val photoScannerViewModel: PhotoScannerViewModel = viewModel()
                                PhotoScannerScreen(
                                    viewModel = photoScannerViewModel
                                )
                            }

                            // Stats tab (fade only for tab switches)
                            composable(
                                route = BottomNavItem.Stats.route,
                                enterTransition = { fadeIn(animationSpec = tween(transitionDuration)) },
                                exitTransition = { fadeOut(animationSpec = tween(transitionDuration)) },
                                popEnterTransition = { fadeIn(animationSpec = tween(transitionDuration)) },
                                popExitTransition = { fadeOut(animationSpec = tween(transitionDuration)) }
                            ) {
                                val statsViewModel: StatsViewModel = viewModel()
                                StatsScreen(viewModel = statsViewModel)
                            }

                            // Settings tab (fade only for tab switches)
                            composable(
                                route = BottomNavItem.Settings.route,
                                enterTransition = { fadeIn(animationSpec = tween(transitionDuration)) },
                                exitTransition = { fadeOut(animationSpec = tween(transitionDuration)) },
                                popEnterTransition = { fadeIn(animationSpec = tween(transitionDuration)) },
                                popExitTransition = { fadeOut(animationSpec = tween(transitionDuration)) }
                            ) {
                                SettingsTabScreen(
                                    viewModel = photoViewModel
                                )
                            }

                            // Main swipe screen with optional filter parameters
                            composable(
                                route = "main?mode={mode}&year={year}&month={month}&albumId={albumId}&allMedia={allMedia}&random={random}&randomStartUri={randomStartUri}&title={title}",
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
                                    navArgument("random") {
                                        type = NavType.BoolType
                                        defaultValue = false
                                    },
                                    navArgument("randomStartUri") {
                                        type = NavType.StringType
                                        defaultValue = ""
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
                                val random = backStackEntry.arguments?.getBoolean("random") ?: false
                                val randomStartUri = backStackEntry.arguments?.getString("randomStartUri") ?: ""
                                val title = backStackEntry.arguments?.getString("title") ?: ""

                                // Build MenuFilter from URL parameters
                                val menuFilter = if (modeStr.isNotEmpty()) {
                                    MenuFilter(
                                        mode = try { MenuMode.valueOf(modeStr) } catch (e: Exception) { MenuMode.BY_DATE },
                                        year = if (year >= 0) year else null,
                                        month = if (month >= 0) month else null,
                                        albumBucketId = if (albumId >= 0) albumId else null,
                                        isAllMedia = allMedia,
                                        isRandom = random,
                                        randomStartUri = randomStartUri.ifEmpty { null },
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
