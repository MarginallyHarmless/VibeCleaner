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

    @Query("DELETE FROM reviewed_photos WHERE uri = :uri")
    suspend fun deleteReviewByUri(uri: String)

    @Query("SELECT * FROM reviewed_photos WHERE action = 'to_delete'")
    fun getPhotosToDelete(): Flow<List<ReviewedPhoto>>

    @Query("SELECT COUNT(*) FROM reviewed_photos WHERE action = 'to_delete'")
    fun getToDeleteCount(): Flow<Int>

    @Query("UPDATE reviewed_photos SET action = 'deleted' WHERE uri IN (:uris)")
    suspend fun markAsDeleted(uris: List<String>)
}
