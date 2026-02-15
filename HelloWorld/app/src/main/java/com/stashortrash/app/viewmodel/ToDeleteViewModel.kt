package com.stashortrash.app.viewmodel

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stashortrash.app.PhotoCleanupApp
import com.stashortrash.app.data.PhotoRepository
import com.stashortrash.app.data.ReviewedPhoto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ToDeleteUiState(
    val photos: List<ReviewedPhoto> = emptyList(),
    val selectedUris: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val deleteIntentSender: IntentSender? = null
)

class ToDeleteViewModel(application: Application) : AndroidViewModel(application) {
    private val database = (application as PhotoCleanupApp).database
    private val repository = PhotoRepository(application, database.reviewedPhotoDao())

    private val _uiState = MutableStateFlow(ToDeleteUiState())
    val uiState: StateFlow<ToDeleteUiState> = _uiState.asStateFlow()

    private var pendingDeleteUris: List<Uri> = emptyList()

    init {
        viewModelScope.launch {
            repository.getPhotosToDelete().collect { photos ->
                _uiState.value = _uiState.value.copy(
                    photos = photos,
                    isLoading = false,
                    selectedUris = _uiState.value.selectedUris.filter { uri ->
                        photos.any { it.uri == uri }
                    }.toSet()
                )
            }
        }
    }

    fun toggleSelection(uri: String) {
        val currentSelected = _uiState.value.selectedUris
        _uiState.value = _uiState.value.copy(
            selectedUris = if (uri in currentSelected) {
                currentSelected - uri
            } else {
                currentSelected + uri
            }
        )
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedUris = _uiState.value.photos.map { it.uri }.toSet()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedUris = emptySet())
    }

    fun restoreSelected() {
        val selectedUris = _uiState.value.selectedUris.toList()
        viewModelScope.launch {
            selectedUris.forEach { uriString ->
                repository.restorePhoto(Uri.parse(uriString))
            }
            _uiState.value = _uiState.value.copy(selectedUris = emptySet())
        }
    }

    fun requestBulkDelete(): IntentSender? {
        val selectedUris = _uiState.value.selectedUris.map { Uri.parse(it) }
        if (selectedUris.isEmpty()) return null

        val context = getApplication<PhotoCleanupApp>()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val deleteRequest = MediaStore.createDeleteRequest(
                context.contentResolver,
                selectedUris
            )
            pendingDeleteUris = selectedUris
            _uiState.value = _uiState.value.copy(
                deleteIntentSender = deleteRequest.intentSender
            )
            deleteRequest.intentSender
        } else {
            // For Android 10 and below, delete directly
            viewModelScope.launch {
                selectedUris.forEach { uri ->
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        // Photo might already be deleted or protected
                    }
                }
                repository.markPhotosAsDeleted(selectedUris)
                _uiState.value = _uiState.value.copy(selectedUris = emptySet())
            }
            null
        }
    }

    fun onDeleteConfirmed(success: Boolean) {
        if (success && pendingDeleteUris.isNotEmpty()) {
            viewModelScope.launch {
                repository.markPhotosAsDeleted(pendingDeleteUris)
                pendingDeleteUris = emptyList()
                _uiState.value = _uiState.value.copy(
                    selectedUris = emptySet(),
                    deleteIntentSender = null
                )
            }
        } else {
            pendingDeleteUris = emptyList()
            _uiState.value = _uiState.value.copy(deleteIntentSender = null)
        }
    }
}
