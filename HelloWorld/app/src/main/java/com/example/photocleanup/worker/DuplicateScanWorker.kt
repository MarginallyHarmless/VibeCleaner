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
import coil.ImageLoader
import coil.request.CachePolicy
import com.example.photocleanup.R
import com.example.photocleanup.data.DuplicateGroup
import com.example.photocleanup.data.PhotoDatabase
import com.example.photocleanup.data.PhotoHash
import com.example.photocleanup.util.ImageHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

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
        const val CURRENT_ALGORITHM_VERSION = 5

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

            // Step 2: Compute hashes for all photos
            val imageLoader = ImageLoader.Builder(applicationContext)
                .crossfade(false)
                .memoryCachePolicy(CachePolicy.DISABLED) // Disable memory cache to save RAM
                .build()

            val hashes = mutableListOf<PhotoHash>()
            val existingUris = photoHashDao.getAllUris().toSet()
            val batchToSave = mutableListOf<PhotoHash>()

            for ((index, photo) in photos.withIndex()) {
                // Check for cancellation
                if (isStopped) {
                    return@withContext Result.failure(workDataOf(
                        KEY_ERROR_MESSAGE to "Scan cancelled"
                    ))
                }

                // Check if we already have a valid hash for this photo
                if (photo.uri in existingUris) {
                    val existingHash = photoHashDao.getByUri(photo.uri)
                    // Only reuse hash if algorithm version matches current version
                    if (existingHash != null && existingHash.algorithmVersion == CURRENT_ALGORITHM_VERSION) {
                        Log.d(TAG, "Using cached hash: ${photo.uri.takeLast(20)} -> ${existingHash.hash}")
                        hashes.add(existingHash)
                        continue
                    }
                    // Old version hash - will recompute below
                    if (existingHash != null) {
                        Log.d(TAG, "Recomputing hash for ${photo.uri.takeLast(30)} (version ${existingHash.algorithmVersion} -> $CURRENT_ALGORITHM_VERSION)")
                    }
                }

                // Compute dHash
                val dHash = ImageHasher.computeHash(
                    applicationContext,
                    Uri.parse(photo.uri),
                    imageLoader
                )

                // Compute pHash, edge hash, AND color histogram together (shares bitmap loading)
                val allHashes = ImageHasher.computeAllHashes(
                    applicationContext,
                    Uri.parse(photo.uri),
                    imageLoader
                )

                if (dHash != null && allHashes != null) {
                    val (pHash, edgeHash, colorHistogram) = allHashes
                    Log.d(TAG, "Hashes computed: ${photo.uri.takeLast(20)} -> dHash=$dHash, pHash=$pHash, edgeHash=$edgeHash, histogram=${colorHistogram.size} bins")
                    val photoHash = PhotoHash(
                        uri = photo.uri,
                        hash = dHash,
                        pHash = pHash,
                        edgeHash = edgeHash,
                        colorHistogram = ImageHasher.encodeHistogram(colorHistogram),
                        fileSize = photo.size,
                        width = photo.width,
                        height = photo.height,
                        dateAdded = photo.dateAdded,
                        lastScanned = System.currentTimeMillis(),
                        bucketId = photo.bucketId,
                        bucketName = photo.bucketName,
                        algorithmVersion = CURRENT_ALGORITHM_VERSION
                    )
                    hashes.add(photoHash)
                    batchToSave.add(photoHash)

                    // Save hashes in batches and clear batch list
                    if (batchToSave.size >= BATCH_SIZE) {
                        photoHashDao.insertAll(batchToSave)
                        batchToSave.clear()
                    }
                } else {
                    Log.w(TAG, "Hash computation FAILED for: ${photo.uri} (dHash=${dHash != null}, allHashes=${allHashes != null})")
                }

                // Update progress (hashing is 0-95%, comparison is quick so we reserve 95-100%)
                val progress = ((index + 1) * 95) / photos.size
                setProgress(workDataOf(
                    KEY_STATUS to STATUS_HASHING,
                    KEY_PROGRESS to progress,
                    KEY_CURRENT to (index + 1),
                    KEY_TOTAL to photos.size
                ))
                try {
                    setForeground(createForegroundInfo("Scanning photos: ${index + 1}/${photos.size}"))
                } catch (e: Exception) { /* ignore notification errors */ }

                // Periodic memory cleanup for large galleries
                if (index % 100 == 0 && index > 0) {
                    System.gc()
                }
            }

            // Save any remaining hashes
            if (batchToSave.isNotEmpty()) {
                photoHashDao.insertAll(batchToSave)
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
            // Time-window clustering for efficient burst detection
            // Only compares photos within 5-minute windows, with relaxed thresholds
            // This reduces comparisons from O(n²) to O(sum of window²) - typically 50-100x faster
            val similarPairs = ImageHasher.findSimilarPairsWithTimeWindows(photoMetadata)

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
        val size: Long,
        val width: Int,
        val height: Int,
        val dateAdded: Long,
        val bucketId: Long,
        val bucketName: String
    )
}
