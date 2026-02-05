package com.example.photocleanup.data

import android.content.IntentSender
import android.net.Uri

/**
 * Represents a pending delete request that requires user permission on Android 11+.
 */
data class PendingDeleteRequest(
    val uris: List<Uri>,
    val intentSender: IntentSender,
    val isDuplicates: Boolean = true
)
