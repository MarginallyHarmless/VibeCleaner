package com.example.photocleanup.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for PhotoHash entities.
 * Provides methods to insert, query, and manage photo hash records.
 */
@Dao
interface PhotoHashDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photoHash: PhotoHash)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photoHashes: List<PhotoHash>)

    @Query("SELECT * FROM photo_hashes WHERE uri = :uri")
    suspend fun getByUri(uri: String): PhotoHash?

    @Query("SELECT * FROM photo_hashes ORDER BY hash")
    suspend fun getAllSortedByHash(): List<PhotoHash>

    @Query("SELECT * FROM photo_hashes")
    fun getAllAsFlow(): Flow<List<PhotoHash>>

    @Query("SELECT COUNT(*) FROM photo_hashes")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM photo_hashes")
    fun getCountAsFlow(): Flow<Int>

    @Query("DELETE FROM photo_hashes WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM photo_hashes WHERE uri IN (:uris)")
    suspend fun deleteByUris(uris: List<String>)

    @Query("DELETE FROM photo_hashes")
    suspend fun deleteAll()

    /**
     * Check if a photo has been scanned after a certain time.
     * Useful for determining if we need to re-scan.
     */
    @Query("SELECT uri FROM photo_hashes WHERE lastScanned > :since")
    suspend fun getUrisScannedSince(since: Long): List<String>

    /**
     * Get all unique URIs that have been hashed.
     * Used to check which photos are already processed.
     */
    @Query("SELECT uri FROM photo_hashes")
    suspend fun getAllUris(): List<String>

    // ==================== Low Quality Photo Queries ====================

    /**
     * Get all photos that have quality issues, sorted by worst quality first.
     * Returns a Flow for reactive updates when quality data changes.
     * Photos with qualityIssues != '' have at least one detected issue.
     */
    @Query("SELECT * FROM photo_hashes WHERE qualityIssues != '' ORDER BY overallQuality ASC")
    fun getLowQualityPhotos(): Flow<List<PhotoHash>>

    /**
     * Get the count of photos with quality issues.
     * Returns a Flow for reactive updates (e.g., for displaying badge counts).
     */
    @Query("SELECT COUNT(*) FROM photo_hashes WHERE qualityIssues != ''")
    fun getLowQualityCount(): Flow<Int>

    /**
     * One-shot query to get all low quality photos.
     * Useful for non-reactive contexts where you just need the current list once.
     * Sorted by worst quality first (lowest overallQuality score).
     */
    @Query("SELECT * FROM photo_hashes WHERE qualityIssues != '' ORDER BY overallQuality ASC")
    suspend fun getLowQualityPhotosOnce(): List<PhotoHash>
}
