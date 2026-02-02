package com.example.photocleanup.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class PhotoRepository(
    private val context: Context,
    private val reviewedPhotoDao: ReviewedPhotoDao
) {
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
    suspend fun loadAllPhotos(): List<Uri> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                photos.add(uri)
            }
        }
        photos
    }

    suspend fun getAvailableFolders(): List<FolderInfo> = withContext(Dispatchers.IO) {
        val folders = mutableMapOf<Long, Pair<String, Int>>()
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val bucketId = cursor.getLong(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn) ?: "Unknown"

                val existing = folders[bucketId]
                if (existing != null) {
                    folders[bucketId] = existing.first to (existing.second + 1)
                } else {
                    folders[bucketId] = bucketName to 1
                }
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

    suspend fun loadFilteredPhotos(filter: PhotoFilter): List<Uri> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        // Folder filter
        if (filter.selectedFolders.isNotEmpty()) {
            val placeholders = filter.selectedFolders.joinToString(",") { "?" }
            selectionParts.add("${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} IN ($placeholders)")
            selectionArgs.addAll(filter.selectedFolders)
        }

        // Date range filter
        if (filter.dateRange != DateRangeFilter.ALL) {
            val minTimestamp = getMinTimestampForDateRange(filter.dateRange)
            selectionParts.add("${MediaStore.Images.Media.DATE_ADDED} >= ?")
            selectionArgs.add(minTimestamp.toString())
        }

        val selection = if (selectionParts.isNotEmpty()) {
            selectionParts.joinToString(" AND ")
        } else null

        val args = if (selectionArgs.isNotEmpty()) {
            selectionArgs.toTypedArray()
        } else null

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                photos.add(uri)
            }
        }
        photos
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

    suspend fun getUnreviewedPhotos(): List<Uri> = withContext(Dispatchers.IO) {
        val allPhotos = loadAllPhotos()
        val reviewedUris = reviewedPhotoDao.getAllReviewedUris().toSet()
        allPhotos.filter { it.toString() !in reviewedUris }
    }

    suspend fun getReviewedUris(): Set<String> = withContext(Dispatchers.IO) {
        reviewedPhotoDao.getAllReviewedUris().toSet()
    }

    suspend fun markAsReviewed(uri: Uri, action: String) {
        reviewedPhotoDao.insertReviewedPhoto(
            ReviewedPhoto(
                uri = uri.toString(),
                reviewedAt = System.currentTimeMillis(),
                action = action
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
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
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
                        put(MediaStore.Images.Media.RELATIVE_PATH, targetRelativePath)
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
                    put(MediaStore.Images.Media.RELATIVE_PATH, targetRelativePath)
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
                put(MediaStore.Images.Media.RELATIVE_PATH, targetRelativePath)
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
        // Query MediaStore to find an existing photo in the target album to get its relative path
        val projection = arrayOf(
            MediaStore.Images.Media.RELATIVE_PATH
        )
        val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(albumName)

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val pathColumn = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                if (pathColumn >= 0) {
                    return cursor.getString(pathColumn)
                }
            }
        }

        // If no existing photos in album, construct path based on common patterns
        return when {
            albumName.equals("Camera", ignoreCase = true) -> "${Environment.DIRECTORY_DCIM}/Camera"
            albumName.equals("Screenshots", ignoreCase = true) -> "${Environment.DIRECTORY_PICTURES}/Screenshots"
            albumName.equals("Download", ignoreCase = true) || albumName.equals("Downloads", ignoreCase = true) -> Environment.DIRECTORY_DOWNLOADS
            else -> "${Environment.DIRECTORY_PICTURES}/$albumName"
        }
    }

    private fun movePhotoLegacy(photoUri: Uri, targetAlbumName: String): MoveResult {
        // Get the source file path
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        var sourcePath: String? = null

        context.contentResolver.query(photoUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
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

            // Scan new file to add to MediaStore
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, targetFile.absolutePath)
                put(MediaStore.Images.Media.DISPLAY_NAME, targetFile.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            val newUri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )

            if (newUri != null) {
                MoveResult.Success(newUri)
            } else {
                MoveResult.Success(photoUri) // Return original URI as fallback
            }
        } catch (e: Exception) {
            MoveResult.Error(e.message ?: "Failed to move file")
        }
    }
}
