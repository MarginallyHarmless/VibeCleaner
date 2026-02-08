package com.example.photocleanup.ui.screens

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.photocleanup.data.DuplicateGroupWithPhotos
import com.example.photocleanup.data.PhotoHash
import com.example.photocleanup.data.PhotoScannerTab
import com.example.photocleanup.data.ScanState
import androidx.compose.material.icons.filled.Lock
import com.example.photocleanup.ui.components.AppButton
import com.example.photocleanup.ui.components.AppFab
import com.example.photocleanup.ui.components.ButtonIntent
import com.example.photocleanup.ui.components.PremiumUpsellSheet
import com.example.photocleanup.ui.components.DialogButton
import com.example.photocleanup.ui.components.DialogButtonIntent
import com.example.photocleanup.ui.components.DuplicateGroupCard
import com.example.photocleanup.ui.components.FullscreenPhotoInfo
import com.example.photocleanup.ui.components.LowQualityPhotoCard
import com.example.photocleanup.ui.components.ScanStatusCard
import com.example.photocleanup.ui.theme.AccentPrimary
import com.example.photocleanup.ui.theme.AccentPrimaryDim
import com.example.photocleanup.ui.theme.DarkBackground
import com.example.photocleanup.ui.theme.TextSecondary
import com.example.photocleanup.viewmodel.PhotoScannerViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Combined screen for duplicate detection and low quality photo management.
 * Uses a segmented button to switch between the two views.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PhotoScannerScreen(
    viewModel: PhotoScannerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Shared state
    val selectedTab by viewModel.selectedTab.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Duplicates state
    val duplicateGroups by viewModel.duplicateGroups.collectAsState()
    val selectedPhotoIds by viewModel.selectedPhotoIds.collectAsState()
    val groupCount by viewModel.groupCount.collectAsState()
    val pendingDeleteRequest by viewModel.pendingDeleteRequest.collectAsState()

    // Low quality state
    val lowQualityPhotos by viewModel.lowQualityPhotos.collectAsState()
    val selectedLowQualityUris by viewModel.selectedLowQualityUris.collectAsState()
    val lowQualityCount by viewModel.lowQualityCount.collectAsState()

    // Permission check
    val isPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionsState = rememberMultiplePermissionsState(
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        )
        permissionsState.allPermissionsGranted
    } else {
        val permissionState = rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)
        permissionState.status.isGranted
    }

    // Launcher for delete permission on Android 11+ (duplicates)
    val duplicateDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeletePermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    // Launcher for delete permission on Android 11+ (low quality)
    val lowQualityDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val deletedUris = selectedLowQualityUris.map { Uri.parse(it) }
            viewModel.onLowQualityPhotosDeleted(deletedUris)
        }
    }

    // Launch delete permission request when pending
    LaunchedEffect(pendingDeleteRequest) {
        pendingDeleteRequest?.let { request ->
            val intentSenderRequest = IntentSenderRequest.Builder(request.intentSender).build()
            if (request.isDuplicates) {
                duplicateDeleteLauncher.launch(intentSenderRequest)
            } else {
                lowQualityDeleteLauncher.launch(intentSenderRequest)
            }
        }
    }

    // State for fullscreen photo preview (long-press)
    var fullscreenPhoto by remember { mutableStateOf<FullscreenPhotoInfo?>(null) }
    var showUpsellSheet by remember { mutableStateOf(false) }

    if (!isPermissionGranted) {
        PermissionScreen(
            onPermissionGranted = { viewModel.loadDuplicateGroups() }
        )
    } else {
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
                    text = "Photo Scanner",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Dynamic subtitle based on state and tab
                Text(
                    text = getSubtitle(
                        scanState = scanState,
                        selectedTab = selectedTab,
                        groupCount = groupCount,
                        lowQualityCount = lowQualityCount
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Segmented button
                SegmentedTabButton(
                    selectedTab = selectedTab,
                    onTabSelected = { viewModel.selectTab(it) },
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Scan status card (shown when scanning)
                ScanStatusCard(
                    scanState = scanState,
                    onCancelScan = { viewModel.cancelScan() },
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Content based on selected tab
                when (selectedTab) {
                    PhotoScannerTab.DUPLICATES -> {
                        DuplicatesContent(
                            scanState = scanState,
                            isLoading = isLoading,
                            duplicateGroups = duplicateGroups,
                            groupCount = groupCount,
                            selectedPhotoIds = selectedPhotoIds,
                            onPhotoSelect = {
                                if (viewModel.isPremium) viewModel.togglePhotoSelection(it)
                                else showUpsellSheet = true
                            },
                            onLongPressPhoto = { info -> fullscreenPhoto = info },
                            onLongPressRelease = { fullscreenPhoto = null },
                            onStartScan = { viewModel.startScan() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PhotoScannerTab.LOW_QUALITY -> {
                        LowQualityContent(
                            scanState = scanState,
                            lowQualityPhotos = lowQualityPhotos,
                            lowQualityCount = lowQualityCount,
                            selectedUris = selectedLowQualityUris,
                            onToggleSelection = {
                                if (viewModel.isPremium) viewModel.toggleLowQualitySelection(it)
                                else showUpsellSheet = true
                            },
                            onLongPressPhoto = { info -> fullscreenPhoto = info },
                            onLongPressRelease = { fullscreenPhoto = null },
                            onStartScan = { viewModel.startScan() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Bottom action bar based on tab
            when (selectedTab) {
                PhotoScannerTab.DUPLICATES -> {
                    if (groupCount > 0 && scanState !is ScanState.Scanning && scanState !is ScanState.Queued) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!viewModel.isPremium) {
                                AppButton(
                                    text = "Delete",
                                    onClick = { showUpsellSheet = true },
                                    intent = ButtonIntent.Destructive,
                                    leadingIcon = Icons.Filled.Lock,
                                    fillWidth = false
                                )
                            } else if (selectedPhotoIds.isNotEmpty()) {
                                AppButton(
                                    text = "Delete ${selectedPhotoIds.size} photos",
                                    onClick = {
                                        activity?.let { viewModel.deleteSelectedDuplicates(it) }
                                    },
                                    intent = ButtonIntent.Destructive,
                                    leadingIcon = Icons.Filled.Delete,
                                    fillWidth = false
                                )
                            }
                            AppFab(
                                onClick = { viewModel.startScan() },
                                icon = Icons.Filled.Refresh,
                                contentDescription = "Rescan"
                            )
                        }
                    }
                }
                PhotoScannerTab.LOW_QUALITY -> {
                    if (lowQualityPhotos.isNotEmpty() && scanState !is ScanState.Scanning && scanState !is ScanState.Queued) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!viewModel.isPremium) {
                                AppButton(
                                    text = "Delete",
                                    onClick = { showUpsellSheet = true },
                                    intent = ButtonIntent.Destructive,
                                    leadingIcon = Icons.Filled.Lock,
                                    fillWidth = false
                                )
                            } else if (selectedLowQualityUris.isNotEmpty()) {
                                AppButton(
                                    text = "Delete ${selectedLowQualityUris.size} photos",
                                    onClick = {
                                        val urisToDelete = selectedLowQualityUris.map { Uri.parse(it) }
                                        requestDeletePhotos(context, urisToDelete, lowQualityDeleteLauncher)
                                    },
                                    intent = ButtonIntent.Destructive,
                                    leadingIcon = Icons.Filled.Delete,
                                    fillWidth = false
                                )
                            }
                            AppFab(
                                onClick = { viewModel.startScan() },
                                icon = Icons.Filled.Refresh,
                                contentDescription = "Rescan"
                            )
                        }
                    }
                }
            }

            // Fullscreen photo overlay
            fullscreenPhoto?.let { info ->
                FullscreenPhotoOverlay(
                    photoInfo = info,
                    onDismiss = { fullscreenPhoto = null }
                )
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
private fun getSubtitle(
    scanState: ScanState,
    selectedTab: PhotoScannerTab,
    groupCount: Int,
    lowQualityCount: Int
): String {
    return when {
        scanState is ScanState.Scanning || scanState is ScanState.Queued ->
            "Scanning your photos..."
        scanState is ScanState.Completed && groupCount == 0 && lowQualityCount == 0 ->
            "No issues found"
        groupCount > 0 || lowQualityCount > 0 -> {
            val parts = mutableListOf<String>()
            if (groupCount > 0) parts.add("$groupCount duplicate groups")
            if (lowQualityCount > 0) parts.add("$lowQualityCount low quality")
            parts.joinToString(", ")
        }
        else -> when (selectedTab) {
            PhotoScannerTab.DUPLICATES -> "Find and remove duplicate photos"
            PhotoScannerTab.LOW_QUALITY -> "Find blurry, dark, or overexposed photos"
        }
    }
}

@Composable
private fun SegmentedTabButton(
    selectedTab: PhotoScannerTab,
    onTabSelected: (PhotoScannerTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        // Duplicates segment
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selectedTab == PhotoScannerTab.DUPLICATES) AccentPrimary else Color.Transparent)
                .clickable { onTabSelected(PhotoScannerTab.DUPLICATES) }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Duplicates",
                color = if (selectedTab == PhotoScannerTab.DUPLICATES) Color.White else Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.SemiBold
            )
        }

        // Low Quality segment
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selectedTab == PhotoScannerTab.LOW_QUALITY) AccentPrimary else Color.Transparent)
                .clickable { onTabSelected(PhotoScannerTab.LOW_QUALITY) }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Low Quality",
                color = if (selectedTab == PhotoScannerTab.LOW_QUALITY) Color.White else Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DuplicatesContent(
    scanState: ScanState,
    isLoading: Boolean,
    duplicateGroups: List<DuplicateGroupWithPhotos>,
    groupCount: Int,
    selectedPhotoIds: Set<Long>,
    onPhotoSelect: (Long) -> Unit,
    onLongPressPhoto: (FullscreenPhotoInfo) -> Unit,
    onLongPressRelease: () -> Unit,
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentPrimary)
            }
        }
        scanState is ScanState.Idle && groupCount == 0 -> {
            InitialScanContent(
                onStartScan = onStartScan,
                modifier = modifier
            )
        }
        scanState is ScanState.Completed && groupCount == 0 -> {
            NoResultsContent(
                title = "No Duplicates Found",
                description = "Your photo gallery looks clean! We didn't find any duplicate or similar photos.",
                onRescan = onStartScan,
                modifier = modifier
            )
        }
        scanState is ScanState.Error -> {
            ErrorContent(
                message = scanState.message,
                onRetry = onStartScan,
                modifier = modifier
            )
        }
        else -> {
            DuplicateGroupsList(
                groups = duplicateGroups,
                selectedPhotoIds = selectedPhotoIds,
                onPhotoSelect = onPhotoSelect,
                onLongPressPhoto = onLongPressPhoto,
                onLongPressRelease = onLongPressRelease,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun LowQualityContent(
    scanState: ScanState,
    lowQualityPhotos: List<PhotoHash>,
    lowQualityCount: Int,
    selectedUris: Set<String>,
    onToggleSelection: (String) -> Unit,
    onLongPressPhoto: (FullscreenPhotoInfo) -> Unit,
    onLongPressRelease: () -> Unit,
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        scanState is ScanState.Idle && lowQualityCount == 0 -> {
            InitialScanContent(
                onStartScan = onStartScan,
                modifier = modifier
            )
        }
        scanState is ScanState.Completed && lowQualityCount == 0 -> {
            NoResultsContent(
                title = "No Quality Issues Found",
                description = "Your photos look great! We didn't find any blurry, dark, or overexposed photos.",
                onRescan = onStartScan,
                modifier = modifier
            )
        }
        scanState is ScanState.Error -> {
            ErrorContent(
                message = scanState.message,
                onRetry = onStartScan,
                modifier = modifier
            )
        }
        else -> {
            LowQualityPhotoGrid(
                photos = lowQualityPhotos,
                selectedUris = selectedUris,
                onToggleSelection = onToggleSelection,
                onLongPressPhoto = onLongPressPhoto,
                onLongPressRelease = onLongPressRelease,
                modifier = modifier
            )
        }
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
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = AccentPrimary,
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Scan Your Photos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AccentPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Find duplicates and low quality photos to free up storage space.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            AppButton(
                text = "Start Scan",
                onClick = onStartScan
            )
        }
    }
}

@Composable
private fun NoResultsContent(
    title: String,
    description: String,
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
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = AccentPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            DialogButton(
                text = "Scan Again",
                onClick = onRescan,
                intent = DialogButtonIntent.Positive
            )
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

@Composable
private fun DuplicateGroupsList(
    groups: List<DuplicateGroupWithPhotos>,
    selectedPhotoIds: Set<Long>,
    onPhotoSelect: (Long) -> Unit,
    onLongPressPhoto: (FullscreenPhotoInfo) -> Unit,
    onLongPressRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
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

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun LowQualityPhotoGrid(
    photos: List<PhotoHash>,
    selectedUris: Set<String>,
    onToggleSelection: (String) -> Unit,
    onLongPressPhoto: (FullscreenPhotoInfo) -> Unit,
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
                onLongPress = { onLongPressPhoto(FullscreenPhotoInfo(photo.uri, photo.fileSize, photo.dateAdded, photo.bucketName)) },
                onLongPressRelease = onLongPressRelease
            )
        }
    }
}

@Composable
private fun FullscreenPhotoOverlay(
    photoInfo: FullscreenPhotoInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Query display name from ContentResolver
    val displayName = remember(photoInfo.uri) {
        try {
            val uri = Uri.parse(photoInfo.uri)
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) { null }
    } ?: "Unknown"

    // Format file size
    val fileSizeText = remember(photoInfo.fileSize) {
        when {
            photoInfo.fileSize >= 1_000_000 -> String.format("%.1f MB", photoInfo.fileSize / 1_000_000.0)
            photoInfo.fileSize >= 1_000 -> String.format("%.0f KB", photoInfo.fileSize / 1_000.0)
            else -> "${photoInfo.fileSize} B"
        }
    }

    // Format date
    val dateText = remember(photoInfo.dateAdded) {
        if (photoInfo.dateAdded > 0) {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            sdf.format(Date(photoInfo.dateAdded * 1000))
        } else ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        if (event.changes.all { !it.pressed }) {
                            onDismiss()
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(photoInfo.uri)
                .crossfade(true)
                .build(),
            contentDescription = "Fullscreen preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Metadata bar at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = displayName,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(fileSizeText)
                    if (dateText.isNotEmpty()) {
                        append("  •  ")
                        append(dateText)
                    }
                    if (photoInfo.bucketName.isNotEmpty()) {
                        append("  •  ")
                        append(photoInfo.bucketName)
                    }
                },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Request deletion of photos using MediaStore.createDeleteRequest for Android R+.
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
        uris.forEach { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                // Handle deletion error
            }
        }
    }
}
