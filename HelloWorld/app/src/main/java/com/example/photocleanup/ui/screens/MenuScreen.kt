package com.example.photocleanup.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.example.photocleanup.ui.components.HeroCard
import com.example.photocleanup.ui.components.MenuCard
import com.example.photocleanup.ui.components.MenuCardType
import com.example.photocleanup.ui.components.PremiumOverlay
import com.example.photocleanup.ui.components.PremiumUpsellSheet
import com.example.photocleanup.ui.theme.AccentPrimary
import com.example.photocleanup.ui.theme.AccentPrimaryDim
import com.example.photocleanup.viewmodel.MenuViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Shuffle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MenuScreen(
    viewModel: MenuViewModel,
    onNavigateToSwipe: (MenuFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showUpsellSheet by remember { mutableStateOf(false) }

    val isPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionsState = rememberMultiplePermissionsState(
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        )
        permissionsState.allPermissionsGranted
    } else {
        val permissionState = rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
        permissionState.status.isGranted
    }

    if (!isPermissionGranted) {
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
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Title
                        Text(
                            text = stringResource(R.string.menu_title),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        // Custom segmented controller
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            // "By Date" segment
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (uiState.menuMode == MenuMode.BY_DATE) AccentPrimary else Color.Transparent)
                                    .clickable { viewModel.setMenuMode(MenuMode.BY_DATE) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.menu_by_date),
                                    color = if (uiState.menuMode == MenuMode.BY_DATE) Color.White else Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            // "By Album" segment
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (uiState.menuMode == MenuMode.BY_ALBUM) AccentPrimary else Color.Transparent)
                                    .clickable { viewModel.setMenuMode(MenuMode.BY_ALBUM) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.menu_by_album),
                                    color = if (uiState.menuMode == MenuMode.BY_ALBUM) Color.White else Color.White.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Cards list
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            when (uiState.menuMode) {
                                MenuMode.BY_DATE -> {
                                    // Hero cards row (All Media + Random)
                                    uiState.allMediaStats?.let { stats ->
                                        item(key = "hero_cards") {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                HeroCard(
                                                    title = stringResource(R.string.menu_all_media),
                                                    icon = Icons.Default.Photo,
                                                    reviewedCount = stats.reviewedCount,
                                                    totalCount = stats.totalCount,
                                                    thumbnailUri = uiState.mostRecentMediaUri,
                                                    onClick = {
                                                        onNavigateToSwipe(
                                                            MenuFilter(
                                                                mode = MenuMode.BY_DATE,
                                                                isAllMedia = true,
                                                                displayTitle = "All Media"
                                                            )
                                                        )
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                PremiumOverlay(
                                                    isLocked = !viewModel.isPremium,
                                                    onLockedClick = { showUpsellSheet = true },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    HeroCard(
                                                        title = stringResource(R.string.menu_random),
                                                        icon = Icons.Default.Shuffle,
                                                        reviewedCount = stats.reviewedCount,
                                                        totalCount = stats.totalCount,
                                                        thumbnailUri = uiState.randomMediaUri,
                                                        showLock = false,
                                                        onClick = {
                                                            if (viewModel.isPremium) {
                                                                onNavigateToSwipe(
                                                                    MenuFilter(
                                                                        mode = MenuMode.BY_DATE,
                                                                        isRandom = true,
                                                                        randomStartUri = uiState.randomMediaUri?.toString(),
                                                                        displayTitle = "Random"
                                                                    )
                                                                )
                                                            } else {
                                                                showUpsellSheet = true
                                                            }
                                                        }
                                                    )
                                                }
                                            }
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

                            // Bottom padding for navigation bar clearance
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }
            }
        }

        if (showUpsellSheet) {
            PremiumUpsellSheet(
                onDismiss = { showUpsellSheet = false },
                onUnlockClick = { showUpsellSheet = false },
                onRestoreClick = { showUpsellSheet = false }
            )
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
