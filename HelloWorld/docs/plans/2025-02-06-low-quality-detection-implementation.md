# Low Quality Photo Detection - Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Detect and surface low-quality photos (blurry, dark, overexposed) alongside duplicate detection.

**Architecture:** Extend existing scan worker to compute quality metrics during hash computation. Store scores in PhotoHash entity. Display in new tab using same UI patterns as duplicates.

**Tech Stack:** Kotlin, Jetpack Compose, Room database, WorkManager

---

## Task 1: Extend PhotoHash Entity with Quality Fields

**Files:**
- Modify: `app/src/main/java/com/example/photocleanup/data/PhotoHash.kt`

**Step 1: Add quality score fields to PhotoHash data class**

Open PhotoHash.kt and add 5 new fields after `colorHistogram` and before `fileSize`:

```kotlin
@Entity(tableName = "photo_hashes")
data class PhotoHash(
    @PrimaryKey val uri: String,
    val hash: Long,
    val pHash: Long = 0,
    val edgeHash: Long = 0,
    val colorHistogram: String = "",
    // Quality metrics (0.0 = bad, 1.0 = good)
    val sharpnessScore: Float = 0f,
    val exposureScore: Float = 0f,
    val noiseScore: Float = 0f,
    val overallQuality: Float = 0f,
    val qualityIssues: String = "",  // Comma-separated: "BLURRY,DARK"
    val fileSize: Long,
    val width: Int,
    val height: Int,
    val dateAdded: Long,
    val lastScanned: Long,
    val bucketId: Long,
    val bucketName: String,
    val algorithmVersion: Int = 0
)
```

**Step 2: Verify build compiles**

Run: `./gradlew.bat assembleDebug --quiet`
Expected: Build fails (database version mismatch) - this is expected, migration comes next

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/photocleanup/data/PhotoHash.kt
git commit -m "feat: add quality score fields to PhotoHash entity"
```

---

## Task 2: Create Database Migration

**Files:**
- Modify: `app/src/main/java/com/example/photocleanup/data/PhotoDatabase.kt`

**Step 1: Update database version**

Change line 12 from `version = 6` to `version = 7`:

```kotlin
@Database(entities = [PhotoHash::class], version = 7, exportSchema = false)
```

**Step 2: Add migration 6→7**

After MIGRATION_5_6 (around line 104), add:

```kotlin
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE photo_hashes ADD COLUMN sharpnessScore REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE photo_hashes ADD COLUMN exposureScore REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE photo_hashes ADD COLUMN noiseScore REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE photo_hashes ADD COLUMN overallQuality REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE photo_hashes ADD COLUMN qualityIssues TEXT NOT NULL DEFAULT ''")
    }
}
```

**Step 3: Register migration**

Update the `.addMigrations()` call (around line 113) to include MIGRATION_6_7:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
```

**Step 4: Verify build compiles**

Run: `./gradlew.bat assembleDebug --quiet`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/example/photocleanup/data/PhotoDatabase.kt
git commit -m "feat: add database migration for quality score columns"
```

---

## Task 3: Create QualityAnalyzer Utility

**Files:**
- Create: `app/src/main/java/com/example/photocleanup/util/QualityAnalyzer.kt`

**Step 1: Create QualityAnalyzer object with data classes**

```kotlin
package com.example.photocleanup.util

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Analyzes photo quality to detect issues like blur, exposure problems, and noise.
 *
 * Quality scores range from 0.0 (bad) to 1.0 (good).
 */
object QualityAnalyzer {

    private const val DEBUG = false

    // Issue types
    enum class QualityIssue {
        BLURRY,           // Out of focus or motion blur
        VERY_DARK,        // Almost completely black
        VERY_BRIGHT,      // Almost completely white
        UNDEREXPOSED,     // Too dark but has content
        OVEREXPOSED,      // Blown highlights
        NOISY,            // High noise/grain
        LOW_CONTRAST      // Flat, washed out
    }

    // Thresholds (balanced mode - catch most issues, some false positives OK)
    private const val SHARPNESS_THRESHOLD = 0.25f      // Below = blurry
    private const val DARK_THRESHOLD = 0.08f           // Below = very dark
    private const val BRIGHT_THRESHOLD = 0.92f         // Above = very bright
    private const val UNDEREXPOSED_THRESHOLD = 0.25f   // Below = underexposed
    private const val OVEREXPOSED_THRESHOLD = 0.75f    // Above = overexposed
    private const val NOISE_THRESHOLD = 0.6f           // Above = noisy
    private const val CONTRAST_THRESHOLD = 0.15f       // Below = low contrast

    data class QualityResult(
        val sharpnessScore: Float,
        val exposureScore: Float,
        val noiseScore: Float,
        val overallQuality: Float,
        val issues: List<QualityIssue>
    ) {
        val issuesString: String get() = issues.joinToString(",") { it.name }
        val hasIssues: Boolean get() = issues.isNotEmpty()

        // Human-readable primary issue
        val primaryIssue: String? get() = when (issues.firstOrNull()) {
            QualityIssue.BLURRY -> "Blurry"
            QualityIssue.VERY_DARK -> "Black"
            QualityIssue.VERY_BRIGHT -> "White"
            QualityIssue.UNDEREXPOSED -> "Too Dark"
            QualityIssue.OVEREXPOSED -> "Overexposed"
            QualityIssue.NOISY -> "Noisy"
            QualityIssue.LOW_CONTRAST -> "Low Contrast"
            null -> null
        }
    }

    /**
     * Analyze a bitmap and return quality metrics.
     * Expects a small bitmap (32x32 to 64x64) for performance.
     */
    fun analyze(bitmap: Bitmap): QualityResult {
        val sharpness = computeSharpness(bitmap)
        val (exposure, avgBrightness, contrast) = computeExposure(bitmap)
        val noise = computeNoise(bitmap)

        // Detect issues
        val issues = mutableListOf<QualityIssue>()

        // Exposure issues (check first - these are most obvious)
        when {
            avgBrightness < DARK_THRESHOLD -> issues.add(QualityIssue.VERY_DARK)
            avgBrightness > BRIGHT_THRESHOLD -> issues.add(QualityIssue.VERY_BRIGHT)
            avgBrightness < UNDEREXPOSED_THRESHOLD -> issues.add(QualityIssue.UNDEREXPOSED)
            avgBrightness > OVEREXPOSED_THRESHOLD -> issues.add(QualityIssue.OVEREXPOSED)
        }

        // Sharpness (only check if not completely dark/bright)
        if (avgBrightness in DARK_THRESHOLD..BRIGHT_THRESHOLD) {
            if (sharpness < SHARPNESS_THRESHOLD) {
                issues.add(QualityIssue.BLURRY)
            }
        }

        // Noise (only meaningful if image has content)
        if (avgBrightness in DARK_THRESHOLD..BRIGHT_THRESHOLD && noise > NOISE_THRESHOLD) {
            issues.add(QualityIssue.NOISY)
        }

        // Contrast
        if (contrast < CONTRAST_THRESHOLD && avgBrightness in 0.2f..0.8f) {
            issues.add(QualityIssue.LOW_CONTRAST)
        }

        // Overall quality (weighted average, penalize for issues)
        val baseQuality = (sharpness * 0.4f + exposure * 0.4f + (1f - noise) * 0.2f)
        val penalty = issues.size * 0.15f
        val overallQuality = (baseQuality - penalty).coerceIn(0f, 1f)

        if (DEBUG) {
            android.util.Log.d("QualityAnalyzer",
                "sharpness=$sharpness, exposure=$exposure, noise=$noise, " +
                "brightness=$avgBrightness, contrast=$contrast, issues=$issues")
        }

        return QualityResult(
            sharpnessScore = sharpness,
            exposureScore = exposure,
            noiseScore = noise,
            overallQuality = overallQuality,
            issues = issues
        )
    }

    /**
     * Compute sharpness using Laplacian variance.
     * Sharp images have high variance in the Laplacian (edges).
     */
    private fun computeSharpness(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) return 0.5f

        // Laplacian kernel: [0, 1, 0], [1, -4, 1], [0, 1, 0]
        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = getLuminance(bitmap.getPixel(x, y))
                val top = getLuminance(bitmap.getPixel(x, y - 1))
                val bottom = getLuminance(bitmap.getPixel(x, y + 1))
                val left = getLuminance(bitmap.getPixel(x - 1, y))
                val right = getLuminance(bitmap.getPixel(x + 1, y))

                val laplacian = (top + bottom + left + right - 4 * center).toDouble()
                sum += laplacian
                sumSq += laplacian * laplacian
                count++
            }
        }

        if (count == 0) return 0.5f

        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)

        // Normalize variance to 0-1 range
        // Empirically, sharp images have variance > 500, blurry < 100
        val normalized = (sqrt(variance) / 30.0).coerceIn(0.0, 1.0)
        return normalized.toFloat()
    }

    /**
     * Compute exposure metrics from histogram analysis.
     * Returns (exposureScore, avgBrightness, contrast)
     */
    private fun computeExposure(bitmap: Bitmap): Triple<Float, Float, Float> {
        val histogram = IntArray(256)
        var totalBrightness = 0L
        var pixelCount = 0

        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val lum = getLuminance(bitmap.getPixel(x, y))
                histogram[lum]++
                totalBrightness += lum
                pixelCount++
            }
        }

        if (pixelCount == 0) return Triple(0.5f, 0.5f, 0.5f)

        val avgBrightness = (totalBrightness.toFloat() / pixelCount) / 255f

        // Find percentiles for contrast
        var cumulative = 0
        var p5 = 0
        var p95 = 255
        for (i in 0..255) {
            cumulative += histogram[i]
            if (p5 == 0 && cumulative >= pixelCount * 0.05) p5 = i
            if (cumulative >= pixelCount * 0.95) {
                p95 = i
                break
            }
        }
        val contrast = (p95 - p5) / 255f

        // Exposure score: penalize extremes
        val exposureScore = when {
            avgBrightness < 0.1f -> avgBrightness * 2  // Very dark
            avgBrightness > 0.9f -> (1f - avgBrightness) * 2  // Very bright
            avgBrightness < 0.3f -> 0.5f + (avgBrightness - 0.1f)  // Dark
            avgBrightness > 0.7f -> 0.5f + (0.9f - avgBrightness)  // Bright
            else -> 0.8f + (0.5f - abs(avgBrightness - 0.5f)) * 0.4f  // Good range
        }.coerceIn(0f, 1f)

        return Triple(exposureScore, avgBrightness, contrast)
    }

    /**
     * Compute noise estimate using local variance.
     * Higher values = more noise.
     */
    private fun computeNoise(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 4 || height < 4) return 0.3f

        // Divide into 4x4 blocks and measure local variance
        val blockSize = minOf(width, height) / 4
        if (blockSize < 2) return 0.3f

        var totalVariance = 0.0
        var blockCount = 0

        for (by in 0 until 4) {
            for (bx in 0 until 4) {
                val startX = bx * blockSize
                val startY = by * blockSize

                var sum = 0.0
                var sumSq = 0.0
                var count = 0

                for (y in startY until minOf(startY + blockSize, height)) {
                    for (x in startX until minOf(startX + blockSize, width)) {
                        val lum = getLuminance(bitmap.getPixel(x, y)).toDouble()
                        sum += lum
                        sumSq += lum * lum
                        count++
                    }
                }

                if (count > 0) {
                    val mean = sum / count
                    val variance = (sumSq / count) - (mean * mean)
                    totalVariance += variance
                    blockCount++
                }
            }
        }

        if (blockCount == 0) return 0.3f

        val avgVariance = totalVariance / blockCount
        // Normalize: low variance = smooth/good, high variance = noisy
        // Empirically, noise variance > 200 is noticeable
        val normalized = (sqrt(avgVariance) / 25.0).coerceIn(0.0, 1.0)
        return normalized.toFloat()
    }

    /**
     * Get luminance (perceived brightness) from a pixel color.
     */
    private fun getLuminance(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        // Standard luminance formula
        return ((0.299 * r) + (0.587 * g) + (0.114 * b)).toInt().coerceIn(0, 255)
    }
}
```

**Step 2: Verify build compiles**

Run: `./gradlew.bat assembleDebug --quiet`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/photocleanup/util/QualityAnalyzer.kt
git commit -m "feat: add QualityAnalyzer utility for photo quality detection"
```

---

## Task 4: Integrate Quality Analysis into Scan Worker

**Files:**
- Modify: `app/src/main/java/com/example/photocleanup/worker/DuplicateScanWorker.kt`

**Step 1: Add import for QualityAnalyzer**

Add near the top with other imports:

```kotlin
import com.example.photocleanup.util.QualityAnalyzer
```

**Step 2: Update CURRENT_ALGORITHM_VERSION**

Change from 5 to 6 (around line 58) to force re-scan:

```kotlin
private const val CURRENT_ALGORITHM_VERSION = 6
```

**Step 3: Modify computeHashForPhoto() to compute quality**

Find the `computeHashForPhoto()` method (around line 358-397). After computing hashes, add quality analysis.

Replace the PhotoHash construction (around line 379-393) with:

```kotlin
// Compute quality metrics from the same bitmap
val qualityBitmap = imageHasher.loadBitmapForQuality(context, photoInfo.uri)
val qualityResult = if (qualityBitmap != null) {
    QualityAnalyzer.analyze(qualityBitmap).also {
        qualityBitmap.recycle()
    }
} else {
    // Default if bitmap load fails
    QualityAnalyzer.QualityResult(0.5f, 0.5f, 0.3f, 0.5f, emptyList())
}

PhotoHash(
    uri = photoInfo.uri.toString(),
    hash = allHashes.dHash,
    pHash = allHashes.pHash,
    edgeHash = allHashes.edgeHash,
    colorHistogram = allHashes.colorHistogramBase64,
    sharpnessScore = qualityResult.sharpnessScore,
    exposureScore = qualityResult.exposureScore,
    noiseScore = qualityResult.noiseScore,
    overallQuality = qualityResult.overallQuality,
    qualityIssues = qualityResult.issuesString,
    fileSize = photoInfo.size,
    width = photoInfo.width,
    height = photoInfo.height,
    dateAdded = photoInfo.dateAdded,
    lastScanned = System.currentTimeMillis(),
    bucketId = photoInfo.bucketId,
    bucketName = photoInfo.bucketName,
    algorithmVersion = CURRENT_ALGORITHM_VERSION
)
```

**Step 4: Add loadBitmapForQuality method to ImageHasher**

Open `app/src/main/java/com/example/photocleanup/util/ImageHasher.kt` and add this method (after `computeAllHashesFast`, around line 572):

```kotlin
/**
 * Load a 64x64 bitmap for quality analysis.
 * Slightly larger than hash bitmap for better quality detection.
 */
fun loadBitmapForQuality(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)

            // Calculate sample size for ~64x64 output
            val targetSize = 64
            options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
            options.inJustDecodeBounds = false

            context.contentResolver.openInputStream(uri)?.use { stream2 ->
                BitmapFactory.decodeStream(stream2, null, options)?.let { bitmap ->
                    Bitmap.createScaledBitmap(bitmap, 64, 64, true).also {
                        if (it != bitmap) bitmap.recycle()
                    }
                }
            }
        }
    } catch (e: Exception) {
        if (DEBUG) Log.e(TAG, "Failed to load bitmap for quality: ${e.message}")
        null
    }
}
```

**Step 5: Verify build compiles**

Run: `./gradlew.bat assembleDebug --quiet`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/java/com/example/photocleanup/worker/DuplicateScanWorker.kt
git add app/src/main/java/com/example/photocleanup/util/ImageHasher.kt
git commit -m "feat: integrate quality analysis into scan worker"
```

---

## Task 5: Add DAO Methods for Low Quality Photos

**Files:**
- Modify: `app/src/main/java/com/example/photocleanup/data/PhotoHashDao.kt`

**Step 1: Add query methods for low quality photos**

Add these methods to the PhotoHashDao interface:

```kotlin
@Query("SELECT * FROM photo_hashes WHERE qualityIssues != '' ORDER BY overallQuality ASC")
fun getLowQualityPhotos(): Flow<List<PhotoHash>>

@Query("SELECT COUNT(*) FROM photo_hashes WHERE qualityIssues != ''")
fun getLowQualityCount(): Flow<Int>

@Query("SELECT * FROM photo_hashes WHERE qualityIssues != '' ORDER BY overallQuality ASC")
suspend fun getLowQualityPhotosOnce(): List<PhotoHash>
```

**Step 2: Verify build compiles**

Run: `./gradlew.bat assembleDebug --quiet`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/photocleanup/data/PhotoHashDao.kt
git commit -m "feat: add DAO methods for querying low quality photos"
```

---

## Task 6: Create LowQualityViewModel

**Files:**
- Create: `app/src/main/java/com/example/photocleanup/viewmodel/LowQualityViewModel.kt`

**Step 1: Create ViewModel following DuplicatesViewModel pattern**

```kotlin
package com.example.photocleanup.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.photocleanup.data.PhotoDatabase
import com.example.photocleanup.data.PhotoHash
import com.example.photocleanup.worker.DuplicateScanWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LowQualityViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PhotoDatabase.getDatabase(application)
    private val photoHashDao = database.photoHashDao()
    private val workManager = WorkManager.getInstance(application)

    // Scan state (reuse from duplicates - same scan)
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // Low quality photos
    private val _lowQualityPhotos = MutableStateFlow<List<PhotoHash>>(emptyList())
    val lowQualityPhotos: StateFlow<List<PhotoHash>> = _lowQualityPhotos.asStateFlow()

    // Selection state
    private val _selectedUris = MutableStateFlow<Set<String>>(emptySet())
    val selectedUris: StateFlow<Set<String>> = _selectedUris.asStateFlow()

    // Count
    private val _lowQualityCount = MutableStateFlow(0)
    val lowQualityCount: StateFlow<Int> = _lowQualityCount.asStateFlow()

    // Fullscreen preview
    private val _previewUri = MutableStateFlow<Uri?>(null)
    val previewUri: StateFlow<Uri?> = _previewUri.asStateFlow()

    init {
        observeScanState()
        loadLowQualityPhotos()
    }

    private fun observeScanState() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkLiveData(DuplicateScanWorker.WORK_NAME)
                .observeForever { workInfos ->
                    val workInfo = workInfos?.firstOrNull()
                    _scanState.value = when (workInfo?.state) {
                        WorkInfo.State.RUNNING -> {
                            val progress = workInfo.progress.getInt("progress", 0)
                            val total = workInfo.progress.getInt("total", 0)
                            val status = workInfo.progress.getString("status") ?: "Scanning..."
                            ScanState.Scanning(progress, total, status)
                        }
                        WorkInfo.State.SUCCEEDED -> ScanState.Completed
                        WorkInfo.State.FAILED -> ScanState.Error("Scan failed")
                        WorkInfo.State.CANCELLED -> ScanState.Idle
                        else -> _scanState.value
                    }
                }
        }
    }

    private fun loadLowQualityPhotos() {
        viewModelScope.launch {
            photoHashDao.getLowQualityPhotos().collect { photos ->
                _lowQualityPhotos.value = photos
                _lowQualityCount.value = photos.size
            }
        }
    }

    fun toggleSelection(uri: String) {
        _selectedUris.value = if (uri in _selectedUris.value) {
            _selectedUris.value - uri
        } else {
            _selectedUris.value + uri
        }
    }

    fun clearSelection() {
        _selectedUris.value = emptySet()
    }

    fun selectAll() {
        _selectedUris.value = _lowQualityPhotos.value.map { it.uri }.toSet()
    }

    fun showPreview(uri: Uri) {
        _previewUri.value = uri
    }

    fun hidePreview() {
        _previewUri.value = null
    }

    fun getSelectedCount(): Int = _selectedUris.value.size

    // Delete selected photos (called after user confirms via system dialog)
    fun onPhotosDeleted(deletedUris: List<Uri>) {
        viewModelScope.launch {
            // Remove from database
            deletedUris.forEach { uri ->
                photoHashDao.deleteByUri(uri.toString())
            }
            // Clear selection
            _selectedUris.value = _selectedUris.value - deletedUris.map { it.toString() }.toSet()
        }
    }
}

// Reuse ScanState from DuplicatesViewModel or define here if not shared
sealed class ScanState {
    object Idle : ScanState()
    data class Scanning(val progress: Int, val total: Int, val status: String) : ScanState()
    object Completed : ScanState()
    data class Error(val message: String) : ScanState()
}
```

**Step 2: Verify build compiles**

Run: `./gradlew.bat assembleDebug --quiet`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/photocleanup/viewmodel/LowQualityViewModel.kt
git commit -m "feat: add LowQualityViewModel for managing low quality photo state"
```

---

## Task 7: Create LowQualityPhotoCard Component

**Files:**
- Create: `app/src/main/java/com/example/photocleanup/ui/components/LowQualityPhotoCard.kt`

**Step 1: Create card component**

```kotlin
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
```

**Step 2: Verify build compiles**

Run: `./gradlew.bat assembleDebug --quiet`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/photocleanup/ui/components/LowQualityPhotoCard.kt
git commit -m "feat: add LowQualityPhotoCard component with issue badges"
```

---

## Task 8: Create LowQualityScreen

**Files:**
- Create: `app/src/main/java/com/example/photocleanup/ui/screens/LowQualityScreen.kt`

**Step 1: Create screen following DuplicatesScreen pattern**

```kotlin
package com.example.photocleanup.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.photocleanup.data.PhotoHash
import com.example.photocleanup.ui.components.FullscreenPhotoPreview
import com.example.photocleanup.ui.components.LowQualityPhotoCard
import com.example.photocleanup.ui.components.ScanStatusCard
import com.example.photocleanup.ui.theme.*
import com.example.photocleanup.viewmodel.LowQualityViewModel
import com.example.photocleanup.viewmodel.ScanState
import com.example.photocleanup.worker.DuplicateScanWorker

@Composable
fun LowQualityScreen(
    viewModel: LowQualityViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scanState by viewModel.scanState.collectAsState()
    val lowQualityPhotos by viewModel.lowQualityPhotos.collectAsState()
    val selectedUris by viewModel.selectedUris.collectAsState()
    val previewUri by viewModel.previewUri.collectAsState()
    val lowQualityCount by viewModel.lowQualityCount.collectAsState()

    // Delete launcher
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val deletedUris = selectedUris.map { Uri.parse(it) }
            viewModel.onPhotosDeleted(deletedUris)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Low Quality Photos",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = when (scanState) {
                        is ScanState.Scanning -> "Scanning your photos..."
                        is ScanState.Completed -> if (lowQualityCount > 0)
                            "$lowQualityCount photos need attention"
                            else "No quality issues found"
                        is ScanState.Error -> "Scan error occurred"
                        else -> "Scan to find blurry, dark, or overexposed photos"
                    },
                    fontSize = 14.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Scan progress
            if (scanState is ScanState.Scanning) {
                val scanning = scanState as ScanState.Scanning
                ScanStatusCard(
                    progress = scanning.progress,
                    total = scanning.total,
                    status = scanning.status,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            // Content
            when {
                scanState is ScanState.Scanning -> {
                    // Show progress, handled above
                }
                lowQualityPhotos.isEmpty() -> {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (scanState == ScanState.Idle)
                                    "Tap Scan to find low quality photos"
                                    else "No quality issues found!",
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    // Photo grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(
                            items = lowQualityPhotos,
                            key = { it.uri }
                        ) { photo ->
                            LowQualityPhotoCard(
                                photo = photo,
                                isSelected = photo.uri in selectedUris,
                                onToggleSelection = { viewModel.toggleSelection(photo.uri) },
                                onLongPress = { viewModel.showPreview(Uri.parse(photo.uri)) }
                            )
                        }
                    }
                }
            }

            // Bottom action bar
            if (lowQualityPhotos.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = DarkSurfaceSubtle,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Select all button
                        TextButton(
                            onClick = {
                                if (selectedUris.size == lowQualityPhotos.size) {
                                    viewModel.clearSelection()
                                } else {
                                    viewModel.selectAll()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (selectedUris.size == lowQualityPhotos.size) "Deselect All" else "Select All",
                                color = TextSecondary
                            )
                        }

                        // Delete button
                        if (selectedUris.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val urisToDelete = selectedUris.map { Uri.parse(it) }
                                    requestDeletePhotos(context, urisToDelete, deleteLauncher)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DustyMauve
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Delete ${selectedUris.size}")
                            }
                        }
                    }
                }
            }
        }

        // Scan FAB (when not scanning and no selection)
        if (scanState !is ScanState.Scanning && selectedUris.isEmpty()) {
            FloatingActionButton(
                onClick = {
                    val workRequest = OneTimeWorkRequestBuilder<DuplicateScanWorker>().build()
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        DuplicateScanWorker.WORK_NAME,
                        ExistingWorkPolicy.KEEP,
                        workRequest
                    )
                },
                containerColor = Seagrass,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Scan for low quality photos",
                    tint = Color.White
                )
            }
        }

        // Fullscreen preview overlay
        previewUri?.let { uri ->
            FullscreenPhotoPreview(
                uri = uri,
                onDismiss = { viewModel.hidePreview() }
            )
        }
    }
}

private fun requestDeletePhotos(
    context: android.content.Context,
    uris: List<Uri>,
    launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>
) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        val pendingIntent = android.provider.MediaStore.createDeleteRequest(
            context.contentResolver,
            uris
        )
        launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    }
}
```

**Step 2: Verify build compiles**

Run: `./gradlew.bat assembleDebug --quiet`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/example/photocleanup/ui/screens/LowQualityScreen.kt
git commit -m "feat: add LowQualityScreen with grid view and selection"
```

---

## Task 9: Add Navigation for Low Quality Tab

**Files:**
- Modify: `app/src/main/java/com/example/photocleanup/ui/navigation/BottomNavItem.kt`
- Modify: `app/src/main/java/com/example/photocleanup/MainActivity.kt`

**Step 1: Add navigation item**

In `BottomNavItem.kt`, add new item after Duplicates:

```kotlin
object LowQuality : BottomNavItem(
    route = "low_quality",
    title = "Quality",
    icon = Icons.Filled.BrokenImage
)
```

Update the items list to include it:

```kotlin
companion object {
    val items: List<BottomNavItem> = listOf(Cleanup, Duplicates, LowQuality, Settings)
}
```

Add the import at the top:

```kotlin
import androidx.compose.material.icons.filled.BrokenImage
```

**Step 2: Add composable route in MainActivity**

In `MainActivity.kt`, add import:

```kotlin
import com.example.photocleanup.ui.screens.LowQualityScreen
import com.example.photocleanup.viewmodel.LowQualityViewModel
```

Add to bottomNavRoutes list (around line 65-70):

```kotlin
val bottomNavRoutes = listOf(
    BottomNavItem.Cleanup.route,
    BottomNavItem.Duplicates.route,
    BottomNavItem.LowQuality.route,
    BottomNavItem.Settings.route
)
```

Add composable for LowQuality route (after Duplicates composable, before Settings):

```kotlin
composable(
    route = BottomNavItem.LowQuality.route,
    enterTransition = { fadeIn(animationSpec = tween(transitionDuration)) },
    exitTransition = { fadeOut(animationSpec = tween(transitionDuration)) },
    popEnterTransition = { fadeIn(animationSpec = tween(transitionDuration)) },
    popExitTransition = { fadeOut(animationSpec = tween(transitionDuration)) }
) {
    val lowQualityViewModel: LowQualityViewModel = viewModel()
    LowQualityScreen(
        viewModel = lowQualityViewModel
    )
}
```

**Step 3: Verify build compiles**

Run: `./gradlew.bat assembleDebug --quiet`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/example/photocleanup/ui/navigation/BottomNavItem.kt
git add app/src/main/java/com/example/photocleanup/MainActivity.kt
git commit -m "feat: add Low Quality tab to bottom navigation"
```

---

## Task 10: Test on Device

**Step 1: Install on device**

Run: `./gradlew.bat installDebug`

**Step 2: Manual testing checklist**

- [ ] App launches without crash
- [ ] Low Quality tab appears in bottom navigation
- [ ] Tapping Scan button starts scan
- [ ] Progress shows during scan
- [ ] Low quality photos appear in grid after scan
- [ ] Issue labels show on each photo (Blurry, Dark, etc.)
- [ ] Tapping photo toggles selection
- [ ] Long-press shows fullscreen preview
- [ ] Select All button works
- [ ] Delete button deletes selected photos
- [ ] Duplicates tab still works correctly

**Step 3: Commit any fixes found during testing**

---

## Summary

| Task | Description | Files |
|------|-------------|-------|
| 1 | Extend PhotoHash entity | PhotoHash.kt |
| 2 | Database migration | PhotoDatabase.kt |
| 3 | Create QualityAnalyzer | QualityAnalyzer.kt (new) |
| 4 | Integrate into scan worker | DuplicateScanWorker.kt, ImageHasher.kt |
| 5 | Add DAO methods | PhotoHashDao.kt |
| 6 | Create ViewModel | LowQualityViewModel.kt (new) |
| 7 | Create photo card component | LowQualityPhotoCard.kt (new) |
| 8 | Create screen | LowQualityScreen.kt (new) |
| 9 | Add navigation | BottomNavItem.kt, MainActivity.kt |
| 10 | Test on device | Manual testing |

Total estimated time: 60-90 minutes
