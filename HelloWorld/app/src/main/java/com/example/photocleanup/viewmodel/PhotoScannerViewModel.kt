package com.example.photocleanup.viewmodel

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photocleanup.PhotoCleanupApp
import com.example.photocleanup.data.DuplicateGroupWithPhotos
import com.example.photocleanup.data.DuplicatePhotoInfo
import com.example.photocleanup.data.DuplicateScanManager
import com.example.photocleanup.data.PendingDeleteRequest
import com.example.photocleanup.data.PhotoHash
import com.example.photocleanup.data.PhotoScannerTab
import com.example.photocleanup.data.ScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Combined ViewModel for the Photo Scanner screen.
 * Manages both duplicate detection and low quality photo functionality.
 */
class PhotoScannerViewModel(application: Application) : AndroidViewModel(application) {
    val isPremium: Boolean get() = (getApplication<PhotoCleanupApp>()).appPreferences.isPremium

    private val database = (application as PhotoCleanupApp).database
    private val photoHashDao = database.photoHashDao()
    private val duplicateGroupDao = database.duplicateGroupDao()
    private val scanManager = DuplicateScanManager(application)

    // Current tab selection
    private val _selectedTab = MutableStateFlow(PhotoScannerTab.DUPLICATES)
    val selectedTab: StateFlow<PhotoScannerTab> = _selectedTab.asStateFlow()

    // Scan state (shared between both tabs)
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ==================== Duplicates State ====================

    // Duplicate groups from database
    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroupWithPhotos>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroupWithPhotos>> = _duplicateGroups.asStateFlow()

    // Selected photo IDs for duplicates (by database ID)
    private val _selectedPhotoIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedPhotoIds: StateFlow<Set<Long>> = _selectedPhotoIds.asStateFlow()

    // Group count
    private val _groupCount = MutableStateFlow(0)
    val groupCount: StateFlow<Int> = _groupCount.asStateFlow()

    // Pending delete request for Android 11+ permission flow (duplicates)
    private val _pendingDeleteRequest = MutableStateFlow<PendingDeleteRequest?>(null)
    val pendingDeleteRequest: StateFlow<PendingDeleteRequest?> = _pendingDeleteRequest.asStateFlow()

    // ==================== Low Quality State ====================

    // Low quality photos from database
    private val _lowQualityPhotos = MutableStateFlow<List<PhotoHash>>(emptyList())
    val lowQualityPhotos: StateFlow<List<PhotoHash>> = _lowQualityPhotos.asStateFlow()

    // Selected URIs for low quality (by URI string)
    private val _selectedLowQualityUris = MutableStateFlow<Set<String>>(emptySet())
    val selectedLowQualityUris: StateFlow<Set<String>> = _selectedLowQualityUris.asStateFlow()

    // Low quality count
    private val _lowQualityCount = MutableStateFlow(0)
    val lowQualityCount: StateFlow<Int> = _lowQualityCount.asStateFlow()

    init {
        // Clean up any stuck work from previous crashes
        scanManager.clearStuckWork()

        // Observe scan state from WorkManager
        viewModelScope.launch {
            scanManager.getScanState().collect { state ->
                _scanState.value = state

                // When scan completes, refresh both data sources
                if (state is ScanState.Completed) {
                    loadDuplicateGroups()
                    loadLowQualityPhotosOnce()
                }
            }
        }

        // Load initial data
        loadDuplicateGroups()
        loadLowQualityPhotos()
    }

    // ==================== Tab Management ====================

    fun selectTab(tab: PhotoScannerTab) {
        _selectedTab.value = tab
    }

    // ==================== Scan Management ====================

    fun startScan() {
        scanManager.startScan()
    }

    fun cancelScan() {
        scanManager.cancelScan()
    }

    // ==================== Duplicates Functions ====================

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
                            bucketName = hash?.bucketName ?: "",
                            dateAdded = hash?.dateAdded ?: 0
                        )
                    }
                    DuplicateGroupWithPhotos(
                        groupId = groupId,
                        photos = photos,
                        createdAt = entries.firstOrNull()?.createdAt ?: 0
                    )
                }.filter { it.photos.size >= 2 }
                    .sortedByDescending { group ->
                        group.photos.maxOfOrNull { it.dateAdded } ?: 0L
                    }

                _duplicateGroups.value = groups
                _groupCount.value = groups.size
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun togglePhotoSelection(photoId: Long) {
        _selectedPhotoIds.value = _selectedPhotoIds.value.toMutableSet().apply {
            if (contains(photoId)) {
                remove(photoId)
            } else {
                add(photoId)
            }
        }
    }

    fun clearDuplicateSelection() {
        _selectedPhotoIds.value = emptySet()
    }

    fun markAsKept(groupId: String, photoId: Long) {
        viewModelScope.launch {
            duplicateGroupDao.setKeptPhoto(groupId, photoId)
            _duplicateGroups.value = _duplicateGroups.value.map { group ->
                if (group.groupId == groupId) {
                    group.copy(
                        photos = group.photos.map { photo ->
                            photo.copy(isKept = photo.id == photoId)
                        }
                    )
                } else {
                    group
                }
            }
        }
    }

    fun deleteSelectedDuplicates(activity: Activity) {
        viewModelScope.launch {
            val selectedIds = _selectedPhotoIds.value.toList()
            if (selectedIds.isEmpty()) return@launch

            val uris = mutableListOf<Uri>()
            for (group in _duplicateGroups.value) {
                for (photo in group.photos) {
                    if (photo.id in selectedIds) {
                        uris.add(Uri.parse(photo.uri))
                    }
                }
            }

            if (uris.isEmpty()) return@launch

            deletePhotos(activity, uris, isDuplicates = true)
        }
    }

    fun onDeletePermissionResult(granted: Boolean) {
        viewModelScope.launch {
            val request = _pendingDeleteRequest.value ?: return@launch
            _pendingDeleteRequest.value = null

            if (granted) {
                val uriStrings = request.uris.map { it.toString() }
                duplicateGroupDao.deleteByUris(uriStrings)
                photoHashDao.deleteByUris(uriStrings)

                loadDuplicateGroups()
                clearDuplicateSelection()
            }
        }
    }

    // ==================== Low Quality Functions ====================

    private fun loadLowQualityPhotos() {
        viewModelScope.launch {
            photoHashDao.getLowQualityPhotos().collect { photos ->
                _lowQualityPhotos.value = photos
                _lowQualityCount.value = photos.size
            }
        }
    }

    private suspend fun loadLowQualityPhotosOnce() {
        val photos = photoHashDao.getLowQualityPhotosOnce()
        _lowQualityPhotos.value = photos
        _lowQualityCount.value = photos.size
    }

    fun toggleLowQualitySelection(uri: String) {
        _selectedLowQualityUris.value = if (uri in _selectedLowQualityUris.value) {
            _selectedLowQualityUris.value - uri
        } else {
            _selectedLowQualityUris.value + uri
        }
    }

    fun clearLowQualitySelection() {
        _selectedLowQualityUris.value = emptySet()
    }

    fun selectAllLowQuality() {
        _selectedLowQualityUris.value = _lowQualityPhotos.value.map { it.uri }.toSet()
    }

    fun deleteLowQualityPhotos(activity: Activity) {
        viewModelScope.launch {
            val uris = _selectedLowQualityUris.value.map { Uri.parse(it) }
            if (uris.isEmpty()) return@launch

            deletePhotos(activity, uris, isDuplicates = false)
        }
    }

    fun onLowQualityPhotosDeleted(deletedUris: List<Uri>) {
        viewModelScope.launch {
            deletedUris.forEach { uri ->
                photoHashDao.deleteByUri(uri.toString())
            }
            _selectedLowQualityUris.value = _selectedLowQualityUris.value - deletedUris.map { it.toString() }.toSet()
        }
    }

    // ==================== Shared Delete Logic ====================

    private suspend fun deletePhotos(activity: Activity, uris: List<Uri>, isDuplicates: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intentSender = MediaStore.createDeleteRequest(
                    activity.contentResolver,
                    uris
                ).intentSender

                _pendingDeleteRequest.value = PendingDeleteRequest(
                    uris = uris,
                    intentSender = intentSender,
                    isDuplicates = isDuplicates
                )
            } catch (e: Exception) {
                deletePhotosLegacy(uris, isDuplicates)
            }
        } else {
            deletePhotosLegacy(uris, isDuplicates)
        }
    }

    private suspend fun deletePhotosLegacy(uris: List<Uri>, isDuplicates: Boolean) {
        val context = getApplication<Application>()

        for (uri in uris) {
            try {
                val result = context.contentResolver.delete(uri, null, null)
                if (result > 0) {
                    if (isDuplicates) {
                        duplicateGroupDao.deleteByUri(uri.toString())
                    }
                    photoHashDao.deleteByUri(uri.toString())
                }
            } catch (e: Exception) {
                // Continue with other deletions
            }
        }

        if (isDuplicates) {
            loadDuplicateGroups()
            clearDuplicateSelection()
        } else {
            clearLowQualitySelection()
        }
    }
}
