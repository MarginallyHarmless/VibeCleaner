package com.stashortrash.app.ui.components

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stashortrash.app.data.MediaItem
import com.stashortrash.app.ui.theme.ActionDelete
import com.stashortrash.app.ui.theme.ActionKeep
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun SwipeablePhotoCard(
    mediaItem: MediaItem,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    entryDirection: Int = 0, // -1 = from left (undo delete), 1 = from right (undo keep)
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val dismissThreshold = with(density) { 25.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(0f) }
    // Dismiss state: 0 = not dismissed, -1 = delete (left), 1 = keep (right)
    var dismissState by remember { mutableIntStateOf(0) }
    var swipeDirection by remember { mutableIntStateOf(0) }
    val minOffsetForDirectionalDismiss = with(density) { 15.dp.toPx() }

    // Calculate targets
    val deleteTargetX = with(density) { -(screenWidth / 2 - 24.dp).toPx() }
    val deleteTargetY = with(density) { -(screenHeight / 2 - 60.dp).toPx() }
    val keepTargetX = with(density) { screenWidth.toPx() * 1.5f }

    // Use Animatable for explicit control over animations
    val animatedOffsetX = remember { Animatable(0f) }
    val animatedOffsetY = remember { Animatable(0f) }
    val animatedScale = remember { Animatable(1f) }
    val animatedAlpha = remember { Animatable(1f) }

    // Separate animatable for undo entry — avoids conflict with drag sync
    val entryStartX = when (entryDirection) {
        -1 -> with(density) { -screenWidth.toPx() * 1.5f }
        1 -> keepTargetX
        else -> 0f
    }
    val entryOffset = remember { Animatable(entryStartX) }

    LaunchedEffect(Unit) {
        if (entryDirection != 0) {
            entryOffset.animateTo(0f, tween(250))
        }
    }

    // Video playback state
    var isPlaying by remember { mutableStateOf(false) }

    // Trigger animations when dismiss state changes
    LaunchedEffect(dismissState) {
        if (dismissState == -1) {
            // Delete animation with momentum effect
            // Phase 1: Momentum push - continue moving left (30ms)
            val pushTargetX = animatedOffsetX.value - 80f
            launch { animatedOffsetX.animateTo(pushTargetX, tween(30)) }
            kotlinx.coroutines.delay(30)

            // Phase 2: Fly to badge, shrink, and fade out (100ms)
            launch { animatedOffsetX.animateTo(deleteTargetX, tween(100, easing = FastOutLinearInEasing)) }
            launch { animatedOffsetY.animateTo(deleteTargetY, tween(100, easing = FastOutLinearInEasing)) }
            launch { animatedScale.animateTo(0.1f, tween(100, easing = FastOutLinearInEasing)) }
            launch { animatedAlpha.animateTo(0f, tween(100, easing = FastOutLinearInEasing)) }

            // Wait for animation to complete
            kotlinx.coroutines.delay(100)

            // Trigger callback — card is invisible, safe to destroy
            onSwipeLeft()
        } else if (dismissState == 1) {
            // Keep animation - fly off right, fade out
            launch { animatedOffsetX.animateTo(keepTargetX, tween(100)) }
            launch { animatedAlpha.animateTo(0f, tween(100)) }

            kotlinx.coroutines.delay(100)

            // Trigger callback - DO NOT reset state after this
            // The card will be destroyed by Compose due to key(photo) change in MainScreen
            onSwipeRight()
        }
    }

    // During drag, update animated offset to follow finger
    LaunchedEffect(offsetX, dismissState) {
        if (dismissState == 0) {
            animatedOffsetX.snapTo(offsetX)
        }
    }

    val swipeProgress = (offsetX / dismissThreshold).coerceIn(-1f, 1f)
    val rotation = swipeProgress * 8f

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset((animatedOffsetX.value + entryOffset.value).roundToInt(), animatedOffsetY.value.roundToInt()) }
                .graphicsLayer {
                    rotationZ = rotation
                    scaleX = animatedScale.value
                    scaleY = animatedScale.value
                }
                .alpha(animatedAlpha.value)
                .pointerInput(mediaItem.uri) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dismissState == 0) {
                                val isSwipingLeft = swipeDirection == -1 && offsetX < -minOffsetForDirectionalDismiss
                                val isSwipingRight = swipeDirection == 1 && offsetX > minOffsetForDirectionalDismiss

                                when {
                                    offsetX < -dismissThreshold || isSwipingLeft -> {
                                        dismissState = -1  // Trigger delete animation
                                    }
                                    offsetX > dismissThreshold || isSwipingRight -> {
                                        dismissState = 1  // Trigger keep animation
                                    }
                                    else -> {
                                        offsetX = 0f
                                    }
                                }
                            }
                            swipeDirection = 0
                        },
                        onDragCancel = {
                            if (dismissState == 0) {
                                val isSwipingLeft = swipeDirection == -1 && offsetX < -minOffsetForDirectionalDismiss
                                val isSwipingRight = swipeDirection == 1 && offsetX > minOffsetForDirectionalDismiss

                                when {
                                    offsetX < -dismissThreshold || isSwipingLeft -> {
                                        dismissState = -1
                                    }
                                    offsetX > dismissThreshold || isSwipingRight -> {
                                        dismissState = 1
                                    }
                                    else -> {
                                        offsetX = 0f
                                    }
                                }
                            }
                            swipeDirection = 0
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (dismissState == 0) {
                                swipeDirection = if (dragAmount < -1f) -1 else if (dragAmount > 1f) 1 else swipeDirection
                                offsetX += dragAmount
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (mediaItem.isVideo && isPlaying) {
                    // Video player (ExoPlayer)
                    VideoPlayer(
                        uri = mediaItem.uri,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black)
                    )
                } else {
                    // Photo or video thumbnail (Coil handles both via VideoFrameDecoder)
                    AsyncImage(
                        model = mediaItem.uri,
                        contentDescription = if (mediaItem.isVideo) "Video to review" else "Photo to review",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black),
                        contentScale = ContentScale.Fit
                    )

                    // Video overlay: play button + duration badge
                    if (mediaItem.isVideo) {
                        // Play button (centered)
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(72.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .clickable { isPlaying = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play video",
                                modifier = Modifier.size(48.dp),
                                tint = Color.White
                            )
                        }

                        // Duration badge (bottom-right)
                        if (mediaItem.durationMs > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.7f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = formatDuration(mediaItem.durationMs),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Delete overlay (swipe left) - gradient from left
                if (swipeProgress < -0.1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        ActionDelete.copy(alpha = swipeProgress.absoluteValue * 0.7f),
                                        Color.Transparent
                                    ),
                                    startX = 0f,
                                    endX = Float.POSITIVE_INFINITY
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 32.dp)
                            .alpha(swipeProgress.absoluteValue),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Delete",
                            modifier = Modifier.size(64.dp),
                            tint = Color.White
                        )
                        Text(
                            text = "DELETE",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }

                // Keep overlay (swipe right) - gradient from right
                if (swipeProgress > 0.1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        ActionKeep.copy(alpha = swipeProgress.absoluteValue * 0.7f)
                                    ),
                                    startX = 0f,
                                    endX = Float.POSITIVE_INFINITY
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 32.dp)
                            .alpha(swipeProgress.absoluteValue),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Keep",
                            modifier = Modifier.size(64.dp),
                            tint = Color.White
                        )
                        Text(
                            text = "KEEP",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }
    }
}
