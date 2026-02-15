package com.stashortrash.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stashortrash.app.ui.theme.DarkSurface
import com.stashortrash.app.ui.theme.DarkSurfaceVariant
import com.stashortrash.app.ui.theme.DustyMauve
import com.stashortrash.app.ui.theme.Seagrass
import com.stashortrash.app.ui.theme.SeagrassMuted
import com.stashortrash.app.ui.theme.TextMuted
import com.stashortrash.app.ui.theme.TextPrimary
import com.stashortrash.app.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.1f GB", gb)
}

fun formatNumber(value: Int): String {
    return NumberFormat.getNumberInstance(Locale.US).format(value)
}

@Composable
fun HeroStatPod(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    showRing: Boolean = false,
    ringProgress: Float = 0f
) {
    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceVariant)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showRing) {
            // Mini circular progress ring for delete rate
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(36.dp)
            ) {
                val ringAnim = remember { Animatable(0f) }
                LaunchedEffect(ringProgress) {
                    ringAnim.animateTo(
                        targetValue = ringProgress,
                        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
                    )
                }
                Canvas(modifier = Modifier.size(36.dp)) {
                    val strokeWidth = 4.dp.toPx()
                    drawArc(
                        color = DarkSurfaceVariant,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth)
                    )
                    drawArc(
                        color = DustyMauve,
                        startAngle = -90f,
                        sweepAngle = 360f * ringAnim.value,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth)
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SeagrassMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SeagrassMuted,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = value,
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Seagrass accent line
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Seagrass.copy(alpha = 0.5f))
        )
    }
}

@Composable
fun StatGridCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = value,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun DetailStatRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun RatioBar(
    deleteRatio: Float,
    modifier: Modifier = Modifier
) {
    val deletePct = (deleteRatio * 100).toInt()
    val keepPct = 100 - deletePct

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .padding(16.dp)
    ) {
        Text(
            text = "Delete vs Keep",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            if (deletePct > 0) {
                Box(
                    modifier = Modifier
                        .weight(deleteRatio.coerceAtLeast(0.01f))
                        .height(12.dp)
                        .background(DustyMauve)
                )
            }
            if (keepPct > 0) {
                Box(
                    modifier = Modifier
                        .weight((1f - deleteRatio).coerceAtLeast(0.01f))
                        .height(12.dp)
                        .background(Seagrass)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(DustyMauve)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$deletePct% deleted",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Seagrass)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$keepPct% kept",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun ReviewedRatioBar(
    reviewed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    if (total <= 0) return

    val ratio = (reviewed.toFloat() / total).coerceIn(0f, 1f)
    val reviewedPct = (ratio * 100).toInt()
    val unreviewedPct = 100 - reviewedPct

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .padding(16.dp)
    ) {
        Text(
            text = "Reviewed vs Total",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            if (reviewedPct > 0) {
                Box(
                    modifier = Modifier
                        .weight(ratio.coerceAtLeast(0.01f))
                        .height(12.dp)
                        .background(Seagrass)
                )
            }
            if (unreviewedPct > 0) {
                Box(
                    modifier = Modifier
                        .weight((1f - ratio).coerceAtLeast(0.01f))
                        .height(12.dp)
                        .background(TextMuted)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Seagrass)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$reviewedPct% reviewed",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(TextMuted)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$unreviewedPct% unreviewed",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
