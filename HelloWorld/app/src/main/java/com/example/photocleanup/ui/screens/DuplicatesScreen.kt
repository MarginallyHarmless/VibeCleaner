package com.example.photocleanup.ui.screens

import android.Manifest
import android.app.Activity
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.photocleanup.data.ScanState
import com.example.photocleanup.ui.components.DuplicateGroupCard
import com.example.photocleanup.ui.components.PrimaryButton
import com.example.photocleanup.ui.components.ScanStatusCard
import com.example.photocleanup.ui.theme.AccentPrimary
import com.example.photocleanup.ui.theme.AccentPrimaryDim
import com.example.photocleanup.ui.theme.ActionDelete
import com.example.photocleanup.ui.theme.ActionRefresh
import com.example.photocleanup.ui.theme.TextSecondary
import com.example.photocleanup.viewmodel.DuplicatesViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Main screen for duplicate photo detection and management.
 * Displays scan controls, progress, and results.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DuplicatesScreen(
    viewModel: DuplicatesViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val scanState by viewModel.scanState.collectAsState()
    val duplicateGroups by viewModel.duplicateGroups.collectAsState()
    val selectedPhotoIds by viewModel.selectedPhotoIds.collectAsState()
    val groupCount by viewModel.groupCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val pendingDeleteRequest by viewModel.pendingDeleteRequest.collectAsState()

    // Permission check
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(permission)

    // Launcher for delete permission on Android 11+
    val deletePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeletePermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    // Launch delete permission request when pending
    LaunchedEffect(pendingDeleteRequest) {
        pendingDeleteRequest?.let { request ->
            val intentSenderRequest = IntentSenderRequest.Builder(request.intentSender).build()
            deletePermissionLauncher.launch(intentSenderRequest)
        }
    }

    // State for fullscreen photo preview (long-press)
    var fullscreenPhotoUri by remember { mutableStateOf<String?>(null) }

    if (!permissionState.status.isGranted) {
        PermissionScreen(
            onPermissionGranted = { viewModel.loadDuplicateGroups() }
        )
    } else {
        Box(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "Duplicates",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Subtitle based on state
                Text(
                    text = when {
                        scanState is ScanState.Scanning || scanState is ScanState.Queued ->
                            "Scanning your photos..."
                        groupCount > 0 ->
                            "$groupCount duplicate groups found"
                        scanState is ScanState.Completed && groupCount == 0 ->
                            "No duplicates found"
                        else ->
                            "Find and remove duplicate photos"
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
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AccentPrimary)
                        }
                    }

                    scanState is ScanState.Idle && groupCount == 0 -> {
                        InitialScanContent(
                            onStartScan = { viewModel.startScan() },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    scanState is ScanState.Completed && groupCount == 0 -> {
                        NoDuplicatesContent(
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
                        // Show duplicate groups
                        DuplicateGroupsList(
                            groups = duplicateGroups,
                            selectedPhotoIds = selectedPhotoIds,
                            onPhotoSelect = { viewModel.togglePhotoSelection(it) },
                            onLongPressPhoto = { uri -> fullscreenPhotoUri = uri },
                            onLongPressRelease = { fullscreenPhotoUri = null },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Bottom action row (delete button + refresh FAB)
            if (groupCount > 0 && scanState !is ScanState.Scanning && scanState !is ScanState.Queued) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Delete button (only shown when photos are selected)
                    if (selectedPhotoIds.isNotEmpty()) {
                        Button(
                            onClick = {
                                activity?.let { viewModel.deleteSelectedPhotos(it) }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentPrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Delete ${selectedPhotoIds.size} photos",
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }

                    // Refresh FAB (teal)
                    FloatingActionButton(
                        onClick = { viewModel.startScan() },
                        containerColor = ActionRefresh
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Rescan",
                            tint = Color.White
                        )
                    }
                }
            }

            // Fullscreen photo overlay (shown when long-pressing a photo)
            fullscreenPhotoUri?.let { uri ->
                FullscreenPhotoOverlay(photoUri = uri)
            }
        }
    }
}

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
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                tint = AccentPrimary,
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Find Duplicate Photos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AccentPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Scan your gallery to find similar and duplicate photos. Free up storage by removing the ones you don't need.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            PrimaryButton(
                text = "Scan for Duplicates",
                onClick = onStartScan
            )
        }
    }
}

@Composable
private fun NoDuplicatesContent(
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
                text = "No Duplicates Found",
                style = MaterialTheme.typography.headlineMedium,
                color = AccentPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your photo gallery looks clean! We didn't find any duplicate or similar photos.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onRescan) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = AccentPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scan Again",
                    color = AccentPrimary
                )
            }
        }
    }
}

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
            color = ActionDelete,
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

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentPrimary
            )
        ) {
            Text("Try Again")
        }
    }
}

@Composable
private fun DuplicateGroupsList(
    groups: List<com.example.photocleanup.data.DuplicateGroupWithPhotos>,
    selectedPhotoIds: Set<Long>,
    onPhotoSelect: (Long) -> Unit,
    onLongPressPhoto: (String) -> Unit,
    onLongPressRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Remember scroll state to preserve position across recompositions
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = groups,
            key = { it.groupId }
        ) { group ->
            DuplicateGroupCard(
                group = group,
                selectedPhotoIds = selectedPhotoIds,
                onPhotoSelect = onPhotoSelect,
                onLongPressPhoto = onLongPressPhoto,
                onLongPressRelease = onLongPressRelease
            )
        }

        // Bottom padding for FAB
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
