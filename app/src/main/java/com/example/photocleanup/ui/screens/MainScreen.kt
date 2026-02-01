package com.example.photocleanup.ui.screens

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.photocleanup.ui.components.ProgressIndicator
import com.example.photocleanup.ui.components.SwipeablePhotoCard
import com.example.photocleanup.ui.theme.AccentPrimary
import com.example.photocleanup.ui.theme.AccentPrimaryDim
import com.example.photocleanup.ui.theme.ActionDelete
import com.example.photocleanup.ui.theme.ActionKeep
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
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

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

    LaunchedEffect(uiState.deleteIntentSender) {
        uiState.deleteIntentSender?.let { intentSender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    if (!permissionState.status.isGranted) {
        PermissionScreen(
            onPermissionGranted = { viewModel.loadPhotos() }
        )
    } else {
        LaunchedEffect(Unit) {
            viewModel.loadPhotos()
        }

        Box(modifier = modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentPrimary
                    )
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top bar - ALWAYS shown when not loading
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: ToDelete badge (only if count > 0)
                            if (uiState.toDeleteCount > 0) {
                                ToDeleteBadge(
                                    count = uiState.toDeleteCount,
                                    onClick = onNavigateToDelete
                                )
                            } else {
                                Spacer(modifier = Modifier.width(48.dp))
                            }

                            // Right: Settings icon
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings"
                                )
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
                                                onSwipeLeft = { viewModel.keepCurrentPhoto() },
                                                onSwipeRight = { viewModel.markCurrentPhotoForDeletion() },
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .zIndex(1f)
                                            )
                                        }
                                    }
                                }

                                // Swipe hints row with undo button in center
                                SwipeHintsWithUndo(
                                    showUndo = uiState.lastAction != null,
                                    onUndo = { viewModel.undoLastAction() },
                                    modifier = Modifier.padding(bottom = 16.dp)
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
private fun SwipeHintsWithUndo(
    showUndo: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left hint - simplified
        Text(
            text = "\u2190 Keep",
            style = MaterialTheme.typography.bodyLarge,
            color = ActionKeep,
            fontWeight = FontWeight.Medium
        )

        // Center: Undo button (only if available)
        if (showUndo) {
            OutlinedButton(
                onClick = onUndo,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AccentPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = "Undo")
            }
        } else {
            // Invisible spacer to maintain layout
            Spacer(modifier = Modifier.width(100.dp))
        }

        // Right hint - simplified
        Text(
            text = "Delete \u2192",
            style = MaterialTheme.typography.bodyLarge,
            color = ActionDelete,
            fontWeight = FontWeight.Medium
        )
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
