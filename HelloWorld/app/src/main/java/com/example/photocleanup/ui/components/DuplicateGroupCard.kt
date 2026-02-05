package com.example.photocleanup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.example.photocleanup.data.DuplicateGroupWithPhotos
import com.example.photocleanup.data.DuplicatePhotoInfo
import com.example.photocleanup.ui.theme.AccentPrimary
import com.example.photocleanup.ui.theme.DarkSurface
import com.example.photocleanup.ui.theme.TextSecondary

/**
 * Card component that displays a group of duplicate photos.
 * Shows all photos in a grid layout (4 per row).
 * Users tap photos to select them for deletion.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuplicateGroupCard(
    group: DuplicateGroupWithPhotos,
    selectedPhotoIds: Set<Long>,
    onPhotoSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedInGroup = group.photos.count { it.id in selectedPhotoIds }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
    ) {
        // Header row
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "${group.photoCount} photos",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            if (selectedInGroup > 0) {
                Text(
                    text = "$selectedInGroup selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentPrimary
                )
            }
        }

        // Photo grid (4 per row)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 4
        ) {
            group.photos.forEach { photo ->
                DuplicatePhotoThumbnail(
                    photo = photo,
                    isSelected = photo.id in selectedPhotoIds,
                    onSelect = { onPhotoSelect(photo.id) },
                    modifier = Modifier.weight(1f)
                )
            }
            // Add empty spacers if needed to fill incomplete row
            val remainder = group.photos.size % 4
            if (remainder > 0) {
                repeat(4 - remainder) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DuplicatePhotoThumbnail(
    photo: DuplicatePhotoInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = AccentPrimary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onSelect)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.uri)
                .size(Size(256, 256))  // Load thumbnail size, not full resolution
                .crossfade(false)      // Disable animation for smoother scrolling
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = "Duplicate photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Selection overlay and checkmark (matches ToDeleteScreen style)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(AccentPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected for deletion",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
