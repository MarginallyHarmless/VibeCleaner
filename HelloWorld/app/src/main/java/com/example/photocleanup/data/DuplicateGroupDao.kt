package com.example.photocleanup.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for DuplicateGroup entities.
 * Provides methods to manage duplicate photo groups.
 */
@Dao
interface DuplicateGroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(duplicateGroup: DuplicateGroup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(duplicateGroups: List<DuplicateGroup>)

    @Query("SELECT * FROM duplicate_groups WHERE groupId = :groupId ORDER BY similarityScore ASC")
    suspend fun getByGroupId(groupId: String): List<DuplicateGroup>

    @Query("SELECT * FROM duplicate_groups ORDER BY createdAt DESC, groupId, similarityScore ASC")
    fun getAllAsFlow(): Flow<List<DuplicateGroup>>

    @Query("SELECT DISTINCT groupId FROM duplicate_groups ORDER BY createdAt DESC")
    fun getAllGroupIdsAsFlow(): Flow<List<String>>

    @Query("SELECT DISTINCT groupId FROM duplicate_groups")
    suspend fun getAllGroupIds(): List<String>

    @Query("SELECT COUNT(DISTINCT groupId) FROM duplicate_groups")
    suspend fun getGroupCount(): Int

    @Query("SELECT COUNT(DISTINCT groupId) FROM duplicate_groups")
    fun getGroupCountAsFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM duplicate_groups")
    suspend fun getTotalPhotoCount(): Int

    @Query("UPDATE duplicate_groups SET isKept = 0 WHERE groupId = :groupId")
    suspend fun clearKeptInGroup(groupId: String)

    @Query("UPDATE duplicate_groups SET isKept = 1 WHERE id = :id")
    suspend fun markAsKept(id: Long)

    /**
     * Mark a specific photo as kept, clearing any other kept status in the same group.
     */
    @Transaction
    suspend fun setKeptPhoto(groupId: String, photoId: Long) {
        clearKeptInGroup(groupId)
        markAsKept(photoId)
    }

    @Query("DELETE FROM duplicate_groups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM duplicate_groups WHERE photoUri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM duplicate_groups WHERE photoUri IN (:uris)")
    suspend fun deleteByUris(uris: List<String>)

    @Query("DELETE FROM duplicate_groups WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)

    @Query("DELETE FROM duplicate_groups")
    suspend fun deleteAll()

    /**
     * Get all photos that are NOT marked as kept (candidates for deletion).
     */
    @Query("SELECT * FROM duplicate_groups WHERE isKept = 0")
    suspend fun getUnkeptPhotos(): List<DuplicateGroup>

    /**
     * Get photos not marked as kept within a specific group.
     */
    @Query("SELECT * FROM duplicate_groups WHERE groupId = :groupId AND isKept = 0")
    suspend fun getUnkeptPhotosInGroup(groupId: String): List<DuplicateGroup>

    /**
     * Check if a photo URI exists in any duplicate group.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM duplicate_groups WHERE photoUri = :uri)")
    suspend fun existsByUri(uri: String): Boolean

    // ==================== Stats Queries ====================

    /**
     * Count extra copies in duplicate groups (total photos - number of groups).
     * Each group has 1 "original" so the rest are extra copies.
     */
    @Query("SELECT COUNT(*) - COUNT(DISTINCT groupId) FROM duplicate_groups")
    suspend fun getExtraCopiesCount(): Int
}
