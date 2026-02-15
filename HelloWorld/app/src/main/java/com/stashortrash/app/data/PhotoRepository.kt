package com.stashortrash.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

class PhotoRepository(
    private val context: Context,
    private val reviewedPhotoDao: ReviewedPhotoDao,
    private val includeVideos: Boolean = false
) {
    companion object {
        private const val TAG = "PhotoRepository"
    }

    /**
     * Check if app has full storage access (MANAGE_EXTERNAL_STORAGE permission on Android 11+).
     * When granted, photo moves don't require per-file permission dialogs.
     */
    fun hasFullStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // On Android 10 and below, we don't need this permission
            true
        }
    }

    // ==================== Multi-volume helpers ====================

    /**
     * Get content URIs for images across all mounted volumes (internal + SD cards).
     * On API 29+ this discovers SD cards via getExternalVolumeNames().
     */
    private fun getImageUris(): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val volumes = MediaStore.getExternalVolumeNames(context)
            Log.d(TAG, "Discovered volumes: $volumes")
            volumes.map { volume ->
                MediaStore.Images.Media.getContentUri(volume)
            }
        } else {
            listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }
    }

    /**
     * Get content URIs for videos across all mounted volumes (internal + SD cards).
     */
    private fun getVideoUris(): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context).map { volume ->
                MediaStore.Video.Media.getContentUri(volume)
            }
        } else {
            listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        }
    }

    // ==================== Dual-query helpers ====================

    /**
     * Query a single MediaStore content URI and return (dateAdded, MediaItem) pairs.
     */
    private fun querySingleStore(
        contentUri: Uri,
        isVideo: Boolean,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null
    ): List<Pair<Long, MediaItem>> {
        val results = mutableListOf<Pair<Long, MediaItem>>()
        val baseProjection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE
        )
        if (isVideo) {
            baseProjection.add(MediaStore.Video.Media.DURATION)
        }

        context.contentResolver.query(
            contentUri,
            baseProjection.toTypedArray(),
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val durationColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val fileSize = cursor.getLong(sizeColumn)
                val duration = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
                val uri = ContentUris.withAppendedId(contentUri, id)
                results.add(dateAdded to MediaItem(uri, isVideo, duration, fileSize))
            }
        }
        return results
    }

    /**
     * Query both image and (optionally) video stores across all volumes,
     * merge and sort by DATE_ADDED DESC.
     */
    private fun queryMediaItems(
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): List<MediaItem> {
        val results = mutableListOf<Pair<Long, MediaItem>>()

        // Query images from all volumes (internal + SD cards)
        for (uri in getImageUris()) {
            results.addAll(querySingleStore(uri, false, selection, selectionArgs))
        }

        // Query videos from all volumes if unlocked
        if (includeVideos) {
            for (uri in getVideoUris()) {
                results.addAll(querySingleStore(uri, true, selection, selectionArgs))
            }
        }

        return results.sortedByDescending { it.first }.map { it.second }
    }

    // ==================== Load methods (return MediaItem) ====================

    suspend fun loadAllPhotos(): List<MediaItem> = withContext(Dispatchers.IO) {
        queryMediaItems()
    }

    suspend fun loadFilteredPhotos(filter: PhotoFilter): List<MediaItem> = withContext(Dispatchers.IO) {
        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        // Folder filter
        if (filter.selectedFolders.isNotEmpty()) {
            val placeholders = filter.selectedFolders.joinToString(",") { "?" }
            selectionParts.add("${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} IN ($placeholders)")
            selectionArgs.addAll(filter.selectedFolders)
        }

        // Date range filter
        if (filter.dateRange != DateRangeFilter.ALL) {
            val minTimestamp = getMinTimestampForDateRange(filter.dateRange)
            selectionParts.add("${MediaStore.MediaColumns.DATE_ADDED} >= ?")
            selectionArgs.add(minTimestamp.toString())
        }

        val selection = if (selectionParts.isNotEmpty()) {
            selectionParts.joinToString(" AND ")
        } else null

        val args = if (selectionArgs.isNotEmpty()) {
            selectionArgs.toTypedArray()
        } else null

        queryMediaItems(selection, args)
    }

    private fun getMinTimestampForDateRange(dateRange: DateRangeFilter): Long {
        val now = System.currentTimeMillis() / 1000  // MediaStore uses seconds
        return when (dateRange) {
            DateRangeFilter.ALL -> 0L
            DateRangeFilter.LAST_WEEK -> now - TimeUnit.DAYS.toSeconds(7)
            DateRangeFilter.LAST_MONTH -> now - TimeUnit.DAYS.toSeconds(30)
            DateRangeFilter.LAST_3_MONTHS -> now - TimeUnit.DAYS.toSeconds(90)
            DateRangeFilter.LAST_YEAR -> now - TimeUnit.DAYS.toSeconds(365)
        }
    }

    suspend fun getUnreviewedPhotos(): List<MediaItem> = withContext(Dispatchers.IO) {
        val allPhotos = loadAllPhotos()
        val reviewedUris = reviewedPhotoDao.getAllReviewedUris().toSet()
        allPhotos.filter { it.uri.toString() !in reviewedUris }
    }

    suspend fun getReviewedUris(): Set<String> = withContext(Dispatchers.IO) {
        reviewedPhotoDao.getAllReviewedUris().toSet()
    }

    suspend fun markAsReviewed(uri: Uri, action: String, fileSize: Long = 0L) {
        reviewedPhotoDao.insertReviewedPhoto(
            ReviewedPhoto(
                uri = uri.toString(),
                reviewedAt = System.currentTimeMillis(),
                action = action,
                fileSize = fileSize
            )
        )
    }

    suspend fun resetAllReviews() {
        reviewedPhotoDao.deleteAllReviews()
    }

    suspend fun removeReview(uri: Uri) {
        reviewedPhotoDao.deleteReviewByUri(uri.toString())
    }

    fun getPhotosToDelete(): Flow<List<ReviewedPhoto>> {
        return reviewedPhotoDao.getPhotosToDelete()
    }

    fun getToDeleteCount(): Flow<Int> {
        return reviewedPhotoDao.getToDeleteCount()
    }

    suspend fun markPhotosAsDeleted(uris: List<Uri>) {
        reviewedPhotoDao.markAsDeleted(uris.map { it.toString() })
    }

    suspend fun restorePhoto(uri: Uri) {
        reviewedPhotoDao.deleteReviewByUri(uri.toString())
    }

    suspend fun getPhotoAlbumInfo(uri: Uri): FolderInfo? = withContext(Dispatchers.IO) {
        // BUCKET_ID and BUCKET_DISPLAY_NAME are the same column name for both images and videos
        val projection = arrayOf(
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
        )

        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val bucketId = cursor.getLong(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"
                FolderInfo(
                    bucketId = bucketId,
                    displayName = bucketName,
                    photoCount = 0  // Not needed for single photo lookup
                )
            } else {
                null
            }
        }
    }

    suspend fun movePhotoToAlbum(photoUri: Uri, targetAlbumName: String): MoveResult = withContext(Dispatchers.IO) {
        try {
            // First, check if photo is already in the target album
            val currentAlbum = getPhotoAlbumInfo(photoUri)
            if (currentAlbum?.displayName == targetAlbumName) {
                return@withContext MoveResult.AlreadyInAlbum
            }

            // Get the target album's relative path
            val targetRelativePath = getAlbumRelativePath(targetAlbumName)
                ?: return@withContext MoveResult.Error("Could not find target album path")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+: Check if we have full storage access
                if (Environment.isExternalStorageManager()) {
                    // We have full access - move directly without permission dialog
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath)
                    }
                    val rowsUpdated = context.contentResolver.update(photoUri, values, null, null)
                    return@withContext if (rowsUpdated > 0) {
                        MoveResult.Success(photoUri)
                    } else {
                        MoveResult.Error("Failed to move photo")
                    }
                } else {
                    // Fall back to permission request
                    val writeRequest = MediaStore.createWriteRequest(
                        context.contentResolver,
                        listOf(photoUri)
                    )
                    return@withContext MoveResult.RequiresPermission(writeRequest.intentSender, targetAlbumName)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10: Update RELATIVE_PATH directly
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath)
                }
                val rowsUpdated = context.contentResolver.update(photoUri, values, null, null)
                if (rowsUpdated > 0) {
                    return@withContext MoveResult.Success(photoUri)
                } else {
                    return@withContext MoveResult.Error("Failed to move photo")
                }
            } else {
                // Android 9 and below: Direct file move
                return@withContext movePhotoLegacy(photoUri, targetAlbumName)
            }
        } catch (e: Exception) {
            return@withContext MoveResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun completeMoveAfterPermission(photoUri: Uri, targetAlbumName: String): MoveResult = withContext(Dispatchers.IO) {
        try {
            val targetRelativePath = getAlbumRelativePath(targetAlbumName)
                ?: return@withContext MoveResult.Error("Could not find target album path")

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath)
            }
            val rowsUpdated = context.contentResolver.update(photoUri, values, null, null)
            if (rowsUpdated > 0) {
                return@withContext MoveResult.Success(photoUri)
            } else {
                return@withContext MoveResult.Error("Failed to move photo")
            }
        } catch (e: Exception) {
            return@withContext MoveResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun getAlbumRelativePath(albumName: String): String? {
        // Query MediaStore to find an existing item in the target album to get its relative path
        // Try images first, then videos — across all volumes
        val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
        val selection = "${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(albumName)

        for (uri in getImageUris()) {
            context.contentResolver.query(
                uri, projection, selection, selectionArgs, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val pathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    if (pathColumn >= 0) {
                        return cursor.getString(pathColumn)
                    }
                }
            }
        }

        // Also check videos if unlocked (album might only contain videos)
        if (includeVideos) {
            for (uri in getVideoUris()) {
                context.contentResolver.query(
                    uri, projection, selection, selectionArgs, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val pathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        if (pathColumn >= 0) {
                            return cursor.getString(pathColumn)
                        }
                    }
                }
            }
        }

        // If no existing items in album, construct path based on common patterns
        return when {
            albumName.equals("Camera", ignoreCase = true) -> "${Environment.DIRECTORY_DCIM}/Camera"
            albumName.equals("Screenshots", ignoreCase = true) -> "${Environment.DIRECTORY_PICTURES}/Screenshots"
            albumName.equals("Download", ignoreCase = true) || albumName.equals("Downloads", ignoreCase = true) -> Environment.DIRECTORY_DOWNLOADS
            else -> "${Environment.DIRECTORY_PICTURES}/$albumName"
        }
    }

    private fun movePhotoLegacy(photoUri: Uri, targetAlbumName: String): MoveResult {
        // Get the source file path
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        var sourcePath: String? = null

        context.contentResolver.query(photoUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (dataColumn >= 0) {
                    sourcePath = cursor.getString(dataColumn)
                }
            }
        }

        if (sourcePath == null) {
            return MoveResult.Error("Could not find source file")
        }

        val sourceFile = File(sourcePath!!)
        if (!sourceFile.exists()) {
            return MoveResult.Error("Source file does not exist")
        }

        // Determine target directory
        val targetDir = when {
            targetAlbumName.equals("Camera", ignoreCase = true) ->
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
            targetAlbumName.equals("Screenshots", ignoreCase = true) ->
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots")
            else ->
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), targetAlbumName)
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val targetFile = File(targetDir, sourceFile.name)

        return try {
            sourceFile.copyTo(targetFile, overwrite = true)
            sourceFile.delete()

            // Delete old entry from MediaStore
            context.contentResolver.delete(photoUri, null, null)

            // Scan new file to add to MediaStore — detect MIME type from the URI
            val mimeType = context.contentResolver.getType(photoUri) ?: "image/jpeg"
            val isVideo = mimeType.startsWith("video/")
            val storeUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                           else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATA, targetFile.absolutePath)
                put(MediaStore.MediaColumns.DISPLAY_NAME, targetFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            }
            val newUri = context.contentResolver.insert(storeUri, values)

            if (newUri != null) {
                MoveResult.Success(newUri)
            } else {
                MoveResult.Success(photoUri) // Return original URI as fallback
            }
        } catch (e: Exception) {
            MoveResult.Error(e.message ?: "Failed to move file")
        }
    }

    // ==================== Menu Screen Methods ====================

    /**
     * Helper: collect URIs from a content URI, grouped by bucket.
     */
    private fun collectFolderCounts(
        contentUri: Uri,
        folders: MutableMap<Long, Pair<String, Int>>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH
        )

        context.contentResolver.query(
            contentUri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val relativePathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val bucketId = cursor.getLong(bucketIdColumn)
                var bucketName = cursor.getString(bucketNameColumn)

                // If bucket name is null, empty, or looks like a bucket ID (all digits),
                // try to extract folder name from relative path
                if (bucketName.isNullOrBlank() || bucketName.all { it.isDigit() }) {
                    if (relativePathColumn >= 0) {
                        val relativePath = cursor.getString(relativePathColumn)
                        bucketName = relativePath
                            ?.trimEnd('/')
                            ?.substringAfterLast('/')
                            ?.takeIf { it.isNotBlank() && !it.all { c -> c.isDigit() } }
                    }
                }

                // Skip folders that still have invalid names (numeric-only or blank)
                if (bucketName.isNullOrBlank() || bucketName.all { it.isDigit() }) {
                    continue
                }

                val existing = folders[bucketId]
                if (existing != null) {
                    folders[bucketId] = existing.first to (existing.second + 1)
                } else {
                    folders[bucketId] = bucketName to 1
                }
            }
        }
    }

    suspend fun getAvailableFolders(): List<FolderInfo> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<Long, Pair<String, Int>>()

        for (uri in getImageUris()) {
            collectFolderCounts(uri, folders)
        }
        if (includeVideos) {
            for (uri in getVideoUris()) {
                collectFolderCounts(uri, folders)
            }
        }

        folders.map { (id, pair) ->
            FolderInfo(
                bucketId = id,
                displayName = pair.first,
                photoCount = pair.second
            )
        }.sortedBy { it.displayName.lowercase() }  // Alphabetical for stable order
    }

    /**
     * Helper: collect URIs grouped by month from a content URI.
     */
    private fun collectMonthUris(
        contentUri: Uri,
        monthCounts: MutableMap<Pair<Int, Int>, MutableList<String>>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        context.contentResolver.query(
            contentUri,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val calendar = Calendar.getInstance()

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val uri = ContentUris.withAppendedId(contentUri, id).toString()

                calendar.timeInMillis = dateAdded * 1000
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)

                val key = year to month
                monthCounts.getOrPut(key) { mutableListOf() }.add(uri)
            }
        }
    }

    /**
     * Get photos grouped by month with review counts.
     * Only returns months that have unreviewed photos.
     */
    suspend fun getPhotosByMonth(): List<MonthGroup> = withContext(Dispatchers.IO) {
        val monthCounts = mutableMapOf<Pair<Int, Int>, MutableList<String>>()

        for (uri in getImageUris()) {
            collectMonthUris(uri, monthCounts)
        }
        if (includeVideos) {
            for (uri in getVideoUris()) {
                collectMonthUris(uri, monthCounts)
            }
        }

        val reviewedUris = reviewedPhotoDao.getAllReviewedUris().toSet()
        val monthNames = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )

        monthCounts.mapNotNull { (yearMonth, uris) ->
            val (year, month) = yearMonth
            val totalCount = uris.size
            val reviewedCount = uris.count { it in reviewedUris }
            val unreviewedCount = totalCount - reviewedCount

            // Only include months with unreviewed photos
            if (unreviewedCount > 0) {
                MonthGroup(
                    year = year,
                    month = month,
                    displayName = "${monthNames[month]} $year",
                    totalCount = totalCount,
                    reviewedCount = reviewedCount
                )
            } else {
                null
            }
        }.sortedWith(compareByDescending<MonthGroup> { it.year }.thenByDescending { it.month })
    }

    /**
     * Helper: collect URIs grouped by album from a content URI.
     */
    private fun collectAlbumUris(
        contentUri: Uri,
        albumCounts: MutableMap<Long, Pair<String, MutableList<String>>>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH
        )

        context.contentResolver.query(
            contentUri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val relativePathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val photoId = cursor.getLong(idColumn)
                val bucketId = cursor.getLong(bucketIdColumn)
                var bucketName = cursor.getString(bucketNameColumn)

                if (bucketName.isNullOrBlank() || bucketName.all { it.isDigit() }) {
                    if (relativePathColumn >= 0) {
                        val relativePath = cursor.getString(relativePathColumn)
                        bucketName = relativePath
                            ?.trimEnd('/')
                            ?.substringAfterLast('/')
                            ?.takeIf { it.isNotBlank() && !it.all { c -> c.isDigit() } }
                    }
                }

                if (bucketName.isNullOrBlank() || bucketName.all { it.isDigit() }) {
                    continue
                }

                val uri = ContentUris.withAppendedId(contentUri, photoId).toString()

                val existing = albumCounts[bucketId]
                if (existing != null) {
                    existing.second.add(uri)
                } else {
                    albumCounts[bucketId] = bucketName to mutableListOf(uri)
                }
            }
        }
    }

    /**
     * Get photos grouped by album with review counts.
     * Only returns albums that have unreviewed photos.
     */
    suspend fun getPhotosByAlbum(): List<AlbumGroup> = withContext(Dispatchers.IO) {
        val albumCounts = mutableMapOf<Long, Pair<String, MutableList<String>>>()

        for (uri in getImageUris()) {
            collectAlbumUris(uri, albumCounts)
        }
        if (includeVideos) {
            for (uri in getVideoUris()) {
                collectAlbumUris(uri, albumCounts)
            }
        }

        val reviewedUris = reviewedPhotoDao.getAllReviewedUris().toSet()

        albumCounts.mapNotNull { (bucketId, nameAndUris) ->
            val (name, uris) = nameAndUris
            val totalCount = uris.size
            val reviewedCount = uris.count { it in reviewedUris }
            val unreviewedCount = totalCount - reviewedCount

            // Only include albums with unreviewed photos
            if (unreviewedCount > 0) {
                AlbumGroup(
                    bucketId = bucketId,
                    displayName = name,
                    totalCount = totalCount,
                    reviewedCount = reviewedCount
                )
            } else {
                null
            }
        }.sortedBy { it.displayName.lowercase() }
    }

    /**
     * Get stats for all media on the device.
     * Returns null if there are no photos or all are reviewed.
     */
    suspend fun getAllMediaStats(): AllMediaStats? = withContext(Dispatchers.IO) {
        val uris = mutableListOf<String>()
        val projection = arrayOf(MediaStore.MediaColumns._ID)

        // Images from all volumes
        for (contentUri in getImageUris()) {
            context.contentResolver.query(
                contentUri, projection, null, null, null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val uri = ContentUris.withAppendedId(contentUri, id).toString()
                    uris.add(uri)
                }
            }
        }

        // Videos from all volumes (if unlocked)
        if (includeVideos) {
            for (contentUri in getVideoUris()) {
                context.contentResolver.query(
                    contentUri, projection, null, null, null
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val uri = ContentUris.withAppendedId(contentUri, id).toString()
                        uris.add(uri)
                    }
                }
            }
        }

        if (uris.isEmpty()) return@withContext null

        val reviewedUris = reviewedPhotoDao.getAllReviewedUris().toSet()
        val reviewedCount = uris.count { it in reviewedUris }
        val unreviewedCount = uris.size - reviewedCount

        // Only return if there are unreviewed photos
        if (unreviewedCount > 0) {
            AllMediaStats(
                totalCount = uris.size,
                reviewedCount = reviewedCount
            )
        } else {
            null
        }
    }

    /**
     * Load photos for a specific month (unreviewed only).
     */
    suspend fun loadPhotosByMonth(year: Int, month: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<Pair<Long, MediaItem>>()

        fun collectFromStore(contentUri: Uri, isVideo: Boolean) {
            val baseProjection = mutableListOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.SIZE
            )
            if (isVideo) baseProjection.add(MediaStore.Video.Media.DURATION)

            context.contentResolver.query(
                contentUri,
                baseProjection.toTypedArray(),
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val durationColumn = if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1
                val calendar = Calendar.getInstance()

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val dateAdded = cursor.getLong(dateColumn)
                    val fileSize = cursor.getLong(sizeColumn)

                    calendar.timeInMillis = dateAdded * 1000
                    val photoYear = calendar.get(Calendar.YEAR)
                    val photoMonth = calendar.get(Calendar.MONTH)

                    if (photoYear == year && photoMonth == month) {
                        val duration = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
                        val uri = ContentUris.withAppendedId(contentUri, id)
                        items.add(dateAdded to MediaItem(uri, isVideo, duration, fileSize))
                    }
                }
            }
        }

        for (uri in getImageUris()) {
            collectFromStore(uri, false)
        }
        if (includeVideos) {
            for (uri in getVideoUris()) {
                collectFromStore(uri, true)
            }
        }

        // Sort by date, filter reviewed
        val reviewedUris = reviewedPhotoDao.getAllReviewedUris().toSet()
        items.sortedByDescending { it.first }
            .map { it.second }
            .filter { it.uri.toString() !in reviewedUris }
    }

    /**
     * Load photos for a specific album (unreviewed only).
     */
    suspend fun loadPhotosByAlbumId(bucketId: Long): List<MediaItem> = withContext(Dispatchers.IO) {
        val selection = "${MediaStore.MediaColumns.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId.toString())
        val allItems = queryMediaItems(selection, selectionArgs)

        // Filter out reviewed
        val reviewedUris = reviewedPhotoDao.getAllReviewedUris().toSet()
        allItems.filter { it.uri.toString() !in reviewedUris }
    }

    /**
     * Load all media (unreviewed only).
     */
    suspend fun loadAllMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val allItems = queryMediaItems()

        // Filter out reviewed
        val reviewedUris = reviewedPhotoDao.getAllReviewedUris().toSet()
        allItems.filter { it.uri.toString() !in reviewedUris }
    }

    /**
     * Get the URI of the most recently added media item (for hero card thumbnail).
     */
    suspend fun getMostRecentMediaUri(): Uri? = withContext(Dispatchers.IO) {
        // Query all image volumes and find the most recent
        val allImageItems = getImageUris().flatMap { uri ->
            querySingleStore(uri, isVideo = false,
                sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC")
        }
        val result = allImageItems.maxByOrNull { it.first }?.second?.uri
        if (result != null) return@withContext result

        // Fall back to video if no images
        if (includeVideos) {
            val allVideoItems = getVideoUris().flatMap { uri ->
                querySingleStore(uri, isVideo = true,
                    sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC")
            }
            allVideoItems.maxByOrNull { it.first }?.second?.uri
        } else null
    }

    /**
     * Get a random unreviewed media URI (for Random hero card thumbnail).
     */
    suspend fun getRandomThumbnailUri(): Uri? = withContext(Dispatchers.IO) {
        val allItems = queryMediaItems()
        val reviewedUris = reviewedPhotoDao.getAllReviewedUris().toSet()
        val unreviewed = allItems.filter { it.uri.toString() !in reviewedUris }
        unreviewed.randomOrNull()?.uri
    }
}
