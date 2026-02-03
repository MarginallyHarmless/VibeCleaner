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
}
