package com.example.photocleanup.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photocleanup.PhotoCleanupApp
import com.example.photocleanup.data.DuplicateScanManager
import com.example.photocleanup.data.PhotoHash
import com.example.photocleanup.data.ScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Low Quality Photos screen.
 * Manages scan state observation, low quality photo list, selection, and preview state.
 */
class LowQualityViewModel(application: Application) : AndroidViewModel(application) {

    private val database = (application as PhotoCleanupApp).database
    private val photoHashDao = database.photoHashDao()
    private val scanManager = DuplicateScanManager(application)

    // Scan state (reuses the same scan as duplicates)
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // Low quality photos from database
    private val _lowQualityPhotos = MutableStateFlow<List<PhotoHash>>(emptyList())
    val lowQualityPhotos: StateFlow<List<PhotoHash>> = _lowQualityPhotos.asStateFlow()

    // Selection state for batch operations
    private val _selectedUris = MutableStateFlow<Set<String>>(emptySet())
    val selectedUris: StateFlow<Set<String>> = _selectedUris.asStateFlow()

    // Count of low quality photos
    private val _lowQualityCount = MutableStateFlow(0)
    val lowQualityCount: StateFlow<Int> = _lowQualityCount.asStateFlow()

    // Fullscreen preview URI
    private val _previewUri = MutableStateFlow<Uri?>(null)
    val previewUri: StateFlow<Uri?> = _previewUri.asStateFlow()

    init {
        observeScanState()
        loadLowQualityPhotos()
    }

    /**
     * Observe scan state from WorkManager via DuplicateScanManager.
     * When scan completes, refresh the low quality photos list.
     */
    private fun observeScanState() {
        viewModelScope.launch {
            scanManager.getScanState().collect { state ->
                _scanState.value = state

                // When scan completes, refresh the low quality photos
                if (state is ScanState.Completed) {
                    loadLowQualityPhotosOnce()
                }
            }
        }
    }

    /**
     * Load low quality photos from database as a Flow (reactive updates).
     */
    private fun loadLowQualityPhotos() {
        viewModelScope.launch {
            photoHashDao.getLowQualityPhotos().collect { photos ->
                _lowQualityPhotos.value = photos
                _lowQualityCount.value = photos.size
            }
        }
    }

    /**
     * Load low quality photos once (non-reactive, for refresh after scan).
     */
    private suspend fun loadLowQualityPhotosOnce() {
        val photos = photoHashDao.getLowQualityPhotosOnce()
        _lowQualityPhotos.value = photos
        _lowQualityCount.value = photos.size
    }

    /**
     * Toggle selection of a photo URI.
     */
    fun toggleSelection(uri: String) {
        _selectedUris.value = if (uri in _selectedUris.value) {
            _selectedUris.value - uri
        } else {
            _selectedUris.value + uri
        }
    }

    /**
     * Clear all selections.
     */
    fun clearSelection() {
        _selectedUris.value = emptySet()
    }

    /**
     * Select all low quality photos.
     */
    fun selectAll() {
        _selectedUris.value = _lowQualityPhotos.value.map { it.uri }.toSet()
    }

    /**
     * Show fullscreen preview for a photo.
     */
    fun showPreview(uri: Uri) {
        _previewUri.value = uri
    }

    /**
     * Hide fullscreen preview.
     */
    fun hidePreview() {
        _previewUri.value = null
    }

    /**
     * Get the count of currently selected photos.
     */
    fun getSelectedCount(): Int = _selectedUris.value.size

    /**
     * Start a duplicate/quality scan.
     * Uses the same scan worker as duplicates (it computes quality during scan).
     */
    fun startScan() {
        scanManager.startScan()
    }

    /**
     * Cancel any running scan.
     */
    fun cancelScan() {
        scanManager.cancelScan()
    }

    /**
     * Called after photos have been deleted (via system delete dialog).
     * Removes deleted URIs from database and clears selection.
     */
    fun onPhotosDeleted(deletedUris: List<Uri>) {
        viewModelScope.launch {
            // Remove from database
            deletedUris.forEach { uri ->
                photoHashDao.deleteByUri(uri.toString())
            }
            // Clear selection for deleted URIs
            _selectedUris.value = _selectedUris.value - deletedUris.map { it.toString() }.toSet()
        }
    }
}
