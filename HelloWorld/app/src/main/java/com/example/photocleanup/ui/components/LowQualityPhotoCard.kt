package com.example.photocleanup.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.photocleanup.data.PhotoHash
import com.example.photocleanup.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LowQualityPhotoCard(
    photo: PhotoHash,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onLongPress: () -> Unit,
    onLongPressRelease: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uri = Uri.parse(photo.uri)
    val coroutineScope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current

    // Get primary issue label
    val issueLabel = photo.qualityIssues.split(",").firstOrNull()?.let { issue ->
        when (issue) {
            "BLURRY" -> "Blurry"
            "MOTION_BLUR" -> "Motion Blur"
            "VERY_DARK" -> "Too Dark"
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
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    var longPressTriggered = false

                    // Start a coroutine to trigger long press after timeout
                    val longPressJob = coroutineScope.launch {
                        delay(viewConfiguration.longPressTimeoutMillis)
                        longPressTriggered = true
                        onLongPress()
                    }

                    // Wait for the finger to lift or cancel
                    val up = waitForUpOrCancellation()

                    // Cancel the long press job if still running
                    longPressJob.cancel()

                    if (longPressTriggered) {
                        // Long press was triggered, now releasing
                        onLongPressRelease()
                    } else if (up != null) {
                        // Short tap - trigger selection
                        onToggleSelection()
                    }
                }
            }
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
