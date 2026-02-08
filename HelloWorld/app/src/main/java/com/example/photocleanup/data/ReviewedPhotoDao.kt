package com.example.photocleanup.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewedPhotoDao {
    @Query("SELECT * FROM reviewed_photos")
    fun getAllReviewedPhotos(): Flow<List<ReviewedPhoto>>

    @Query("SELECT uri FROM reviewed_photos")
    suspend fun getAllReviewedUris(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewedPhoto(photo: ReviewedPhoto)

    @Query("DELETE FROM reviewed_photos")
    suspend fun deleteAllReviews()

    @Query("SELECT COUNT(*) FROM reviewed_photos")
    suspend fun getReviewedCount(): Int

    @Query("SELECT COUNT(*) FROM reviewed_photos")
    fun getReviewedCountAsFlow(): Flow<Int>

    @Query("DELETE FROM reviewed_photos WHERE uri = :uri")
    suspend fun deleteReviewByUri(uri: String)

    @Query("SELECT * FROM reviewed_photos WHERE action = 'to_delete'")
    fun getPhotosToDelete(): Flow<List<ReviewedPhoto>>

    @Query("SELECT COUNT(*) FROM reviewed_photos WHERE action = 'to_delete'")
    fun getToDeleteCount(): Flow<Int>

    @Query("UPDATE reviewed_photos SET action = 'deleted' WHERE uri IN (:uris)")
    suspend fun markAsDeleted(uris: List<String>)

    // ==================== Stats Queries ====================

    @Query("SELECT COUNT(*) FROM reviewed_photos WHERE action = 'deleted'")
    suspend fun getDeletedCount(): Int

    @Query("SELECT COUNT(*) FROM reviewed_photos WHERE action = 'keep'")
    suspend fun getKeptCount(): Int

    @Query("SELECT strftime('%Y-%m', reviewedAt / 1000, 'unixepoch') AS month, action, COUNT(*) AS count FROM reviewed_photos GROUP BY month, action ORDER BY month")
    suspend fun getReviewsByMonth(): List<MonthActionCount>

    @Query("SELECT date(reviewedAt / 1000, 'unixepoch') AS day, COUNT(*) AS count FROM reviewed_photos GROUP BY day ORDER BY count DESC LIMIT 1")
    suspend fun getBusiestDay(): DayCount?

    @Query("SELECT reviewedAt FROM reviewed_photos ORDER BY reviewedAt")
    suspend fun getAllReviewTimestamps(): List<Long>

    @Query("SELECT MIN(reviewedAt) FROM reviewed_photos")
    suspend fun getEarliestReviewDate(): Long?

    @Query("SELECT date(reviewedAt / 1000, 'unixepoch') AS day, COUNT(*) AS count FROM reviewed_photos GROUP BY day ORDER BY day")
    suspend fun getReviewedCountByDay(): List<DayCount>

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM reviewed_photos WHERE action = 'deleted'")
    suspend fun getDeletedFilesSizeSum(): Long

    @Query("""
        SELECT strftime('%Y-%m', reviewedAt / 1000, 'unixepoch') AS month,
               COALESCE(SUM(fileSize), 0) AS bytes
        FROM reviewed_photos
        WHERE action = 'deleted'
        GROUP BY month
        ORDER BY month
    """)
    suspend fun getDeletedFilesSizeByMonth(): List<MonthBytes>

    @Query("SELECT fileSize, reviewedAt AS dateAdded FROM reviewed_photos WHERE action = 'deleted' AND fileSize > 0 ORDER BY fileSize DESC LIMIT 1")
    suspend fun getLargestDeletedFile(): LargestFile?
}

data class MonthActionCount(
    val month: String,
    val action: String,
    val count: Int
)

data class DayCount(
    val day: String,
    val count: Int
)
