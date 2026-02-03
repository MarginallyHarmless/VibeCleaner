package com.example.photocleanup.viewmodel

import android.app.Activity
import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photocleanup.PhotoCleanupApp
import com.example.photocleanup.data.DuplicateGroup
import com.example.photocleanup.data.DuplicateGroupWithPhotos
import com.example.photocleanup.data.DuplicatePhotoInfo
import com.example.photocleanup.data.DuplicateScanManager
import com.example.photocleanup.data.ScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Duplicates screen.
 * Manages scan state, duplicate groups, selection, and deletion operations.
 */
class DuplicatesViewModel(application: Application) : AndroidViewModel(application) {

    private val database = (application as PhotoCleanupApp).database
    private val photoHashDao = database.photoHashDao()
    private val duplicateGroupDao = database.duplicateGroupDao()
    private val scanManager = DuplicateScanManager(application)

    // Scan state
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // Selected photos for batch operations
    private val _selectedPhotoIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedPhotoIds: StateFlow<Set<Long>> = _selectedPhotoIds.asStateFlow()

    // Duplicate groups from database
    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroupWithPhotos>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroupWithPhotos>> = _duplicateGroups.asStateFlow()

    // Group count
    private val _groupCount = MutableStateFlow(0)
    val groupCount: StateFlow<Int> = _groupCount.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Pending delete request for Android 11+ permission flow
    private val _pendingDeleteRequest = MutableStateFlow<PendingDeleteRequest?>(null)
    val pendingDeleteRequest: StateFlow<PendingDeleteRequest?> = _pendingDeleteRequest.asStateFlow()

    init {
        // Clean up any stuck work from previous crashes
        scanManager.clearStuckWork()

        // Observe scan state from WorkManager
        viewModelScope.launch {
            scanManager.getScanState().collect { state ->
                _scanState.value = state

                // When scan completes, refresh the duplicate groups
                if (state is ScanState.Completed) {
                    loadDuplicateGroups()
                }
            }
        }

        // Load initial data
        loadDuplicateGroups()
    }

    /**
     * Start a new duplicate scan.
     */
    fun startScan() {
        scanManager.startScan()
    }

    /**
     * Cancel the current scan.
     */
    fun cancelScan() {
        scanManager.cancelScan()
    }

    /**
     * Load duplicate groups from database.
     */
    fun loadDuplicateGroups() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val groupIds = duplicateGroupDao.getAllGroupIds()
                val groups = groupIds.map { groupId ->
                    val entries = duplicateGroupDao.getByGroupId(groupId)
                    val photos = entries.map { entry ->
                        val hash = photoHashDao.getByUri(entry.photoUri)
                        DuplicatePhotoInfo(
                            id = entry.id,
                            uri = entry.photoUri,
                            similarityScore = entry.similarityScore,
                            isKept = entry.isKept,
                            fileSize = hash?.fileSize ?: 0,
                            width = hash?.width ?: 0,
                            height = hash?.height ?: 0,
                            bucketName = hash?.bucketName ?: ""
                        )
                    }
                    DuplicateGroupWithPhotos(
                        groupId = groupId,
                        photos = photos,
                        createdAt = entries.firstOrNull()?.createdAt ?: 0
                    )
                }.filter { it.photos.size >= 2 } // Only show groups with 2+ photos

                _duplicateGroups.value = groups
                _groupCount.value = groups.size
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Toggle selection of a photo for batch operations.
     */
    fun togglePhotoSelection(photoId: Long) {
        _selectedPhotoIds.value = _selectedPhotoIds.value.toMutableSet().apply {
            if (contains(photoId)) {
                remove(photoId)
            } else {
                add(photoId)
            }
        }
    }

    /**
     * Clear all selections.
     */
    fun clearSelection() {
        _selectedPhotoIds.value = emptySet()
    }

    /**
     * Select all photos that are NOT marked as kept.
     */
    fun selectAllUnkept() {
        viewModelScope.launch {
            val unkeptPhotos = duplicateGroupDao.getUnkeptPhotos()
            _selectedPhotoIds.value = unkeptPhotos.map { it.id }.toSet()
        }
    }

    /**
     * Mark a photo as the one to keep in its group.
     */
    fun markAsKept(groupId: String, photoId: Long) {
        viewModelScope.launch {
            duplicateGroupDao.setKeptPhoto(groupId, photoId)
            loadDuplicateGroups()
        }
    }

    /**
     * Delete selected photos.
     * On Android 11+, this will trigger a permission request.
     */
    fun deleteSelectedPhotos(activity: Activity) {
        viewModelScope.launch {
            val selectedIds = _selectedPhotoIds.value.toList()
            if (selectedIds.isEmpty()) return@launch

            // Get URIs for selected photos
            val uris = mutableListOf<Uri>()
            for (group in _duplicateGroups.value) {
                for (photo in group.photos) {
                    if (photo.id in selectedIds) {
                        uris.add(Uri.parse(photo.uri))
                    }
                }
            }

            if (uris.isEmpty()) return@launch

            deletePhotos(activity, uris)
        }
    }

    /**
     * Delete photos that are not marked as kept in a specific group.
     */
    fun deleteUnkeptInGroup(activity: Activity, groupId: String) {
        viewModelScope.launch {
            val unkeptPhotos = duplicateGroupDao.getUnkeptPhotosInGroup(groupId)
            if (unkeptPhotos.isEmpty()) return@launch

            val uris = unkeptPhotos.map { Uri.parse(it.photoUri) }
            deletePhotos(activity, uris)
        }
    }

    /**
     * Delete specified photos from the device.
     */
    private suspend fun deletePhotos(activity: Activity, uris: List<Uri>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: Use MediaStore.createDeleteRequest for batch deletion
            try {
                val intentSender = MediaStore.createDeleteRequest(
                    activity.contentResolver,
                    uris
                ).intentSender

                _pendingDeleteRequest.value = PendingDeleteRequest(
                    uris = uris,
                    intentSender = intentSender
                )
            } catch (e: Exception) {
                // Fall back to individual deletion
                deletePhotosLegacy(uris)
            }
        } else {
            // Android 10 and below: Direct deletion
            deletePhotosLegacy(uris)
        }
    }

    /**
     * Legacy photo deletion for Android 10 and below.
     */
    private suspend fun deletePhotosLegacy(uris: List<Uri>) {
        val context = getApplication<Application>()
        var deletedCount = 0

        for (uri in uris) {
            try {
                val result = context.contentResolver.delete(uri, null, null)
                if (result > 0) {
                    deletedCount++
                    // Remove from database
                    duplicateGroupDao.deleteByUri(uri.toString())
                    photoHashDao.deleteByUri(uri.toString())
                }
            } catch (e: Exception) {
                // Continue with other deletions
            }
        }

        // Refresh data and clear selection
        loadDuplicateGroups()
        clearSelection()
    }

    /**
     * Called when the delete permission request completes.
     */
    fun onDeletePermissionResult(granted: Boolean) {
        viewModelScope.launch {
            val request = _pendingDeleteRequest.value ?: return@launch
            _pendingDeleteRequest.value = null

            if (granted) {
                // User approved deletion, remove from database
                val uriStrings = request.uris.map { it.toString() }
                duplicateGroupDao.deleteByUris(uriStrings)
                photoHashDao.deleteByUris(uriStrings)

                // Refresh data and clear selection
                loadDuplicateGroups()
                clearSelection()
            }
        }
    }

    /**
     * Clear scan results and reset state.
     */
    fun clearResults() {
        viewModelScope.launch {
            duplicateGroupDao.deleteAll()
            _duplicateGroups.value = emptyList()
            _groupCount.value = 0
            _selectedPhotoIds.value = emptySet()
            _scanState.value = ScanState.Idle
        }
    }
}

/**
 * Represents a pending delete request that requires user permission.
 */
data class PendingDeleteRequest(
    val uris: List<Uri>,
    val intentSender: android.content.IntentSender
)
