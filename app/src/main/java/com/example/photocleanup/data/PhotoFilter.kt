package com.example.photocleanup.data

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
