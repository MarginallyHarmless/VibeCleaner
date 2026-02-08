package com.example.photocleanup.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.photocleanup.ui.components.DetailStatRow
import com.example.photocleanup.ui.components.HeroStatPod
import com.example.photocleanup.ui.components.PremiumOverlay
import com.example.photocleanup.ui.components.PremiumUpsellSheet
import com.example.photocleanup.ui.components.MilestoneSection
import com.example.photocleanup.ui.components.QualityBreakdownChart
import com.example.photocleanup.ui.components.RatioBar
import com.example.photocleanup.ui.components.ReviewedRatioBar
import com.example.photocleanup.ui.components.ReviewProgressChart
import com.example.photocleanup.ui.components.StatGridCard
import com.example.photocleanup.ui.components.formatBytes
import com.example.photocleanup.ui.components.formatNumber
import com.example.photocleanup.ui.theme.AccentPrimary
import com.example.photocleanup.ui.theme.DarkSurfaceVariant
import com.example.photocleanup.viewmodel.DailyProgress
import com.example.photocleanup.viewmodel.Milestone
import com.example.photocleanup.viewmodel.StatsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showUpsellSheet by remember { mutableStateOf(false) }

    if (uiState.isLoading) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = AccentPrimary)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
    ) {
        // Title
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Stats",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        // Hero Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroStatPod(
                    icon = Icons.Rounded.Photo,
                    value = formatNumber(uiState.totalReviewed),
                    label = "Reviewed",
                    modifier = Modifier.weight(1f)
                )
                HeroStatPod(
                    icon = Icons.Rounded.SaveAlt,
                    value = formatBytes(uiState.spaceRecoveredBytes),
                    label = "Recovered",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Reviewed vs Total ratio bar
        if (uiState.totalPhotosOnDevice > 0) {
            item {
                ReviewedRatioBar(
                    reviewed = uiState.totalReviewed,
                    total = uiState.totalPhotosOnDevice
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Activity Grid (2x2)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatGridCard(
                        icon = Icons.Rounded.Delete,
                        value = formatNumber(uiState.totalDeleted),
                        label = "Deleted",
                        modifier = Modifier.weight(1f)
                    )
                    StatGridCard(
                        icon = Icons.Rounded.Star,
                        value = formatNumber(uiState.totalKept),
                        label = "Kept",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatGridCard(
                        icon = Icons.Rounded.Speed,
                        value = "~${uiState.avgPerSession}",
                        label = "Avg per Session",
                        modifier = Modifier.weight(1f)
                    )
                    StatGridCard(
                        icon = Icons.Rounded.OfflineBolt,
                        value = if (uiState.busiestDayCount > 0) formatNumber(uiState.busiestDayCount) else "-",
                        label = if (uiState.busiestDay.isNotEmpty()) "Best Day" else "Busiest Day",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Ratio Bar
        if (uiState.totalReviewed > 0) {
            item {
                RatioBar(deleteRatio = uiState.deleteRatio)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Premium-locked section: Review Progress through Milestones
        if (viewModel.isPremium) {
            // Review progress chart
            if (uiState.progressByDay.size >= 2) {
                item {
                    ReviewProgressChart(data = uiState.progressByDay)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Quality breakdown chart
            if (uiState.qualityDistribution.values.sum() > 0) {
                item {
                    QualityBreakdownChart(data = uiState.qualityDistribution)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Detail card: Duplicates & Quality
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurfaceVariant)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Duplicates & Quality",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    DetailStatRow(
                        icon = Icons.Rounded.ContentCopy,
                        label = "Duplicate groups found",
                        value = formatNumber(uiState.duplicateGroupsFound)
                    )
                    DetailStatRow(
                        icon = Icons.Rounded.FolderOpen,
                        label = "Extra copies found",
                        value = formatNumber(uiState.duplicatesRemoved)
                    )
                    DetailStatRow(
                        icon = Icons.Rounded.Warning,
                        label = "Low-quality flagged",
                        value = formatNumber(uiState.lowQualityFlagged)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Detail card: Records
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurfaceVariant)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Records",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    DetailStatRow(
                        icon = Icons.Rounded.PhotoSizeSelectLarge,
                        label = "Largest file deleted",
                        value = if (uiState.largestFileDeletedSize > 0) formatBytes(uiState.largestFileDeletedSize) else "-"
                    )
                    DetailStatRow(
                        icon = Icons.Rounded.LocalFireDepartment,
                        label = "Review streak",
                        value = "${uiState.longestStreak} day${if (uiState.longestStreak != 1) "s" else ""}"
                    )
                    DetailStatRow(
                        icon = Icons.Rounded.CalendarToday,
                        label = "Using since",
                        value = if (uiState.usingSince > 0) {
                            SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(uiState.usingSince))
                        } else "-"
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Milestones
            if (uiState.milestones.isNotEmpty()) {
                item {
                    MilestoneSection(milestones = uiState.milestones)
                    Spacer(modifier = Modifier.height(80.dp)) // nav bar clearance
                }
            }
        } else {
            // Locked: show sample data so users see what premium stats look like
            item {
                PremiumOverlay(
                    isLocked = true,
                    onLockedClick = { showUpsellSheet = true }
                ) {
                    ReviewProgressChart(data = sampleProgressData)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                PremiumOverlay(
                    isLocked = true,
                    onLockedClick = { showUpsellSheet = true }
                ) {
                    QualityBreakdownChart(data = sampleQualityData)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                PremiumOverlay(
                    isLocked = true,
                    onLockedClick = { showUpsellSheet = true }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurfaceVariant)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Duplicates & Quality",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        DetailStatRow(
                            icon = Icons.Rounded.ContentCopy,
                            label = "Duplicate groups found",
                            value = "24"
                        )
                        DetailStatRow(
                            icon = Icons.Rounded.FolderOpen,
                            label = "Extra copies found",
                            value = "87"
                        )
                        DetailStatRow(
                            icon = Icons.Rounded.Warning,
                            label = "Low-quality flagged",
                            value = "31"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                PremiumOverlay(
                    isLocked = true,
                    onLockedClick = { showUpsellSheet = true }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkSurfaceVariant)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Records",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        DetailStatRow(
                            icon = Icons.Rounded.PhotoSizeSelectLarge,
                            label = "Largest file deleted",
                            value = "14.2 MB"
                        )
                        DetailStatRow(
                            icon = Icons.Rounded.LocalFireDepartment,
                            label = "Review streak",
                            value = "7 days"
                        )
                        DetailStatRow(
                            icon = Icons.Rounded.CalendarToday,
                            label = "Using since",
                            value = "Jan 15, 2026"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                PremiumOverlay(
                    isLocked = true,
                    onLockedClick = { showUpsellSheet = true }
                ) {
                    MilestoneSection(milestones = sampleMilestones)
                }
                Spacer(modifier = Modifier.height(80.dp)) // nav bar clearance
            }
        }
    }

    if (showUpsellSheet) {
        PremiumUpsellSheet(
            onDismiss = { showUpsellSheet = false },
            onUnlockClick = { showUpsellSheet = false },
            onRestoreClick = { showUpsellSheet = false }
        )
    }
}

// Sample data for locked premium preview
private val sampleProgressData = listOf(
    DailyProgress("2026-01-01", 2480, 0),
    DailyProgress("2026-01-08", 2520, 120),
    DailyProgress("2026-01-15", 2560, 310),
    DailyProgress("2026-01-22", 2610, 580),
    DailyProgress("2026-01-29", 2640, 890),
    DailyProgress("2026-02-05", 2700, 1240)
)

private val sampleQualityData = mapOf(
    "Sharp" to 1842,
    "Slightly Soft" to 326,
    "Blurry" to 89,
    "Too Dark" to 47,
    "Screenshot" to 156
)

private val sampleMilestones = listOf(
    Milestone("First Swipe", "Review your first photo", Icons.Rounded.TouchApp, 1, 1, 1706400000000L, "Photos"),
    Milestone("Getting Started", "Review 100 photos", Icons.Rounded.Photo, 100, 100, 1706900000000L, "Photos"),
    Milestone("Photo Pro", "Review 500 photos", Icons.Rounded.Star, 500, 500, 1707800000000L, "Photos"),
    Milestone("Space Saver", "Recover 100 MB", Icons.Rounded.SaveAlt, 100L * 1024 * 1024, 100L * 1024 * 1024, 1707200000000L, "Space"),
    Milestone("Day One", "First app use", Icons.Rounded.LocalFireDepartment, 1, 1, 1706400000000L, "Streak"),
    Milestone("3-Day Streak", "3 consecutive days", Icons.Rounded.LocalFireDepartment, 3, 3, 1706700000000L, "Streak")
)
