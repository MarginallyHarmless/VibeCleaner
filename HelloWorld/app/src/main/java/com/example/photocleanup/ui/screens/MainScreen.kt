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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.net.Uri
import com.example.photocleanup.data.MenuFilter
import com.example.photocleanup.ui.components.AlbumSelector
import com.example.photocleanup.ui.components.AppButton
import com.example.photocleanup.ui.components.ButtonVariant
import com.example.photocleanup.ui.components.PremiumOverlay
import com.example.photocleanup.ui.components.PremiumUpsellSheet
import com.example.photocleanup.ui.components.DialogButton
import com.example.photocleanup.ui.components.DialogButtonIntent
import com.example.photocleanup.ui.components.ProgressIndicator
import com.example.photocleanup.ui.components.SwipeablePhotoCard
import com.example.photocleanup.ui.theme.AccentPrimary
import com.example.photocleanup.ui.theme.AccentPrimaryDim
import com.example.photocleanup.ui.theme.ActionDelete
import com.example.photocleanup.ui.theme.ActionKeep
import com.example.photocleanup.viewmodel.PhotoViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: PhotoViewModel,
    onNavigateToDelete: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    menuFilter: MenuFilter? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showUpsellSheet by remember { mutableStateOf(false) }

    // Track undo entry animation direction for the specific photo
    var undoPhotoKey by remember { mutableStateOf<Uri?>(null) }
    var undoEntryDirection by remember { mutableIntStateOf(0) }

    val isPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionsState = rememberMultiplePermissionsState(
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        )
        permissionsState.allPermissionsGranted
    } else {
        val permissionState = rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
        permissionState.status.isGranted
    }

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

    if (!isPermissionGranted) {
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

                            Spacer(modifier = Modifier.weight(1f))
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

                                // Action buttons row (fixed height prevents layout shift from async toDeleteCount updates)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ToDeleteBadge(
                                        count = uiState.toDeleteCount,
                                        onClick = onNavigateToDelete,
                                        enabled = uiState.toDeleteCount > 0
                                    )
                                    UndoButton(
                                        onClick = {
                                            val action = uiState.lastAction
                                            if (action != null) {
                                                undoEntryDirection = if (action.action == "to_delete") -1 else 1
                                                undoPhotoKey = action.photo.uri
                                            }
                                            viewModel.undoLastAction()
                                        },
                                        enabled = uiState.lastAction != null
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    // Next photo underneath (zIndex = 0)
                                    // No key() — keeps the composable alive during transitions
                                    // to prevent a black flash when current/next cards swap
                                    uiState.nextPhoto?.let { nextPhoto ->
                                        SwipeablePhotoCard(
                                            mediaItem = nextPhoto,
                                            onSwipeLeft = { },
                                            onSwipeRight = { },
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .zIndex(0f)
                                        )
                                    }

                                    // Current photo on top (zIndex = 1)
                                    uiState.currentPhoto?.let { photo ->
                                        val direction = if (photo.uri == undoPhotoKey) undoEntryDirection else 0
                                        key(photo.uri) {
                                            SwipeablePhotoCard(
                                                mediaItem = photo,
                                                onSwipeLeft = { viewModel.markCurrentPhotoForDeletion() },
                                                onSwipeRight = { viewModel.keepCurrentPhoto() },
                                                entryDirection = direction,
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
                                val needsStorageAccess = viewModel.isPremium &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                    !hasFullStorageAccess()

                                if (needsStorageAccess && !promptDismissed) {
                                    FullStorageAccessPrompt(
                                        onDismiss = { promptDismissed = true },
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }

                                // Album selector at bottom
                                PremiumOverlay(
                                    isLocked = !viewModel.isPremium,
                                    onLockedClick = { showUpsellSheet = true }
                                ) {
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
private fun UndoButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val contentAlpha = if (enabled) 1f else 0.35f
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = AccentPrimary.copy(alpha = if (enabled) 0.15f else 0.07f)
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
                tint = AccentPrimary.copy(alpha = contentAlpha)
            )
            Text(
                text = "Undo",
                style = MaterialTheme.typography.labelLarge,
                color = AccentPrimary.copy(alpha = contentAlpha),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ToDeleteBadge(
    count: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val contentAlpha = if (enabled) 1f else 0.35f

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
        targetValue = if (!enabled) 0.07f else if (isFlashing) 0.4f else 0.15f,
        animationSpec = tween(durationMillis = 150),
        label = "flashAnimation"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
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
                tint = ActionDelete.copy(alpha = contentAlpha)
            )
            Text(
                text = if (enabled) count.toString() else "0",
                style = MaterialTheme.typography.labelLarge,
                color = ActionDelete.copy(alpha = contentAlpha),
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

            AppButton(
                text = "Back",
                onClick = onBack,
                variant = ButtonVariant.Secondary
            )

            Spacer(modifier = Modifier.height(12.dp))

            DialogButton(
                text = "Review Again",
                onClick = onReviewAgain,
                intent = DialogButtonIntent.Positive
            )
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
