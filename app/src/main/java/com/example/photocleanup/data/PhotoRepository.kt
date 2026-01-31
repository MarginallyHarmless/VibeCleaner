package com.example.photocleanup.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PhotoRepository(
    private val context: Context,
    private val reviewedPhotoDao: ReviewedPhotoDao
) {
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
        }.sortedByDescending { it.photoCount }
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
}
