package com.example.photocleanup.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photocleanup.ui.theme.DarkSurface
import com.example.photocleanup.ui.theme.DarkSurfaceVariant
import com.example.photocleanup.ui.theme.DustyMauve
import com.example.photocleanup.ui.theme.DustyMauveMuted
import com.example.photocleanup.ui.theme.Seagrass
import com.example.photocleanup.ui.theme.SeagrassMuted
import com.example.photocleanup.ui.theme.TextMuted
import com.example.photocleanup.ui.theme.TextPrimary
import com.example.photocleanup.ui.theme.TextSecondary
import com.example.photocleanup.ui.theme.HoneyBronze
import com.example.photocleanup.viewmodel.DailyProgress
import com.example.photocleanup.viewmodel.MonthlyStats
import java.util.Locale

@Composable
fun ReviewProgressChart(
    data: List<DailyProgress>,
    modifier: Modifier = Modifier
) {
    if (data.size < 2) return

    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .padding(16.dp)
    ) {
        Text(
            text = "Review Progress",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Legend
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(TextSecondary)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Total Photos", color = TextSecondary, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Seagrass)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Reviewed", color = TextSecondary, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val leftPad = 48f
            val bottomPad = 24f
            val topPad = 12f
            val chartWidth = size.width - leftPad
            val chartHeight = size.height - bottomPad - topPad
            val maxCount = data.maxOf { maxOf(it.totalPhotos, it.totalReviewed) }.coerceAtLeast(1)
            val divisor = data.size - 1

            // Y-axis gridlines
            val ySteps = 4
            for (i in 0..ySteps) {
                val y = topPad + chartHeight * (1f - i.toFloat() / ySteps)
                val value = maxCount * i / ySteps
                drawLine(
                    color = DarkSurfaceVariant,
                    start = Offset(leftPad, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = formatNumber(value),
                    topLeft = Offset(0f, y - 6f),
                    style = TextStyle(color = TextMuted, fontSize = 9.sp)
                )
            }

            // Compute point positions
            fun pointsFor(values: List<Int>): List<Offset> {
                return values.mapIndexed { index, v ->
                    val x = leftPad + chartWidth * index / divisor
                    val y = topPad + chartHeight * (1f - v.toFloat() / maxCount)
                    Offset(x, y)
                }
            }

            val totalPoints = pointsFor(data.map { it.totalPhotos })
            val reviewedPoints = pointsFor(data.map { it.totalReviewed })

            // Fill between the two lines (gap area)
            val gapPath = Path().apply {
                // Top edge: total photos line (left to right)
                moveTo(totalPoints.first().x, totalPoints.first().y)
                for (p in totalPoints) lineTo(p.x, p.y)
                // Bottom edge: reviewed line (right to left)
                for (p in reviewedPoints.reversed()) lineTo(p.x, p.y)
                close()
            }
            drawPath(
                path = gapPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DustyMauve.copy(alpha = 0.10f),
                        DustyMauve.copy(alpha = 0.03f)
                    ),
                    startY = topPad,
                    endY = topPad + chartHeight
                )
            )

            // Fill beneath reviewed line
            val reviewedFill = Path().apply {
                moveTo(reviewedPoints.first().x, topPad + chartHeight)
                for (p in reviewedPoints) lineTo(p.x, p.y)
                lineTo(reviewedPoints.last().x, topPad + chartHeight)
                close()
            }
            drawPath(
                path = reviewedFill,
                brush = Brush.verticalGradient(
                    colors = listOf(Seagrass.copy(alpha = 0.25f), Seagrass.copy(alpha = 0.02f)),
                    startY = topPad,
                    endY = topPad + chartHeight
                )
            )

            // Total photos line
            val totalPath = Path().apply {
                moveTo(totalPoints.first().x, totalPoints.first().y)
                for (i in 1 until totalPoints.size) lineTo(totalPoints[i].x, totalPoints[i].y)
            }
            drawPath(
                path = totalPath,
                color = TextSecondary.copy(alpha = 0.6f),
                style = Stroke(width = 2f, cap = StrokeCap.Round)
            )

            // Reviewed line
            val reviewedPath = Path().apply {
                moveTo(reviewedPoints.first().x, reviewedPoints.first().y)
                for (i in 1 until reviewedPoints.size) lineTo(reviewedPoints[i].x, reviewedPoints[i].y)
            }
            drawPath(
                path = reviewedPath,
                color = Seagrass,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )

            // Endpoint dots
            val lastTotal = totalPoints.last()
            drawCircle(color = TextSecondary, radius = 4f, center = lastTotal)

            val lastReviewed = reviewedPoints.last()
            drawCircle(color = Seagrass, radius = 6f, center = lastReviewed)
            drawCircle(color = DarkSurface, radius = 3f, center = lastReviewed)

            // X-axis date labels (show a few evenly spaced)
            val labelCount = minOf(data.size, 5)
            val monthNames = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            for (li in 0 until labelCount) {
                val idx = if (labelCount <= 1) 0 else li * (data.size - 1) / (labelCount - 1)
                val x = leftPad + chartWidth * idx / divisor
                val day = data[idx].day // "yyyy-MM-dd"
                val monthNum = day.substring(5, 7).toIntOrNull()?.minus(1)?.coerceIn(0, 11) ?: 0
                val dayNum = day.substring(8).trimStart('0')
                drawText(
                    textMeasurer = textMeasurer,
                    text = "${monthNames[monthNum]} $dayNum",
                    topLeft = Offset(x - 14f, size.height - 14f),
                    style = TextStyle(color = TextMuted, fontSize = 8.sp)
                )
            }
        }
    }
}

@Composable
fun MonthlyActivityBarChart(
    data: List<MonthlyStats>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .padding(16.dp)
    ) {
        Text(
            text = "Monthly Activity",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Legend
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Seagrass)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Kept", color = TextSecondary, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(DustyMauve)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Deleted", color = TextSecondary, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val leftPad = 36f
            val bottomPad = 24f
            val topPad = 8f
            val chartWidth = size.width - leftPad
            val chartHeight = size.height - bottomPad - topPad

            val maxTotal = data.maxOf { it.kept + it.deleted }.coerceAtLeast(1)
            val barCount = data.size
            val barSpacing = chartWidth / barCount
            val barWidth = (barSpacing * 0.6f).coerceAtMost(40f)

            // Y-axis gridlines
            val ySteps = 4
            for (i in 0..ySteps) {
                val y = topPad + chartHeight * (1f - i.toFloat() / ySteps)
                val value = maxTotal * i / ySteps
                drawLine(
                    color = DarkSurfaceVariant,
                    start = Offset(leftPad, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = value.toString(),
                    topLeft = Offset(0f, y - 6f),
                    style = TextStyle(color = TextMuted, fontSize = 9.sp)
                )
            }

            // Bars
            data.forEachIndexed { index, item ->
                val x = leftPad + barSpacing * index + (barSpacing - barWidth) / 2
                val total = item.kept + item.deleted
                val totalHeight = chartHeight * total.toFloat() / maxTotal
                val deletedHeight = chartHeight * item.deleted.toFloat() / maxTotal
                val keptHeight = totalHeight - deletedHeight

                val barBottom = topPad + chartHeight

                // Deleted portion (bottom)
                if (item.deleted > 0) {
                    drawRect(
                        color = DustyMauve,
                        topLeft = Offset(x, barBottom - deletedHeight),
                        size = Size(barWidth, deletedHeight)
                    )
                }

                // Kept portion (top)
                if (item.kept > 0) {
                    drawRect(
                        color = Seagrass,
                        topLeft = Offset(x, barBottom - totalHeight),
                        size = Size(barWidth, keptHeight)
                    )
                }

                // Month label
                val shortMonth = item.yearMonth.substring(5)
                val monthNames = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
                val monthIdx = shortMonth.toIntOrNull()?.minus(1)?.coerceIn(0, 11) ?: 0
                drawText(
                    textMeasurer = textMeasurer,
                    text = monthNames[monthIdx],
                    topLeft = Offset(x, size.height - 14f),
                    style = TextStyle(color = TextMuted, fontSize = 9.sp)
                )
            }
        }
    }
}

@Composable
fun QualityBreakdownChart(
    data: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val total = data.values.sum()
    if (total == 0) return

    // Ordered categories with colors
    val categories = listOf(
        "Sharp" to Seagrass,
        "Slightly Soft" to SeagrassMuted,
        "Blurry" to DustyMauve,
        "Too Dark" to DustyMauveMuted,
        "Screenshot" to Color(0xFF7A6E65) // Warm gray
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurface)
            .padding(16.dp)
    ) {
        Text(
            text = "Photo Quality Breakdown",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        for ((label, color) in categories) {
            val count = data[label] ?: 0
            if (count == 0) continue
            val pct = count * 100f / total

            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.width(100.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(DarkSurfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = (pct / 100f).coerceIn(0.02f, 1f))
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(color)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = String.format(Locale.US, "%.0f%%", pct),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.width(36.dp)
                    )
                }
            }
        }
    }
}
