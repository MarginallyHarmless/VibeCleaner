package com.example.photocleanup.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing perceptual hashes of images.
 *
 * Two hash types are stored for two-stage duplicate detection:
 * - dHash (Difference Hash): Fast 64-bit fingerprint based on gradient differences
 * - pHash (Perceptual Hash): More discriminative 64-bit fingerprint using DCT
 *
 * The two-stage approach uses dHash for fast candidate selection (permissive threshold)
 * and pHash for strict confirmation (reduces false positives).
 */
@Entity(
    tableName = "photo_hashes",
    indices = [Index(value = ["hash"])]
)
data class PhotoHash(
    @PrimaryKey
    val uri: String,              // Content URI of the photo
    val hash: Long,               // 64-bit dHash value (fast first-pass)
    val pHash: Long = 0,          // 64-bit pHash value (DCT-based, for confirmation)
    val fileSize: Long,           // File size in bytes
    val width: Int,               // Image width in pixels (effective, after EXIF rotation)
    val height: Int,              // Image height in pixels (effective, after EXIF rotation)
    val dateAdded: Long,          // When the photo was added to the device
    val lastScanned: Long,        // When we last computed the hash
    val bucketId: Long,           // MediaStore bucket ID (album)
    val bucketName: String,       // Album/folder name
    val algorithmVersion: Int = 0 // Version of hash algorithm (for cache invalidation)
)
