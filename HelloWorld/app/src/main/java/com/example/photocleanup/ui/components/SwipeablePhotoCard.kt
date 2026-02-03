package com.example.photocleanup.ui.components

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.launch
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
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val dismissThreshold = with(density) { 25.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(0f) }
    // Dismiss state: 0 = not dismissed, -1 = delete (left), 1 = keep (right)
    var dismissState by remember { mutableIntStateOf(0) }
    var swipeDirection by remember { mutableIntStateOf(0) }
    val minOffsetForDirectionalDismiss = with(density) { 15.dp.toPx() }

    // Use Animatable for explicit control over animations
    val animatedOffsetX = remember { Animatable(0f) }
    val animatedOffsetY = remember { Animatable(0f) }
    val animatedScale = remember { Animatable(1f) }
    val animatedAlpha = remember { Animatable(1f) }

    // Calculate targets
    val deleteTargetX = with(density) { -(screenWidth / 2 - 24.dp).toPx() }
    val deleteTargetY = with(density) { -(screenHeight / 2 - 60.dp).toPx() }
    val keepTargetX = with(density) { screenWidth.toPx() * 1.5f }

    // Trigger animations when dismiss state changes
    LaunchedEffect(dismissState) {
        if (dismissState == -1) {
            // Delete animation with momentum effect
            // Phase 1: Momentum push - continue moving left (30ms)
            val pushTargetX = animatedOffsetX.value - 80f
            launch { animatedOffsetX.animateTo(pushTargetX, tween(30)) }
            kotlinx.coroutines.delay(30)

            // Phase 2: Fly to badge and shrink (100ms)
            launch { animatedOffsetX.animateTo(deleteTargetX, tween(100, easing = FastOutLinearInEasing)) }
            launch { animatedOffsetY.animateTo(deleteTargetY, tween(100, easing = FastOutLinearInEasing)) }
            launch { animatedScale.animateTo(0.1f, tween(100, easing = FastOutLinearInEasing)) }

            // Wait for fly-to-badge animation to complete
            kotlinx.coroutines.delay(100)

            // Trigger callback - DO NOT reset state after this
            // The card will be destroyed by Compose due to key(photo) change in MainScreen
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
                .offset { IntOffset(animatedOffsetX.value.roundToInt(), animatedOffsetY.value.roundToInt()) }
                .graphicsLayer {
                    rotationZ = rotation
                    scaleX = animatedScale.value
                    scaleY = animatedScale.value
                }
                .alpha(animatedAlpha.value)
                .pointerInput(photo) {
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
                AsyncImage(
                    model = photo,
                    contentDescription = "Photo to review",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black),
                    contentScale = ContentScale.Fit
                )

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
