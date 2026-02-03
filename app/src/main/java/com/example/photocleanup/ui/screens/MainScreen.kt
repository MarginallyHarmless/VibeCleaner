package com.example.photocleanup.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.photocleanup.data.MenuFilter
import com.example.photocleanup.ui.components.AlbumSelector
import com.example.photocleanup.ui.components.ProgressIndicator
import com.example.photocleanup.ui.components.SwipeablePhotoCard
import com.example.photocleanup.ui.theme.AccentPrimary
import com.example.photocleanup.ui.theme.AccentPrimaryDim
import com.example.photocleanup.ui.theme.ActionDelete
import com.example.photocleanup.viewmodel.PhotoViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: PhotoViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToDelete: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    menuFilter: MenuFilter? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(permission)

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeleteConfirmed(result.resultCode == android.app.Activity.RESULT_OK)
    }

    // Launcher for move permission (Android 11+)
    val moveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onMovePermissionResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.deleteIntentSender) {
        uiState.deleteIntentSender?.let { intentSender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    // Handle move permission request
    LaunchedEffect(uiState.moveIntentSender) {
        uiState.moveIntentSender?.let { intentSender ->
            moveLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    // Show move error as toast
    LaunchedEffect(uiState.moveError) {
        uiState.moveError?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            viewModel.clearMoveError()
        }
    }

    // Load current photo album when photo changes
    LaunchedEffect(uiState.currentPhoto) {
        if (uiState.currentPhoto != null) {
            viewModel.loadCurrentPhotoAlbum()
        }
    }

    if (!permissionState.status.isGranted) {
        PermissionScreen(
            onPermissionGranted = {
                if (menuFilter != null) {
                    viewModel.loadPhotosWithMenuFilter(menuFilter)
                } else {
                    viewModel.loadPhotos()
                }
            }
        )
    } else {
        LaunchedEffect(menuFilter) {
            if (menuFilter != null) {
                viewModel.loadPhotosWithMenuFilter(menuFilter)
            } else {
                viewModel.loadPhotos()
            }
            viewModel.loadAvailableFolders()
        }

        // Keep album list scroll state at this level so it survives isLoading toggles
        val albumListState = rememberLazyListState()

        Box(modifier = modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentPrimary
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        // Top bar - ALWAYS shown when not loading
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left side: Back button + Title
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Back button if navigating from menu
                                if (onNavigateBack != null) {
                                    IconButton(onClick = onNavigateBack) {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowBack,
                                            contentDescription = "Back to menu"
                                        )
                                    }
                                }

                                // Title next to back arrow
                                if (menuFilter != null && menuFilter.displayTitle.isNotEmpty()) {
                                    Text(
                                        text = menuFilter.displayTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }

                            // Right: Settings icon
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings"
                                )
                            }
                        }

                        // Delete/Undo buttons row (below title)
                        if (uiState.toDeleteCount > 0 || uiState.lastAction != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (uiState.toDeleteCount > 0) {
                                    ToDeleteBadge(
                                        count = uiState.toDeleteCount,
                                        onClick = onNavigateToDelete
                                    )
                                }
                                if (uiState.lastAction != null) {
                                    UndoButton(onClick = { viewModel.undoLastAction() })
                                }
                            }
                        }

                        // Content area
                        when {
                            uiState.isAllDone -> {
                                if (uiState.showCelebration) {
                                    AllDoneContent(
                                        totalReviewed = uiState.reviewedCount,
                                        onReviewAgain = { viewModel.resetAllReviews() },
                                        onBack = { viewModel.dismissCelebration() },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    EmptyStateContent(modifier = Modifier.weight(1f))
                                }
                            }

                            uiState.hasPhotos -> {
                                ProgressIndicator(
                                    currentIndex = uiState.currentIndex,
                                    totalCount = uiState.totalPhotosCount,
                                    reviewedCount = uiState.reviewedCount
                                )

                                Box(modifier = Modifier.weight(1f)) {
                                    // Next photo underneath (zIndex = 0)
                                    uiState.nextPhoto?.let { nextPhoto ->
                                        key(nextPhoto) {
                                            SwipeablePhotoCard(
                                                photo = nextPhoto,
                                                onSwipeLeft = { },
                                                onSwipeRight = { },
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .zIndex(0f)
                                            )
                                        }
                                    }

                                    // Current photo on top (zIndex = 1)
                                    uiState.currentPhoto?.let { photo ->
                                        key(photo) {
                                            SwipeablePhotoCard(
                                                photo = photo,
                                                onSwipeLeft = { viewModel.markCurrentPhotoForDeletion() },
                                                onSwipeRight = { viewModel.keepCurrentPhoto() },
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .zIndex(1f)
                                            )
                                        }
                                    }
                                }

                                // Full storage access prompt (Android 11+)
                                // Only show if not already granted and user hasn't dismissed
                                var promptDismissed by rememberSaveable { mutableStateOf(false) }
                                val needsStorageAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                    !hasFullStorageAccess()

                                if (needsStorageAccess && !promptDismissed) {
                                    FullStorageAccessPrompt(
                                        onDismiss = { promptDismissed = true },
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }

                                // Album selector at bottom
                                AlbumSelector(
                                    albums = uiState.availableFolders,
                                    currentAlbumId = uiState.currentPhotoAlbum?.bucketId,
                                    onAlbumSelected = { viewModel.movePhotoToAlbum(it) },
                                    isMoving = uiState.isMovingPhoto,
                                    listState = albumListState,
                                    modifier = Modifier.padding(bottom = 32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UndoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = AccentPrimary.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = AccentPrimary
            )
            Text(
                text = "Undo",
                style = MaterialTheme.typography.labelLarge,
                color = AccentPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ToDeleteBadge(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Track previous count to detect increments
    var lastCount by remember { mutableIntStateOf(count) }
    var isFlashing by remember { mutableStateOf(false) }

    // Detect when count increases (photo added to delete list)
    LaunchedEffect(count) {
        if (count > lastCount) {
            isFlashing = true
            delay(300)
            isFlashing = false
        }
        lastCount = count
    }

    // Animate background alpha
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isFlashing) 0.4f else 0.15f,
        animationSpec = tween(durationMillis = 150),
        label = "flashAnimation"
    )

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = ActionDelete.copy(alpha = backgroundAlpha)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = ActionDelete
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = ActionDelete,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}


@Composable
private fun AllDoneContent(
    totalReviewed: Int,
    onReviewAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
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
                text = "\uD83C\uDF89",
                fontSize = 72.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "All Done!",
                style = MaterialTheme.typography.displaySmall,
                color = AccentPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "You've reviewed all $totalReviewed photos.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.clip(RoundedCornerShape(24.dp))
            ) {
                Text(
                    text = "Back",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onReviewAgain) {
                Text(
                    text = "Review Again",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun EmptyStateContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "\uD83D\uDCF7",
            fontSize = 64.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No photos to review",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "New photos will appear here when you add them to your gallery.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
