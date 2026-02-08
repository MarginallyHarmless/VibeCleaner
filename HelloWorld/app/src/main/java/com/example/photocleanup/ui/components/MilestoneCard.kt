package com.example.photocleanup.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photocleanup.ui.theme.DarkSurface
import com.example.photocleanup.ui.theme.DarkSurfaceHigh
import com.example.photocleanup.ui.theme.DarkSurfaceVariant
import com.example.photocleanup.ui.theme.HoneyBronze
import com.example.photocleanup.ui.theme.Seagrass
import com.example.photocleanup.ui.theme.TextMuted
import com.example.photocleanup.ui.theme.TextPrimary
import com.example.photocleanup.ui.theme.TextSecondary
import com.example.photocleanup.viewmodel.Milestone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MilestoneItem(
    milestone: Milestone,
    modifier: Modifier = Modifier
) {
    val isUnlocked = milestone.unlockedAt != null
    val progress = if (milestone.targetValue > 0) {
        (milestone.currentValue.toFloat() / milestone.targetValue).coerceIn(0f, 1f)
    } else 0f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon circle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isUnlocked) Seagrass.copy(alpha = 0.2f) else DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = milestone.icon,
                contentDescription = null,
                tint = if (isUnlocked) Seagrass else TextMuted,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = milestone.name,
                color = if (isUnlocked) TextPrimary else TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = milestone.description,
                color = TextMuted,
                fontSize = 12.sp
            )

            if (!isUnlocked) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    @Suppress("DEPRECATION")
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = HoneyBronze,
                        trackColor = DarkSurfaceHigh,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${formatProgressValue(milestone.currentValue, milestone.category)} / ${formatProgressValue(milestone.targetValue, milestone.category)}",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(2.dp))
                val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(milestone.unlockedAt!!))
                Text(
                    text = "Unlocked $dateStr",
                    color = Seagrass.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun formatProgressValue(value: Long, category: String): String {
    return when (category) {
        "Space" -> formatBytes(value)
        else -> formatNumber(value.toInt())
    }
}

@Composable
fun MilestoneSection(
    milestones: List<Milestone>,
    modifier: Modifier = Modifier
) {
    val grouped = milestones.groupBy { it.category }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Milestones",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        for ((category, items) in grouped) {
            Text(
                text = category,
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            for (milestone in items) {
                MilestoneItem(
                    milestone = milestone,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
