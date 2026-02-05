package com.example.photocleanup.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing duplicate photo groups.
 * Each entry represents a photo that belongs to a group of similar images.
 *
 * Photos are grouped by a unique groupId (UUID), and each photo in the group
 * has a similarity score indicating how closely it matches other photos in the group.
 *
 * The isKept flag allows users to mark which photo they want to keep from each group,
 * making it easier to batch-delete the remaining duplicates.
 */
@Entity(
    tableName = "duplicate_groups",
    indices = [Index(value = ["groupId"])]
)
data class DuplicateGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: String,          // UUID identifying the duplicate group
    val photoUri: String,         // Content URI of the photo
    val similarityScore: Int,     // Hamming distance (0 = identical, lower = more similar)
    val isKept: Boolean = false,  // User marked this as the photo to keep
    val createdAt: Long           // When this group was detected
)

/**
 * Data class for displaying a duplicate group in the UI.
 * Contains all photos in a group along with computed metadata.
 */
data class DuplicateGroupWithPhotos(
    val groupId: String,
    val photos: List<DuplicatePhotoInfo>,
    val createdAt: Long
) {
    val photoCount: Int get() = photos.size
    val keptPhotoUri: String? get() = photos.find { it.isKept }?.uri
}

/**
 * Information about a single photo within a duplicate group.
 */
data class DuplicatePhotoInfo(
    val id: Long,
    val uri: String,
    val similarityScore: Int,
    val isKept: Boolean,
    val fileSize: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val bucketName: String = "",
    val dateAdded: Long = 0
)
