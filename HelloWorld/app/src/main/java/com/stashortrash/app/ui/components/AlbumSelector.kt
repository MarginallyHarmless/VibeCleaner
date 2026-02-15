package com.stashortrash.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stashortrash.app.data.FolderInfo
import com.stashortrash.app.ui.theme.AccentPrimary
import com.stashortrash.app.ui.theme.AccentPrimaryDim
import com.stashortrash.app.ui.theme.DarkSurfaceVariant
import com.stashortrash.app.ui.theme.TextMuted
import com.stashortrash.app.ui.theme.TextPrimary

@Composable
fun AlbumSelector(
    albums: List<FolderInfo>,
    currentAlbumId: Long?,
    onAlbumSelected: (String) -> Unit,
    isMoving: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val filteredAlbums = albums.filter { !it.displayName.startsWith(".") }

    if (filteredAlbums.isEmpty()) {
        // Show empty state
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "No albums found",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
        return
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(
            items = filteredAlbums,
            key = { it.bucketId }
        ) { album ->
            val isCurrentAlbum = album.bucketId == currentAlbumId

            AlbumChip(
                album = album,
                isSelected = isCurrentAlbum,
                enabled = !isMoving && !isCurrentAlbum,
                onClick = { onAlbumSelected(album.displayName) }
            )
        }

        // Show loading indicator while moving
        if (isMoving) {
            item {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(start = 8.dp),
                    color = AccentPrimary,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumChip(
    album: FolderInfo,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        isSelected -> AccentPrimaryDim.copy(alpha = 0.3f)
        !enabled -> DarkSurfaceVariant.copy(alpha = 0.5f)
        else -> DarkSurfaceVariant
    }
    val labelColor = when {
        isSelected -> AccentPrimary
        !enabled -> TextMuted
        else -> TextPrimary
    }
    val iconTint = if (isSelected) AccentPrimary else TextMuted

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),  // Increased height for better touch targets
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = album.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = labelColor
                )
                Text(
                    text = album.photoCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) AccentPrimary else TextMuted
                )
            }
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = iconTint
            )
        },
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            selectedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            labelColor = labelColor,
            selectedLabelColor = labelColor,
            disabledLabelColor = labelColor,
            iconColor = iconTint,
            selectedLeadingIconColor = iconTint,
            disabledLeadingIconColor = iconTint
        ),
        border = null
    )
}
