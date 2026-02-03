package com.example.photocleanup.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photocleanup.R
import com.example.photocleanup.data.MenuFilter
import com.example.photocleanup.data.MenuMode
import com.example.photocleanup.ui.components.MenuCard
import com.example.photocleanup.ui.components.MenuCardType
import com.example.photocleanup.ui.theme.AccentPrimary
import com.example.photocleanup.ui.theme.AccentPrimaryDim
import com.example.photocleanup.viewmodel.MenuViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MenuScreen(
    viewModel: MenuViewModel,
    onNavigateToSwipe: (MenuFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(permission)

    if (!permissionState.status.isGranted) {
        PermissionScreen(
            onPermissionGranted = { viewModel.loadMenuData() }
        )
    } else {
        LaunchedEffect(Unit) {
            viewModel.loadMenuData()
        }

        Box(modifier = modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentPrimary
                    )
                }

                !uiState.hasAnyUnreviewedPhotos -> {
                    AllCaughtUpContent(modifier = Modifier.fillMaxSize())
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(32.dp))

                        // Title
                        Text(
                            text = stringResource(R.string.menu_title),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = AccentPrimary,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        // Tab row for mode selection
                        TabRow(
                            selectedTabIndex = if (uiState.menuMode == MenuMode.BY_DATE) 0 else 1,
                            containerColor = Color.Transparent,
                            contentColor = AccentPrimary
                        ) {
                            Tab(
                                selected = uiState.menuMode == MenuMode.BY_DATE,
                                onClick = { viewModel.setMenuMode(MenuMode.BY_DATE) },
                                text = { Text(stringResource(R.string.menu_by_date)) }
                            )
                            Tab(
                                selected = uiState.menuMode == MenuMode.BY_ALBUM,
                                onClick = { viewModel.setMenuMode(MenuMode.BY_ALBUM) },
                                text = { Text(stringResource(R.string.menu_by_album)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Cards list
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (uiState.menuMode) {
                                MenuMode.BY_DATE -> {
                                    // Recent Photos card (if available)
                                    uiState.recentPhotosStats?.let { stats ->
                                        item(key = "recent") {
                                            MenuCard(
                                                title = stringResource(R.string.menu_recent_photos),
                                                reviewedCount = stats.reviewedCount,
                                                totalCount = stats.totalCount,
                                                cardType = MenuCardType.RECENT,
                                                onClick = {
                                                    onNavigateToSwipe(
                                                        MenuFilter(
                                                            mode = MenuMode.BY_DATE,
                                                            isRecentPhotos = true,
                                                            displayTitle = "Recent Photos"
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    // Month cards
                                    items(
                                        items = uiState.monthGroups,
                                        key = { "${it.year}-${it.month}" }
                                    ) { monthGroup ->
                                        MenuCard(
                                            title = monthGroup.displayName,
                                            reviewedCount = monthGroup.reviewedCount,
                                            totalCount = monthGroup.totalCount,
                                            cardType = MenuCardType.MONTH,
                                            onClick = {
                                                onNavigateToSwipe(
                                                    MenuFilter(
                                                        mode = MenuMode.BY_DATE,
                                                        year = monthGroup.year,
                                                        month = monthGroup.month,
                                                        displayTitle = monthGroup.displayName
                                                    )
                                                )
                                            }
                                        )
                                    }
                                }

                                MenuMode.BY_ALBUM -> {
                                    items(
                                        items = uiState.albumGroups,
                                        key = { it.bucketId }
                                    ) { albumGroup ->
                                        MenuCard(
                                            title = albumGroup.displayName,
                                            reviewedCount = albumGroup.reviewedCount,
                                            totalCount = albumGroup.totalCount,
                                            cardType = MenuCardType.ALBUM,
                                            onClick = {
                                                onNavigateToSwipe(
                                                    MenuFilter(
                                                        mode = MenuMode.BY_ALBUM,
                                                        albumBucketId = albumGroup.bucketId,
                                                        displayTitle = albumGroup.displayName
                                                    )
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            // Bottom padding
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllCaughtUpContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        AccentPrimaryDim.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\u2728",
                fontSize = 72.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "All Caught Up!",
                style = MaterialTheme.typography.displaySmall,
                color = AccentPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "You've reviewed all your photos. New photos will appear here when added to your gallery.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
