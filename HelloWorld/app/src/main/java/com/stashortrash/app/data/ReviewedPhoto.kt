package com.stashortrash.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reviewed_photos")
data class ReviewedPhoto(
    @PrimaryKey val uri: String,
    val reviewedAt: Long,
    val action: String, // "keep" or "deleted"
    val fileSize: Long = 0L // File size in bytes (for space recovered stats)
)
