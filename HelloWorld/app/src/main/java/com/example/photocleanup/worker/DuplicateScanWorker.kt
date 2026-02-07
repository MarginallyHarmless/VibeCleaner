package com.example.photocleanup.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.photocleanup.R
import com.example.photocleanup.data.DuplicateGroup
import com.example.photocleanup.data.PhotoDatabase
import com.example.photocleanup.data.PhotoHash
import com.example.photocleanup.util.ImageHasher
import com.example.photocleanup.util.QualityAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * WorkManager CoroutineWorker that scans the device's photo gallery for duplicates.
 *
 * The scan process:
 * 1. Query MediaStore for all images
 * 2. Compute dHash for each image (in batches for memory efficiency)
 * 3. Compare hashes to find similar images (Hamming distance <= threshold)
 * 4. Group similar images together
 * 5. Store results in the database
 *
 * Progress is reported via setProgress() and can be observed by the UI.
 * A foreground notification shows scan status to the user.
 */
class DuplicateScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "duplicate_scan_work"

        // Algorithm version - increment this when changing hash algorithm or parameters
        // Old hashes with different version will be recomputed
        // Version 3: Added pHash (perceptual hash using DCT) for two-stage duplicate detection
        // Version 4: Added color histogram pre-filter and restored strict thresholds
        // Version 5: Added edge hash for brightness-invariant structure matching (dual-path entry)
        // Version 6: Added quality analysis (sharpness, exposure, noise detection)
        // Version 7: Stricter quality thresholds to reduce false positives
        // Version 8: Much stricter thresholds, especially for noise (was flagging textured photos)
        // Version 9: Disabled noise detection (algorithm mistakes texture for noise)
        // Version 10: Skip quality checks for screenshots, relaxed blur threshold
        // Version 11: Less aggressive screenshot detection, balanced thresholds
        // Version 12: Better screenshot detection (pure white/black), stricter underexposed, relaxed blur
        // Version 13: More sensitive blur detection (0.28), improved screenshot detection (35% solid OR <20 colors OR combo)
        // Version 14: Even more sensitive blur detection (0.38)
        // Version 15: Tiled Laplacian (256x256, 4x4 grid), motion blur detection, edge density tiebreaker
        // Version 16: Center-weighted scoring — catches misfocused photos where edges are sharp but center is blurry
        // Version 17: Texture-aware tile filtering — skip featureless tiles (sky, walls) to avoid false positives on uniform-color photos
        // Version 18: Better dark photo detection — raised thresholds, bright-pixel rescue for night photos
        // Version 19: Simplified underexposed detection — p95 highlight check only, improved dark-mode screenshot detection
        // Version 20: p99 + avgBrightness gate for underexposed
        // Version 21: Remove screenshot gate from darkness checks (p99 handles dark screenshots naturally), lower thresholds (p99<100, avg<0.25)
        // Version 22: Merge VERY_DARK into UNDEREXPOSED ("Too Dark"), stricter thresholds (p99<80, avg<0.20) to avoid screenshot false positives
        // Version 23: Lower blur threshold (0.60→0.55), detect strong motion blur even when sharpness passes (ratio>8.0)
        // Version 24: Remove black ratio from screenshot detector entirely (dark photos overlap too much), lower blur thresholds (0.50/0.35)
        // Version 25: Remove palette-only screenshot detection (dark/blurry photos have few quantized colors too),
        //   gate center-blur check on sharpness<0.65 (off-center compositions are not misfocused),
        //   remove strong motion blur check (directional content like buildings/stripes causes false positives),
        //   add debug labels (photo filename) to quality analyzer logs
        const val CURRENT_ALGORITHM_VERSION = 25

        // Progress data keys
        const val KEY_PROGRESS = "progress"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val KEY_STATUS = "status"

        // Status values
        const val STATUS_HASHING = "hashing"
        const val STATUS_COMPARING = "comparing"
        const val STATUS_COMPLETE = "complete"
        const val STATUS_ERROR = "error"

        // Result data keys
        const val KEY_GROUPS_FOUND = "groups_found"
        const val KEY_DUPLICATES_FOUND = "duplicates_found"
        const val KEY_ERROR_MESSAGE = "error_message"

        // Notification
        private const val NOTIFICATION_CHANNEL_ID = "duplicate_scan_channel"
        private const val NOTIFICATION_ID = 1001

        // Processing
        private const val BATCH_SIZE = 50
        private const val HASH_PARALLELISM = 8  // Number of concurrent hash computations (higher = faster but more memory)

        private const val TAG = "DuplicateScanWorker"
    }

    private val database = PhotoDatabase.getDatabase(applicationContext)
    private val photoHashDao = database.photoHashDao()
    private val duplicateGroupDao = database.duplicateGroupDao()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Show foreground notification (wrapped in try-catch for permission issues)
            try {
                setForeground(createForegroundInfo("Starting scan..."))
            } catch (e: Exception) {
                // Continue without foreground notification if it fails
                // This can happen if POST_NOTIFICATIONS permission is denied on Android 13+
            }

            // Step 1: Query all photos from MediaStore
            val photos = queryAllPhotos()
            if (photos.isEmpty()) {
                setProgress(workDataOf(
                    KEY_STATUS to STATUS_COMPLETE,
                    KEY_PROGRESS to 100
                ))
                return@withContext Result.success(workDataOf(
                    KEY_GROUPS_FOUND to 0,
                    KEY_DUPLICATES_FOUND to 0
                ))
            }

            // Step 2: Compute hashes for all photos (PARALLEL)
            // Uses Android's thumbnail cache for fast loading (no Coil needed)
            val existingHashes = photoHashDao.getAllSortedByHash().associateBy { it.uri }
            val progressCounter = AtomicInteger(0)
            val semaphore = Semaphore(HASH_PARALLELISM)

            Log.d(TAG, "Starting parallel hash computation with parallelism=$HASH_PARALLELISM")

            // Process photos in parallel with controlled concurrency
            val hashes = coroutineScope {
                photos.mapIndexed { index, photo ->
                    async(Dispatchers.IO) {
                        val waitStart = System.currentTimeMillis()
                        semaphore.withPermit {
                            val waitTime = System.currentTimeMillis() - waitStart
                            if (waitTime > 1000) {
                                Log.d(TAG, "SEMAPHORE WAIT: ${waitTime}ms for photo $index")
                            }

                            // Check for cancellation
                            if (isStopped) {
                                return@withPermit null
                            }

                            computeHashForPhoto(
                                photo,
                                existingHashes,
                                progressCounter,
                                photos.size
                            )
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            // Check if cancelled during parallel processing
            if (isStopped) {
                return@withContext Result.failure(workDataOf(
                    KEY_ERROR_MESSAGE to "Scan cancelled"
                ))
            }

            // Save all newly computed hashes to database
            val newHashes = hashes.filter { hash ->
                val existing = existingHashes[hash.uri]
                existing == null || existing.algorithmVersion != CURRENT_ALGORITHM_VERSION
            }
            if (newHashes.isNotEmpty()) {
                // Save in batches to avoid transaction size limits
                newHashes.chunked(BATCH_SIZE).forEach { batch ->
                    photoHashDao.insertAll(batch)
                }
                Log.d(TAG, "Saved ${newHashes.size} new/updated hashes to database")
            }

            // Step 3: Find duplicates by comparing hashes
            setProgress(workDataOf(
                KEY_STATUS to STATUS_COMPARING,
                KEY_PROGRESS to 95
            ))
            try {
                setForeground(createForegroundInfo("Finding duplicates..."))
            } catch (e: Exception) { /* ignore notification errors */ }

            // Clear old duplicate groups before new scan
            duplicateGroupDao.deleteAll()

            // Convert to PhotoMetadata for enhanced multi-stage comparison
            val photoMetadata = hashes.map { hash ->
                ImageHasher.PhotoMetadata(
                    uri = hash.uri,
                    hash = hash.hash,
                    pHash = hash.pHash,
                    edgeHash = hash.edgeHash,
                    colorHistogram = ImageHasher.decodeHistogram(hash.colorHistogram),
                    width = hash.width,
                    height = hash.height,
                    fileSize = hash.fileSize,
                    dateAdded = hash.dateAdded  // Include timestamp for time-window clustering
                )
            }
            // Adaptive time-window clustering for efficient burst detection
            // Window size automatically adjusts based on photo density:
            // - Dense periods (>100/hr): 15-min windows
            // - Medium (>50/hr): 30-min windows
            // - Low (>20/hr): 1-hour windows
            // - Sparse: 2-hour windows
            // This reduces comparisons from O(n²) to O(sum of window²) - typically 50-100x faster
            val similarPairs = ImageHasher.findSimilarPairsWithAdaptiveWindows(photoMetadata)

            // Step 4: Group similar photos using Union-Find
            val groups = groupDuplicates(similarPairs, hashes)

            // Step 5: Save duplicate groups to database
            val now = System.currentTimeMillis()
            var totalDuplicates = 0

            for ((groupId, uris) in groups) {
                if (uris.size < 2) continue // Not a duplicate group

                val duplicateEntries = uris.mapIndexed { index, uri ->
                    DuplicateGroup(
                        groupId = groupId,
                        photoUri = uri,
                        similarityScore = 0, // Could compute actual similarity if needed
                        isKept = index == 0, // Mark first photo as kept by default
                        createdAt = now
                    )
                }
                duplicateGroupDao.insertAll(duplicateEntries)
                totalDuplicates += uris.size
            }

            // Complete
            setProgress(workDataOf(
                KEY_STATUS to STATUS_COMPLETE,
                KEY_PROGRESS to 100
            ))

            Result.success(workDataOf(
                KEY_GROUPS_FOUND to groups.size,
                KEY_DUPLICATES_FOUND to totalDuplicates
            ))

        } catch (e: Exception) {
            setProgress(workDataOf(
                KEY_STATUS to STATUS_ERROR,
                KEY_ERROR_MESSAGE to (e.message ?: "Unknown error")
            ))
            Result.failure(workDataOf(
                KEY_ERROR_MESSAGE to (e.message ?: "Unknown error")
            ))
        }
    }

    /**
     * Query all photos from MediaStore.
     */
    private fun queryAllPhotos(): List<PhotoInfo> {
        val photos = mutableListOf<PhotoInfo>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.ORIENTATION
        )

        applicationContext.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                val displayName = cursor.getString(displayNameColumn) ?: "unknown"
                val rawWidth = cursor.getInt(widthColumn)
                val rawHeight = cursor.getInt(heightColumn)
                val orientation = cursor.getInt(orientationColumn)

                // Calculate effective dimensions based on EXIF rotation
                // 90° and 270° rotations swap width and height
                val (effectiveWidth, effectiveHeight) = if (orientation == 90 || orientation == 270) {
                    rawHeight to rawWidth
                } else {
                    rawWidth to rawHeight
                }

                // Log photo info for debugging (helps identify test photos by name)
                val fileSize = cursor.getLong(sizeColumn)
                Log.d(TAG, "Photo: id=$id, name=$displayName, ${effectiveWidth}x${effectiveHeight}, size=${fileSize/1024}KB, orientation=$orientation")

                photos.add(PhotoInfo(
                    uri = uri,
                    displayName = displayName,
                    size = cursor.getLong(sizeColumn),
                    width = effectiveWidth,
                    height = effectiveHeight,
                    dateAdded = cursor.getLong(dateAddedColumn),
                    bucketId = cursor.getLong(bucketIdColumn),
                    bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"
                ))
            }
        }

        return photos
    }

    /**
     * Compute hashes for a single photo.
     *
     * This function is designed to be called in parallel. It handles:
     * - Checking cache for existing valid hashes
     * - Computing new hashes if needed
     * - Updating progress (thread-safe)
     *
     * @param photo Photo info from MediaStore
     * @param existingHashes Map of existing hashes by URI for cache lookup
     * @param progressCounter Atomic counter for thread-safe progress tracking
     * @param totalPhotos Total number of photos for progress calculation
     * @return PhotoHash if successful, null if computation failed
     */
    private suspend fun computeHashForPhoto(
        photo: PhotoInfo,
        existingHashes: Map<String, PhotoHash>,
        progressCounter: AtomicInteger,
        totalPhotos: Int
    ): PhotoHash? {
        // Check if we already have a valid hash for this photo
        val existingHash = existingHashes[photo.uri]
        if (existingHash != null && existingHash.algorithmVersion == CURRENT_ALGORITHM_VERSION) {
            Log.d(TAG, "Using cached hash: ${photo.uri.takeLast(20)} -> ${existingHash.hash}")
            updateProgress(progressCounter, totalPhotos)
            return existingHash
        }

        if (existingHash != null) {
            Log.d(TAG, "Recomputing hash for ${photo.uri.takeLast(30)} (version ${existingHash.algorithmVersion} -> $CURRENT_ALGORITHM_VERSION)")
        }

        // Compute ALL hashes using Android's thumbnail cache (FAST)
        // This is 10-50x faster than decoding full images
        val startTime = System.currentTimeMillis()
        val allHashes = ImageHasher.computeAllHashesFast(
            applicationContext,
            Uri.parse(photo.uri)
        )
        val hashTime = System.currentTimeMillis() - startTime

        if (allHashes == null) {
            Log.w(TAG, "Hash computation FAILED for: ${photo.uri}")
            updateProgress(progressCounter, totalPhotos)
            return null
        }

        val (dHash, pHash, edgeHash, colorHistogram) = allHashes
        if (hashTime > 200) {
            Log.d(TAG, "SLOW HASH (${hashTime}ms): ${photo.uri.takeLast(30)}")
        }
        Log.d(TAG, "Hashes computed: ${photo.uri.takeLast(20)} -> dHash=$dHash, pHash=$pHash, edgeHash=$edgeHash, histogram=${colorHistogram.size} bins")

        // Compute quality metrics
        val qualityStart = System.currentTimeMillis()
        val qualityBitmap = ImageHasher.loadBitmapForQuality(applicationContext, Uri.parse(photo.uri))
        val qualityResult = if (qualityBitmap != null) {
            try {
                QualityAnalyzer.analyze(qualityBitmap, photo.displayName)
            } finally {
                qualityBitmap.recycle()
            }
        } else {
            // Default quality result if bitmap loading fails
            QualityAnalyzer.QualityResult(
                sharpnessScore = 0.5f,
                exposureScore = 0.5f,
                noiseScore = 0.3f,
                overallQuality = 0.5f,
                issues = emptyList()
            )
        }
        val qualityTime = System.currentTimeMillis() - qualityStart
        if (qualityTime > 100) {
            Log.d(TAG, "SLOW QUALITY (${qualityTime}ms): ${photo.uri.takeLast(30)}")
        }
        if (qualityResult.hasIssues) {
            Log.d(TAG, "Quality issues: ${photo.uri.takeLast(20)} -> ${qualityResult.issuesString}, overall=${qualityResult.overallQuality}")
        }

        val photoHash = PhotoHash(
            uri = photo.uri,
            hash = dHash,
            pHash = pHash,
            edgeHash = edgeHash,
            colorHistogram = ImageHasher.encodeHistogram(colorHistogram),
            sharpnessScore = qualityResult.sharpnessScore,
            exposureScore = qualityResult.exposureScore,
            noiseScore = qualityResult.noiseScore,
            overallQuality = qualityResult.overallQuality,
            qualityIssues = qualityResult.issuesString,
            fileSize = photo.size,
            width = photo.width,
            height = photo.height,
            dateAdded = photo.dateAdded,
            lastScanned = System.currentTimeMillis(),
            bucketId = photo.bucketId,
            bucketName = photo.bucketName,
            algorithmVersion = CURRENT_ALGORITHM_VERSION
        )

        updateProgress(progressCounter, totalPhotos)
        return photoHash
    }

    /**
     * Update progress in a thread-safe manner.
     * Only updates UI every 10 photos to reduce overhead.
     */
    private suspend fun updateProgress(counter: AtomicInteger, total: Int) {
        val current = counter.incrementAndGet()

        // Update progress every 10 photos to reduce overhead
        if (current % 10 == 0 || current == total) {
            val progress = (current * 95) / total
            setProgress(workDataOf(
                KEY_STATUS to STATUS_HASHING,
                KEY_PROGRESS to progress,
                KEY_CURRENT to current,
                KEY_TOTAL to total
            ))
            try {
                setForeground(createForegroundInfo("Scanning photos: $current/$total"))
            } catch (e: Exception) { /* ignore notification errors */ }
        }

        // Periodic memory cleanup for large galleries
        if (current % 100 == 0) {
            System.gc()
        }
    }

    /**
     * Group similar photo pairs using representative-based clustering with group merging.
     *
     * When adding a photo to a group, it must match the group's representative.
     * When two groups could be merged (both photos already in different groups),
     * we merge them IF their representatives match each other.
     * This prevents "chaining" through weak intermediate links while still
     * allowing legitimate group merges.
     */
    private fun groupDuplicates(
        similarPairs: List<Pair<String, String>>,
        hashes: List<PhotoHash>
    ): Map<String, List<String>> {
        // Build hash lookup map for quick access
        val hashMap = hashes.associate { it.uri to it.hash }

        // Track which group each photo belongs to (uri -> groupId)
        val photoToGroup = mutableMapOf<String, String>()

        // Groups: groupId -> (representative uri, list of member uris)
        val groups = mutableMapOf<String, MutableList<String>>()

        for ((uri1, uri2) in similarPairs) {
            val group1 = photoToGroup[uri1]
            val group2 = photoToGroup[uri2]

            when {
                // Neither photo is in a group - create new group
                group1 == null && group2 == null -> {
                    val groupId = UUID.randomUUID().toString()
                    groups[groupId] = mutableListOf(uri1, uri2)
                    photoToGroup[uri1] = groupId
                    photoToGroup[uri2] = groupId
                }

                // uri1 is in a group, uri2 is not - check if uri2 matches representative
                group1 != null && group2 == null -> {
                    val representative = groups[group1]?.firstOrNull()
                    if (representative != null && matchesRepresentative(uri2, representative, hashMap)) {
                        groups[group1]?.add(uri2)
                        photoToGroup[uri2] = group1
                    }
                }

                // uri2 is in a group, uri1 is not - check if uri1 matches representative
                group1 == null && group2 != null -> {
                    val representative = groups[group2]?.firstOrNull()
                    if (representative != null && matchesRepresentative(uri1, representative, hashMap)) {
                        groups[group2]?.add(uri1)
                        photoToGroup[uri1] = group2
                    }
                }

                // Both are already in different groups - try to merge if representatives match
                group1 != null && group2 != null && group1 != group2 -> {
                    val rep1 = groups[group1]?.firstOrNull()
                    val rep2 = groups[group2]?.firstOrNull()

                    // Only merge if the representatives of both groups match each other
                    // This prevents chaining through weak intermediate links
                    if (rep1 != null && rep2 != null && matchesRepresentative(rep1, rep2, hashMap)) {
                        // Merge group2 into group1
                        val group2Members = groups[group2] ?: mutableListOf()
                        groups[group1]?.addAll(group2Members)

                        // Update all group2 members to point to group1
                        for (member in group2Members) {
                            photoToGroup[member] = group1
                        }

                        // Remove the old group
                        groups.remove(group2)
                    }
                }

                // Both in same group - nothing to do
            }
        }

        return groups
    }

    /**
     * Check if a photo matches a group's representative using hash comparison.
     * Uses a relaxed threshold since we're comparing representatives that were
     * already determined to be similar to other photos in their groups.
     */
    private fun matchesRepresentative(
        photoUri: String,
        representativeUri: String,
        hashMap: Map<String, Long>
    ): Boolean {
        val hash1 = hashMap[photoUri] ?: return false
        val hash2 = hashMap[representativeUri] ?: return false
        // Use a more relaxed threshold for representative matching to allow group merging
        return ImageHasher.hammingDistance(hash1, hash2) <= ImageHasher.DHASH_THRESHOLD + 10
    }

    /**
     * Create foreground notification for the scan.
     */
    private fun createForegroundInfo(message: String): ForegroundInfo {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Scanning for duplicates")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()

        // Android 14+ requires specifying the foreground service type
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Duplicate Scan",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of duplicate photo scanning"
            }

            val notificationManager = applicationContext.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Internal data class for photo information from MediaStore.
     */
    private data class PhotoInfo(
        val uri: String,
        val displayName: String,
        val size: Long,
        val width: Int,
        val height: Int,
        val dateAdded: Long,
        val bucketId: Long,
        val bucketName: String
    )
}
