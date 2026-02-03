package com.example.photocleanup.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.photocleanup.data.DuplicateGroupWithPhotos
import com.example.photocleanup.data.DuplicatePhotoInfo
import com.example.photocleanup.ui.theme.AccentPrimary
import com.example.photocleanup.ui.theme.ActionDelete
import com.example.photocleanup.ui.theme.ActionKeep
import com.example.photocleanup.ui.theme.DarkSurface
import com.example.photocleanup.ui.theme.DarkSurfaceVariant
import com.example.photocleanup.ui.theme.TextSecondary

/**
 * Card component that displays a group of duplicate photos.
 * Shows a preview row of thumbnails and can be expanded to show all photos.
 * Users can mark which photo to keep and delete the rest.
 */
@Composable
fun DuplicateGroupCard(
    group: DuplicateGroupWithPhotos,
    selectedPhotoIds: Set<Long>,
    onPhotoSelect: (Long) -> Unit,
    onMarkAsKept: (String, Long) -> Unit,
    onDeleteUnkept: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .animateContentSize()
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${group.photoCount} similar photos",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (group.keptPhotoUri != null) "1 marked to keep" else "Tap a photo to keep it",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = TextSecondary
            )
        }

        // Photo thumbnails row (always visible)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = group.photos.take(if (isExpanded) group.photos.size else 4),
                key = { it.id }
            ) { photo ->
                DuplicatePhotoThumbnail(
                    photo = photo,
                    isSelected = photo.id in selectedPhotoIds,
                    onSelect = { onPhotoSelect(photo.id) },
                    onMarkAsKept = { onMarkAsKept(group.groupId, photo.id) },
                    isExpanded = isExpanded
                )
            }

            // Show more indicator when collapsed
            if (!isExpanded && group.photos.size > 4) {
                item {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .clickable { isExpanded = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${group.photos.size - 4}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Expanded content with details and actions
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Photo details
                group.photos.forEach { photo ->
                    PhotoDetailRow(
                        photo = photo,
                        isSelected = photo.id in selectedPhotoIds,
                        onSelect = { onPhotoSelect(photo.id) },
                        onMarkAsKept = { onMarkAsKept(group.groupId, photo.id) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    val unkeptCount = group.photos.count { !it.isKept }
                    if (group.keptPhotoUri != null && unkeptCount > 0) {
                        TextButton(
                            onClick = { onDeleteUnkept(group.groupId) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                tint = ActionDelete,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Delete $unkeptCount duplicates",
                                color = ActionDelete
                            )
                        }
                    }
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
    onMarkAsKept: () -> Unit,
    isExpanded: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (photo.isKept) 3.dp else if (isSelected) 2.dp else 0.dp,
                color = when {
                    photo.isKept -> ActionKeep
                    isSelected -> AccentPrimary
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { if (isExpanded) onSelect() else onMarkAsKept() }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.uri)
                .crossfade(true)
                .build(),
            contentDescription = "Duplicate photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Keep badge
        if (photo.isKept) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(ActionKeep),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Kept",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Selection indicator
        if (isSelected && !photo.isKept) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(AccentPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun PhotoDetailRow(
    photo: DuplicatePhotoInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onMarkAsKept: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Photo info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (photo.width > 0 && photo.height > 0) {
                    "${photo.width} x ${photo.height}"
                } else {
                    "Unknown size"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Text(
                text = buildString {
                    if (photo.fileSize > 0) {
                        append(formatFileSize(photo.fileSize))
                    }
                    if (photo.bucketName.isNotEmpty()) {
                        if (isNotEmpty()) append(" \u2022 ")
                        append(photo.bucketName)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        // Keep button
        if (photo.isKept) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ActionKeep.copy(alpha = 0.2f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Keeping",
                    style = MaterialTheme.typography.labelMedium,
                    color = ActionKeep,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            TextButton(onClick = onMarkAsKept) {
                Text(
                    text = "Keep",
                    color = AccentPrimary
                )
            }
        }
    }
}

/**
 * Format file size into human-readable string.
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
