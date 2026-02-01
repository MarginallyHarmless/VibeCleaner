package com.example.photocleanup.ui.components

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.photocleanup.ui.theme.ActionDelete
import com.example.photocleanup.ui.theme.ActionKeep
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
fun SwipeablePhotoCard(
    photo: Uri,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val dismissThreshold = with(density) { 80.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var isDismissed by remember { mutableStateOf(false) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = if (isDismissed) {
            if (offsetX < 0) -with(density) { screenWidth.toPx() } * 1.5f
            else with(density) { screenWidth.toPx() } * 1.5f
        } else {
            offsetX
        },
        animationSpec = if (isDismissed) {
            tween(durationMillis = 150)
        } else {
            snap()
        },
        finishedListener = {
            if (isDismissed) {
                if (offsetX < 0) onSwipeLeft() else onSwipeRight()
                offsetX = 0f
                isDismissed = false
            }
        },
        label = "swipeAnimation"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isDismissed) 0f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "alphaAnimation"
    )

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
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .graphicsLayer {
                    rotationZ = rotation
                }
                .alpha(alpha)
                .pointerInput(photo) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                offsetX < -dismissThreshold -> {
                                    isDismissed = true
                                }
                                offsetX > dismissThreshold -> {
                                    isDismissed = true
                                }
                                else -> {
                                    offsetX = 0f
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!isDismissed) {
                                offsetX += dragAmount
                            }
                        }
                    )
                },
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = photo,
                    contentDescription = "Photo to review",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black),
                    contentScale = ContentScale.Fit
                )

                // Keep overlay (swipe left) - gradient from left
                if (swipeProgress < -0.1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        ActionKeep.copy(alpha = swipeProgress.absoluteValue * 0.7f),
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

                // Delete overlay (swipe right) - gradient from right
                if (swipeProgress > 0.1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        ActionDelete.copy(alpha = swipeProgress.absoluteValue * 0.7f)
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
            }
        }
    }
}
