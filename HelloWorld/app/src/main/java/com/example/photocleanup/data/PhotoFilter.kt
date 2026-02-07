package com.example.photocleanup.data

import android.content.IntentSender
import android.net.Uri

data class PhotoFilter(
    val selectedFolders: Set<String> = emptySet(),  // Empty = all folders
    val dateRange: DateRangeFilter = DateRangeFilter.ALL
)

enum class DateRangeFilter {
    ALL,
    LAST_WEEK,
    LAST_MONTH,
    LAST_3_MONTHS,
    LAST_YEAR
}

data class FolderInfo(
    val bucketId: Long,
    val displayName: String,
    val photoCount: Int
)

sealed class MoveResult {
    data class Success(val newUri: Uri) : MoveResult()
    data class RequiresPermission(val intentSender: IntentSender, val targetAlbum: String) : MoveResult()
    data class Error(val message: String) : MoveResult()
    data object AlreadyInAlbum : MoveResult()
}

sealed class FavouriteResult {
    data object Success : FavouriteResult()
    data class RequiresPermission(val intentSender: IntentSender) : FavouriteResult()
    data object Unsupported : FavouriteResult()
}

// Menu screen data classes

enum class MenuMode {
    BY_DATE,
    BY_ALBUM
}

data class MonthGroup(
    val year: Int,
    val month: Int,           // 0-11 (Calendar.JANUARY = 0)
    val displayName: String,  // "January 2024"
    val totalCount: Int,
    val reviewedCount: Int
) {
    val unreviewedCount: Int get() = totalCount - reviewedCount
}

data class AlbumGroup(
    val bucketId: Long,
    val displayName: String,
    val totalCount: Int,
    val reviewedCount: Int
) {
    val unreviewedCount: Int get() = totalCount - reviewedCount
}

data class AllMediaStats(
    val totalCount: Int,
    val reviewedCount: Int
) {
    val unreviewedCount: Int get() = totalCount - reviewedCount
}

data class MenuFilter(
    val mode: MenuMode,
    val year: Int? = null,
    val month: Int? = null,
    val albumBucketId: Long? = null,
    val isAllMedia: Boolean = false,
    val displayTitle: String = ""  // For showing in the header (e.g., "January 2024" or "Camera")
)
