package com.example.photocleanup.ui.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.photocleanup.data.PhotoHash
import com.example.photocleanup.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LowQualityPhotoCard(
    photo: PhotoHash,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uri = Uri.parse(photo.uri)

    // Get primary issue label
    val issueLabel = photo.qualityIssues.split(",").firstOrNull()?.let { issue ->
        when (issue) {
            "BLURRY" -> "Blurry"
            "VERY_DARK" -> "Black"
            "VERY_BRIGHT" -> "White"
            "UNDEREXPOSED" -> "Too Dark"
            "OVEREXPOSED" -> "Overexposed"
            "NOISY" -> "Noisy"
            "LOW_CONTRAST" -> "Low Contrast"
            else -> issue.lowercase().replaceFirstChar { it.uppercase() }
        }
    } ?: "Low Quality"

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onToggleSelection,
                onLongClick = onLongPress
            )
    ) {
        // Photo thumbnail
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(uri)
                .size(200)
                .crossfade(true)
                .build(),
            contentDescription = "Photo with quality issue: $issueLabel",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Issue label badge at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = issueLabel,
                color = Color.White,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Selection indicator
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Seagrass.copy(alpha = 0.3f))
            )
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Selected",
                tint = Seagrass,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
            )
        }
    }
}
