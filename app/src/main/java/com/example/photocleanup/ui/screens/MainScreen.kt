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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photocleanup.ui.components.ProgressIndicator
import com.example.photocleanup.ui.components.SwipeablePhotoCard
import com.example.photocleanup.ui.theme.VibeCoral
import com.example.photocleanup.ui.theme.VibeCoralLight
import com.example.photocleanup.ui.theme.VibeDelete
import com.example.photocleanup.ui.theme.VibeKeep
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
                        color = VibeCoral
                    )
                }

                uiState.isAllDone -> {
                    AllDoneScreen(
                        totalReviewed = uiState.reviewedCount,
                        onReviewAgain = {
                            viewModel.resetAllReviews()
                        }
                    )
                }

                uiState.hasPhotos -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top bar with ToDelete badge and Filters icon
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

                            // Right: Filters icon (was Settings)
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Filters"
                                )
                            }
                        }

                        ProgressIndicator(
                            currentIndex = uiState.currentIndex,
                            totalCount = uiState.totalPhotosCount,
                            reviewedCount = uiState.reviewedCount
                        )

                        uiState.currentPhoto?.let { photo ->
                            SwipeablePhotoCard(
                                photo = photo,
                                onSwipeLeft = { viewModel.keepCurrentPhoto() },
                                onSwipeRight = { viewModel.markCurrentPhotoForDeletion() },
                                modifier = Modifier.weight(1f)
                            )
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
            color = VibeKeep,
            fontWeight = FontWeight.Medium
        )

        // Center: Undo button (only if available)
        if (showUndo) {
            OutlinedButton(
                onClick = onUndo,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = VibeCoral
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
            color = VibeDelete,
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
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = VibeDelete.copy(alpha = 0.15f)
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
                tint = VibeDelete
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = VibeDelete,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}


@Composable
private fun AllDoneScreen(
    totalReviewed: Int,
    onReviewAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        VibeCoralLight.copy(alpha = 0.3f),
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
                color = VibeCoral,
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
                onClick = onReviewAgain,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Text(
                    text = "Review Again",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}
