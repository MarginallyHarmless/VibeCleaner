package com.example.photocleanup.ui.screens

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.photocleanup.data.ScanState
import com.example.photocleanup.ui.components.AppButton
import com.example.photocleanup.ui.components.AppFab
import com.example.photocleanup.ui.components.ButtonIntent
import com.example.photocleanup.ui.components.LowQualityPhotoCard
import com.example.photocleanup.ui.components.ScanStatusCard
import com.example.photocleanup.ui.theme.AccentPrimary
import com.example.photocleanup.ui.theme.AccentPrimaryDim
import com.example.photocleanup.ui.theme.DarkBackground
import com.example.photocleanup.ui.theme.DarkSurfaceSubtle
import com.example.photocleanup.ui.theme.TextSecondary
import com.example.photocleanup.viewmodel.LowQualityViewModel

/**
 * Screen for displaying and managing low quality photos (blurry, dark, overexposed).
 * Shows a grid view of photos with quality issues detected during the scan.
 */
@Composable
fun LowQualityScreen(
    viewModel: LowQualityViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val scanState by viewModel.scanState.collectAsState()
    val lowQualityPhotos by viewModel.lowQualityPhotos.collectAsState()
    val selectedUris by viewModel.selectedUris.collectAsState()
    val previewUri by viewModel.previewUri.collectAsState()
    val lowQualityCount by viewModel.lowQualityCount.collectAsState()

    // State for fullscreen photo preview (long-press)
    var fullscreenPhotoUri by remember { mutableStateOf<String?>(null) }

    // Delete launcher for Android R+
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val deletedUris = selectedUris.map { Uri.parse(it) }
            viewModel.onPhotosDeleted(deletedUris)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = "Low Quality Photos",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Dynamic subtitle based on state
            Text(
                text = when {
                    scanState is ScanState.Scanning || scanState is ScanState.Queued ->
                        "Scanning your photos..."
                    lowQualityCount > 0 ->
                        "$lowQualityCount photos need attention"
                    scanState is ScanState.Completed && lowQualityCount == 0 ->
                        "No quality issues found"
                    else ->
                        "Scan to find blurry, dark, or overexposed photos"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Scan status card (shown when scanning)
            ScanStatusCard(
                scanState = scanState,
                onCancelScan = { viewModel.cancelScan() },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Content based on state
            when {
                scanState is ScanState.Idle && lowQualityCount == 0 -> {
                    InitialScanContent(
                        onStartScan = { viewModel.startScan() },
                        modifier = Modifier.weight(1f)
                    )
                }

                scanState is ScanState.Completed && lowQualityCount == 0 -> {
                    NoIssuesContent(
                        onRescan = { viewModel.startScan() },
                        modifier = Modifier.weight(1f)
                    )
                }

                scanState is ScanState.Error -> {
                    ErrorContent(
                        message = (scanState as ScanState.Error).message,
                        onRetry = { viewModel.startScan() },
                        modifier = Modifier.weight(1f)
                    )
                }

                else -> {
                    // Show low quality photos grid
                    LowQualityPhotoGrid(
                        photos = lowQualityPhotos,
                        selectedUris = selectedUris,
                        onToggleSelection = { viewModel.toggleSelection(it) },
                        onLongPressPhoto = { uri -> fullscreenPhotoUri = uri },
                        onLongPressRelease = { fullscreenPhotoUri = null },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Bottom action bar (when photos exist and not scanning)
        if (lowQualityPhotos.isNotEmpty() && scanState !is ScanState.Scanning && scanState !is ScanState.Queued) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = DarkSurfaceSubtle,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Select all button
                    TextButton(
                        onClick = {
                            if (selectedUris.size == lowQualityPhotos.size) {
                                viewModel.clearSelection()
                            } else {
                                viewModel.selectAll()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (selectedUris.size == lowQualityPhotos.size) "Deselect All" else "Select All",
                            color = TextSecondary
                        )
                    }

                    // Delete button (only shown when photos are selected)
                    if (selectedUris.isNotEmpty()) {
                        AppButton(
                            text = "Delete ${selectedUris.size}",
                            onClick = {
                                val urisToDelete = selectedUris.map { Uri.parse(it) }
                                requestDeletePhotos(context, urisToDelete, deleteLauncher)
                            },
                            intent = ButtonIntent.Destructive,
                            leadingIcon = Icons.Filled.Delete,
                            fillWidth = false
                        )
                    }
                }
            }
        }

        // FAB for starting scan (when not scanning and no bottom bar)
        if (lowQualityPhotos.isEmpty() && scanState !is ScanState.Scanning && scanState !is ScanState.Queued) {
            // FAB handled by InitialScanContent
        } else if (lowQualityPhotos.isNotEmpty() && scanState !is ScanState.Scanning && scanState !is ScanState.Queued) {
            // Refresh FAB in bottom right
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppFab(
                    onClick = { viewModel.startScan() },
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Rescan"
                )
            }
        }

        // Fullscreen photo overlay (shown when long-pressing a photo)
        fullscreenPhotoUri?.let { uri ->
            FullscreenPhotoOverlay(photoUri = uri)
        }
    }
}

/**
 * Content shown before first scan.
 */
@Composable
private fun InitialScanContent(
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        AccentPrimaryDim.copy(alpha = 0.2f),
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
            Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = null,
                tint = AccentPrimary,
                modifier = Modifier.height(72.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Find Low Quality Photos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AccentPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Scan your gallery to find blurry, dark, or overexposed photos. Free up storage by removing the ones you don't need.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            AppButton(
                text = "Scan for Issues",
                onClick = onStartScan
            )
        }
    }
}

/**
 * Content shown when scan completes with no quality issues.
 */
@Composable
private fun NoIssuesContent(
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        AccentPrimaryDim.copy(alpha = 0.2f),
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
                text = "No Quality Issues Found",
                style = MaterialTheme.typography.headlineMedium,
                color = AccentPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your photos look great! We didn't find any blurry, dark, or overexposed photos.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            AppButton(
                text = "Scan Again",
                onClick = onRescan
            )
        }
    }
}

/**
 * Content shown when scan fails.
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.headlineSmall,
            color = com.example.photocleanup.ui.theme.ActionDelete,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        AppButton(
            text = "Try Again",
            onClick = onRetry
        )
    }
}

/**
 * Grid of low quality photos with selection support.
 */
@Composable
private fun LowQualityPhotoGrid(
    photos: List<com.example.photocleanup.data.PhotoHash>,
    selectedUris: Set<String>,
    onToggleSelection: (String) -> Unit,
    onLongPressPhoto: (String) -> Unit,
    onLongPressRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = photos,
            key = { it.uri }
        ) { photo ->
            LowQualityPhotoCard(
                photo = photo,
                isSelected = photo.uri in selectedUris,
                onToggleSelection = { onToggleSelection(photo.uri) },
                onLongPress = { onLongPressPhoto(photo.uri) }
            )
        }
    }
}

/**
 * Fullscreen photo overlay for preview on long-press.
 */
@Composable
private fun FullscreenPhotoOverlay(
    photoUri: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photoUri)
                .crossfade(true)
                .build(),
            contentDescription = "Fullscreen preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Request deletion of photos using MediaStore.createDeleteRequest for Android R+.
 * For older versions, photos are deleted directly.
 */
private fun requestDeletePhotos(
    context: android.content.Context,
    uris: List<Uri>,
    launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val pendingIntent = MediaStore.createDeleteRequest(
            context.contentResolver,
            uris
        )
        launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    } else {
        // For older versions, delete directly (requires WRITE_EXTERNAL_STORAGE)
        uris.forEach { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                // Handle deletion error
            }
        }
    }
}
