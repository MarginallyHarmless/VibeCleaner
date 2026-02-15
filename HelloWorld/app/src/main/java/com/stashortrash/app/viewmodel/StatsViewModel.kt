package com.stashortrash.app.viewmodel

import android.app.Application
import android.os.Build
import android.provider.MediaStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stashortrash.app.PhotoCleanupApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MonthlyStats(val yearMonth: String, val kept: Int, val deleted: Int)
data class MonthlySpace(val yearMonth: String, val bytes: Long)
data class DailyProgress(val day: String, val totalPhotos: Int, val totalReviewed: Int)

data class Milestone(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val targetValue: Long,
    val currentValue: Long,
    val unlockedAt: Long?,
    val category: String
)

data class StatsUiState(
    val isLoading: Boolean = true,
    val totalPhotosOnDevice: Int = 0,
    val totalReviewed: Int = 0,
    val totalDeleted: Int = 0,
    val totalKept: Int = 0,
    val spaceRecoveredBytes: Long = 0L,
    val deleteRatio: Float = 0f,
    val avgPerSession: Int = 0,
    val busiestDay: String = "",
    val busiestDayCount: Int = 0,
    val progressByDay: List<DailyProgress> = emptyList(),
    val activityByMonth: List<MonthlyStats> = emptyList(),
    val qualityDistribution: Map<String, Int> = emptyMap(),
    val duplicateGroupsFound: Int = 0,
    val duplicatesRemoved: Int = 0,
    val lowQualityFlagged: Int = 0,
    val largestFileDeletedSize: Long = 0L,
    val largestFileDeletedDate: Long = 0L,
    val longestStreak: Int = 0,
    val usingSince: Long = 0L,
    val milestones: List<Milestone> = emptyList()
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    val isPremium: Boolean get() = (getApplication<PhotoCleanupApp>()).appPreferences.isPremium

    private val database = (application as PhotoCleanupApp).database
    private val reviewedPhotoDao = database.reviewedPhotoDao()
    private val photoHashDao = database.photoHashDao()
    private val duplicateGroupDao = database.duplicateGroupDao()

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        // Observe all 3 tables — whenever any row changes, Room emits and we recompute
        viewModelScope.launch {
            combine(
                reviewedPhotoDao.getReviewedCountAsFlow(),
                photoHashDao.getCountAsFlow(),
                duplicateGroupDao.getGroupCountAsFlow()
            ) { _, _, _ ->
                // We don't use the counts directly — they're just triggers
            }
                .flowOn(Dispatchers.IO)
                .collect {
                    recomputeStats()
                }
        }
    }

    private suspend fun recomputeStats() {
        try {
            // Basic counts
            val deletedCount = reviewedPhotoDao.getDeletedCount()
            val keptCount = reviewedPhotoDao.getKeptCount()
            val totalReviewed = deletedCount + keptCount
            val deleteRatio = if (totalReviewed > 0) deletedCount.toFloat() / totalReviewed else 0f

            // Space recovered (from reviewed_photos.fileSize directly)
            val spaceRecovered = reviewedPhotoDao.getDeletedFilesSizeSum()

            // Progress chart: total photos vs reviewed over time
            val progressByDay = computeProgressByDay()

            // Total photos on device (from last progress entry, or query MediaStore)
            val totalPhotosOnDevice = progressByDay.lastOrNull()?.totalPhotos ?: countPhotosOnDevice()

            // Monthly activity data
            val reviewsByMonth = reviewedPhotoDao.getReviewsByMonth()
            val monthMap = mutableMapOf<String, MonthlyStats>()
            for (row in reviewsByMonth) {
                val existing = monthMap[row.month] ?: MonthlyStats(row.month, 0, 0)
                monthMap[row.month] = when (row.action) {
                    "keep" -> existing.copy(kept = existing.kept + row.count)
                    "deleted" -> existing.copy(deleted = existing.deleted + row.count)
                    else -> existing
                }
            }
            val activityByMonth = monthMap.entries.sortedBy { it.key }.map { it.value }

            // Busiest day
            val busiestDay = reviewedPhotoDao.getBusiestDay()

            // Session calculation & streak
            val timestamps = reviewedPhotoDao.getAllReviewTimestamps()
            val avgPerSession = calculateAvgPerSession(timestamps)
            val longestStreak = calculateLongestStreak(timestamps)

            // Earliest review date
            val usingSince = reviewedPhotoDao.getEarliestReviewDate() ?: 0L

            // Largest deleted file (from reviewed_photos.fileSize directly)
            val largestFile = reviewedPhotoDao.getLargestDeletedFile()

            // Duplicate stats
            val duplicateGroupsFound = duplicateGroupDao.getGroupCount()
            val duplicatesRemoved = duplicateGroupDao.getExtraCopiesCount()

            // Quality distribution
            val qualityIssues = photoHashDao.getAllQualityIssues()
            val sharpCount = photoHashDao.getSharpPhotoCount()
            val slightlySoftCount = photoHashDao.getSlightlySoftPhotoCount()

            val qualityDist = mutableMapOf<String, Int>()
            qualityDist["Sharp"] = sharpCount
            qualityDist["Slightly Soft"] = slightlySoftCount
            var blurryCount = 0
            var darkCount = 0
            var screenshotCount = 0
            for (issues in qualityIssues) {
                if (issues.contains("BLURRY")) blurryCount++
                if (issues.contains("UNDEREXPOSED")) darkCount++
                if (issues.contains("SCREENSHOT")) screenshotCount++
            }
            qualityDist["Blurry"] = blurryCount
            qualityDist["Too Dark"] = darkCount
            qualityDist["Screenshot"] = screenshotCount

            val lowQualityFlagged = qualityIssues.size

            // Milestones
            val milestones = computeMilestones(
                totalReviewed, spaceRecovered, longestStreak, usingSince, timestamps
            )

            _uiState.value = StatsUiState(
                isLoading = false,
                totalPhotosOnDevice = totalPhotosOnDevice,
                totalReviewed = totalReviewed,
                totalDeleted = deletedCount,
                totalKept = keptCount,
                spaceRecoveredBytes = spaceRecovered,
                deleteRatio = deleteRatio,
                avgPerSession = avgPerSession,
                busiestDay = busiestDay?.day ?: "",
                busiestDayCount = busiestDay?.count ?: 0,
                progressByDay = progressByDay,
                activityByMonth = activityByMonth,
                qualityDistribution = qualityDist,
                duplicateGroupsFound = duplicateGroupsFound,
                duplicatesRemoved = duplicatesRemoved,
                lowQualityFlagged = lowQualityFlagged,
                largestFileDeletedSize = largestFile?.fileSize ?: 0L,
                largestFileDeletedDate = largestFile?.dateAdded ?: 0L,
                longestStreak = longestStreak,
                usingSince = usingSince,
                milestones = milestones
            )
        } catch (e: Exception) {
            _uiState.value = StatsUiState(isLoading = false)
        }
    }

    /**
     * Build daily progress data: total photos on device and cumulative reviewed count per day.
     * Total photos = cumulative count of photos by DATE_ADDED from MediaStore.
     * Reviewed = cumulative count from reviewed_photos by reviewedAt.
     */
    private suspend fun computeProgressByDay(): List<DailyProgress> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // Get daily reviewed counts from DB
        val reviewedByDay = reviewedPhotoDao.getReviewedCountByDay()
        if (reviewedByDay.isEmpty()) return emptyList()

        val firstReviewDay = reviewedByDay.first().day
        val today = dateFormat.format(System.currentTimeMillis())

        // Get all photo DATE_ADDED timestamps from MediaStore (all volumes)
        val context = getApplication<Application>()
        val photoDayCountMap = mutableMapOf<String, Int>()

        val projection = arrayOf(MediaStore.MediaColumns.DATE_ADDED)
        val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context)
        } else {
            setOf(MediaStore.VOLUME_EXTERNAL)
        }
        for (volume in volumes) {
            val contentUri = MediaStore.Images.Media.getContentUri(volume)
            context.contentResolver.query(
                contentUri, projection, null, null, null
            )?.use { cursor ->
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val dateAdded = cursor.getLong(dateCol) * 1000 // seconds → millis
                    val day = dateFormat.format(dateAdded)
                    photoDayCountMap[day] = (photoDayCountMap[day] ?: 0) + 1
                }
            }
        }

        // Build sorted list of all relevant days (from first review to today)
        val allDays = (photoDayCountMap.keys + reviewedByDay.map { it.day } + today)
            .filter { it >= firstReviewDay }
            .distinct()
            .sorted()

        // Build cumulative reviewed map
        val reviewedMap = mutableMapOf<String, Int>()
        for (dc in reviewedByDay) {
            reviewedMap[dc.day] = dc.count
        }

        // Compute cumulative total photos up to each day
        val sortedAllPhotoDays = photoDayCountMap.keys.sorted()

        // Build result with cumulative values
        var cumulativeReviewed = 0
        // Pre-compute total photos added before first review day
        var cumulativeTotal = 0
        for (day in sortedAllPhotoDays) {
            if (day < firstReviewDay) {
                cumulativeTotal += photoDayCountMap[day]!!
            }
        }

        // Also count reviewed photos before first review day (shouldn't exist, but be safe)
        // Actually we need to sum reviewed_photos before firstReviewDay for cumulative start
        // Since firstReviewDay IS the first review day, cumulativeReviewed starts at 0

        val result = mutableListOf<DailyProgress>()

        // Add a starting point (day before first review) so we always have >= 2 data points
        val cal = Calendar.getInstance()
        cal.time = dateFormat.parse(firstReviewDay)!!
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val dayBefore = dateFormat.format(cal.time)
        // Count photos added on or before dayBefore
        var photosBeforeStart = 0
        for ((day, count) in photoDayCountMap) {
            if (day <= dayBefore) photosBeforeStart += count
        }
        result.add(DailyProgress(dayBefore, photosBeforeStart, 0))

        for (day in allDays) {
            cumulativeTotal += photoDayCountMap[day] ?: 0
            cumulativeReviewed += reviewedMap[day] ?: 0
            result.add(DailyProgress(day, cumulativeTotal, cumulativeReviewed))
        }

        // Downsample if too many days (keep ~30 points max for readability)
        return if (result.size > 30) {
            val step = result.size / 30
            result.filterIndexed { index, _ ->
                index % step == 0 || index == result.lastIndex
            }
        } else {
            result
        }
    }

    private fun countPhotosOnDevice(): Int {
        val context = getApplication<Application>()
        var totalCount = 0
        val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context)
        } else {
            setOf(MediaStore.VOLUME_EXTERNAL)
        }
        for (volume in volumes) {
            val contentUri = MediaStore.Images.Media.getContentUri(volume)
            val cursor = context.contentResolver.query(
                contentUri, arrayOf(MediaStore.MediaColumns._ID),
                null, null, null
            )
            totalCount += cursor?.count ?: 0
            cursor?.close()
        }
        return totalCount
    }

    private fun calculateAvgPerSession(timestamps: List<Long>): Int {
        if (timestamps.isEmpty()) return 0
        val sessionGapMs = 30 * 60 * 1000L // 30 minutes
        var sessionCount = 1
        for (i in 1 until timestamps.size) {
            if (timestamps[i] - timestamps[i - 1] > sessionGapMs) {
                sessionCount++
            }
        }
        return timestamps.size / sessionCount
    }

    private fun calculateLongestStreak(timestamps: List<Long>): Int {
        if (timestamps.isEmpty()) return 0
        val cal = Calendar.getInstance()
        val days = timestamps.map { ts ->
            cal.timeInMillis = ts
            // Normalize to day: year * 400 + dayOfYear gives a unique day number
            cal.get(Calendar.YEAR) * 400 + cal.get(Calendar.DAY_OF_YEAR)
        }.distinct().sorted()

        if (days.isEmpty()) return 0
        var longest = 1
        var current = 1
        for (i in 1 until days.size) {
            if (days[i] == days[i - 1] + 1) {
                current++
                if (current > longest) longest = current
            } else {
                current = 1
            }
        }
        return longest
    }

    private fun computeMilestones(
        totalReviewed: Int,
        spaceRecovered: Long,
        longestStreak: Int,
        usingSince: Long,
        timestamps: List<Long>
    ): List<Milestone> {
        val milestones = mutableListOf<Milestone>()

        // Photo count milestones
        data class MilestoneDef(val name: String, val desc: String, val icon: ImageVector, val target: Long, val cat: String)

        val photoDefs = listOf(
            MilestoneDef("First Swipe", "Review your first photo", Icons.Rounded.TouchApp, 1, "Photos"),
            MilestoneDef("Getting Started", "Review 100 photos", Icons.Rounded.CameraAlt, 100, "Photos"),
            MilestoneDef("Photo Pro", "Review 500 photos", Icons.Rounded.Star, 500, "Photos"),
            MilestoneDef("Thousand Club", "Review 1,000 photos", Icons.Rounded.EmojiEvents, 1_000, "Photos"),
            MilestoneDef("Cleanup Master", "Review 5,000 photos", Icons.Rounded.MilitaryTech, 5_000, "Photos"),
            MilestoneDef("Photo Legend", "Review 10,000 photos", Icons.Rounded.WorkspacePremium, 10_000, "Photos")
        )

        val spaceDefs = listOf(
            MilestoneDef("Space Saver", "Recover 100 MB", Icons.Rounded.SaveAlt, 100L * 1024 * 1024, "Space"),
            MilestoneDef("Gigabyte Club", "Recover 1 GB", Icons.Rounded.Storage, 1024L * 1024 * 1024, "Space"),
            MilestoneDef("Storage Hero", "Recover 5 GB", Icons.Rounded.RocketLaunch, 5L * 1024 * 1024 * 1024, "Space"),
            MilestoneDef("Digital Minimalist", "Recover 10 GB", Icons.Rounded.CleaningServices, 10L * 1024 * 1024 * 1024, "Space")
        )

        val streakDefs = listOf(
            MilestoneDef("Day One", "First app use", Icons.Rounded.Bolt, 1, "Streak"),
            MilestoneDef("3-Day Streak", "3 consecutive days", Icons.Rounded.LocalFireDepartment, 3, "Streak"),
            MilestoneDef("Week Warrior", "7-day streak", Icons.Rounded.FitnessCenter, 7, "Streak"),
            MilestoneDef("Monthly Master", "30-day streak", Icons.Rounded.AutoAwesome, 30, "Streak")
        )

        // Derive unlock dates from data
        for (def in photoDefs) {
            val unlockDate = if (totalReviewed >= def.target && timestamps.size >= def.target.toInt()) {
                timestamps[def.target.toInt() - 1]
            } else null
            milestones.add(
                Milestone(def.name, def.desc, def.icon, def.target, totalReviewed.toLong(), unlockDate, def.cat)
            )
        }

        for (def in spaceDefs) {
            // For space milestones, we can't derive exact unlock date easily, use usingSince if unlocked
            val unlocked = spaceRecovered >= def.target
            milestones.add(
                Milestone(def.name, def.desc, def.icon, def.target, spaceRecovered, if (unlocked) usingSince else null, def.cat)
            )
        }

        for (def in streakDefs) {
            val unlocked = longestStreak >= def.target
            milestones.add(
                Milestone(def.name, def.desc, def.icon, def.target, longestStreak.toLong(), if (unlocked) usingSince else null, def.cat)
            )
        }

        return milestones
    }
}
