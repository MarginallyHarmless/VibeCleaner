package com.example.photocleanup.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.IntentSender
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photocleanup.PhotoCleanupApp
import com.example.photocleanup.data.DateRangeFilter
import com.example.photocleanup.data.FolderInfo
import com.example.photocleanup.data.MenuFilter
import com.example.photocleanup.data.MoveResult
import com.example.photocleanup.data.PhotoFilter
import com.example.photocleanup.data.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UndoableAction(
    val photo: Uri,
    val action: String,
    val previousIndex: Int
)

data class PhotoUiState(
    val photos: List<Uri> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = true,
    val totalPhotosCount: Int = 0,
    val reviewedCount: Int = 0,
    val pendingDeleteUri: Uri? = null,
    val deleteIntentSender: IntentSender? = null,
    val lastAction: UndoableAction? = null,
    val toDeleteCount: Int = 0,
    val filter: PhotoFilter = PhotoFilter(),
    val availableFolders: List<FolderInfo> = emptyList(),
    val isLoadingFolders: Boolean = false,
    val showCelebration: Boolean = true,
    // Album selector state
    val currentPhotoAlbum: FolderInfo? = null,
    val isMovingPhoto: Boolean = false,
    val moveIntentSender: IntentSender? = null,
    val pendingMoveAlbum: String? = null,
    val pendingMoveSourceAlbumId: Long? = null,
    val moveError: String? = null,
    // Menu filter state
    val menuFilter: MenuFilter? = null,
    // Favourite state
    val isCurrentPhotoFavourite: Boolean = false
) {
    val currentPhoto: Uri? get() = photos.getOrNull(currentIndex)
    val nextPhoto: Uri? get() = photos.getOrNull(currentIndex + 1)
    val hasPhotos: Boolean get() = photos.isNotEmpty()
    val isAllDone: Boolean get() = !isLoading && photos.isEmpty()
    val hasActiveFilters: Boolean get() = filter.selectedFolders.isNotEmpty() || filter.dateRange != DateRangeFilter.ALL
    val menuFilterTitle: String get() = menuFilter?.displayTitle ?: ""
}

class PhotoViewModel(application: Application) : AndroidViewModel(application) {
    private val database = (application as PhotoCleanupApp).database
    private val repository = PhotoRepository(application, database.reviewedPhotoDao())

    private val _uiState = MutableStateFlow(PhotoUiState())
    val uiState: StateFlow<PhotoUiState> = _uiState.asStateFlow()

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            // Don't reload while a move operation is in progress or pending permission
            // This prevents the photo from disappearing when tapping an album
            val state = _uiState.value
            if (!state.isMovingPhoto && state.moveIntentSender == null && state.pendingMoveAlbum == null) {
                // Reload using the same filter that was originally applied
                if (state.menuFilter != null) {
                    loadPhotosWithMenuFilter(state.menuFilter)
                } else {
                    loadPhotos()
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            repository.getToDeleteCount().collect { count ->
                _uiState.value = _uiState.value.copy(toDeleteCount = count)
            }
        }

        // Register content observer to detect new photos
        getApplication<PhotoCleanupApp>().contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<PhotoCleanupApp>().contentResolver.unregisterContentObserver(contentObserver)
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val filter = _uiState.value.filter
            val filteredPhotos = repository.loadFilteredPhotos(filter)
            val reviewedUris = repository.getReviewedUris()
            val unreviewedPhotos = filteredPhotos.filter { it.toString() !in reviewedUris }
            _uiState.value = _uiState.value.copy(
                photos = unreviewedPhotos,
                currentIndex = 0,
                isLoading = false,
                totalPhotosCount = filteredPhotos.size,
                reviewedCount = filteredPhotos.size - unreviewedPhotos.size,
                showCelebration = true
            )
        }
    }

    fun loadPhotosWithMenuFilter(menuFilter: MenuFilter?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, menuFilter = menuFilter)

            val unreviewedPhotos = if (menuFilter == null) {
                // No filter - load all unreviewed photos
                repository.getUnreviewedPhotos()
            } else {
                when {
                    menuFilter.isAllMedia -> {
                        repository.loadAllMedia()
                    }
                    menuFilter.year != null && menuFilter.month != null -> {
                        repository.loadPhotosByMonth(menuFilter.year, menuFilter.month)
                    }
                    menuFilter.albumBucketId != null -> {
                        repository.loadPhotosByAlbumId(menuFilter.albumBucketId)
                    }
                    else -> {
                        repository.getUnreviewedPhotos()
                    }
                }
            }

            _uiState.value = _uiState.value.copy(
                photos = unreviewedPhotos,
                currentIndex = 0,
                isLoading = false,
                totalPhotosCount = unreviewedPhotos.size,
                reviewedCount = 0,
                showCelebration = true
            )
        }
    }

    fun loadAvailableFolders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingFolders = true)
            val folders = repository.getAvailableFolders()
            _uiState.value = _uiState.value.copy(
                availableFolders = folders,
                isLoadingFolders = false
            )
        }
    }

    fun updateFilter(filter: PhotoFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        loadPhotos()
    }

    fun toggleFolderSelection(folderName: String) {
        val currentFilter = _uiState.value.filter
        val newSelectedFolders = if (folderName in currentFilter.selectedFolders) {
            currentFilter.selectedFolders - folderName
        } else {
            currentFilter.selectedFolders + folderName
        }
        updateFilter(currentFilter.copy(selectedFolders = newSelectedFolders))
    }

    fun setDateRange(dateRange: DateRangeFilter) {
        val currentFilter = _uiState.value.filter
        updateFilter(currentFilter.copy(dateRange = dateRange))
    }

    fun clearFilters() {
        updateFilter(PhotoFilter())
    }

    fun keepCurrentPhoto() {
        val currentPhoto = _uiState.value.currentPhoto ?: return
        val currentIndex = _uiState.value.currentIndex
        viewModelScope.launch {
            repository.markAsReviewed(currentPhoto, "keep")
            _uiState.value = _uiState.value.copy(
                lastAction = UndoableAction(currentPhoto, "keep", currentIndex)
            )
            moveToNextPhoto()
        }
    }

    fun markCurrentPhotoForDeletion() {
        val currentPhoto = _uiState.value.currentPhoto ?: return
        val currentIndex = _uiState.value.currentIndex
        viewModelScope.launch {
            repository.markAsReviewed(currentPhoto, "to_delete")
            _uiState.value = _uiState.value.copy(
                lastAction = UndoableAction(currentPhoto, "to_delete", currentIndex)
            )
            moveToNextPhoto()
        }
    }

    fun undoLastAction() {
        val lastAction = _uiState.value.lastAction ?: return
        viewModelScope.launch {
            repository.removeReview(lastAction.photo)
            val currentPhotos = _uiState.value.photos.toMutableList()
            currentPhotos.add(lastAction.previousIndex, lastAction.photo)
            _uiState.value = _uiState.value.copy(
                photos = currentPhotos,
                currentIndex = lastAction.previousIndex,
                reviewedCount = _uiState.value.reviewedCount - 1,
                lastAction = null
            )
        }
    }

    fun requestDeleteCurrentPhoto(): IntentSender? {
        val currentPhoto = _uiState.value.currentPhoto ?: return null
        val context = getApplication<PhotoCleanupApp>()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val deleteRequest = MediaStore.createDeleteRequest(
                context.contentResolver,
                listOf(currentPhoto)
            )
            _uiState.value = _uiState.value.copy(
                pendingDeleteUri = currentPhoto,
                deleteIntentSender = deleteRequest.intentSender
            )
            deleteRequest.intentSender
        } else {
            // For Android 10 and below, delete directly
            deletePhotoDirectly(context.contentResolver, currentPhoto)
            null
        }
    }

    fun onDeleteConfirmed(success: Boolean) {
        val pendingUri = _uiState.value.pendingDeleteUri
        if (pendingUri != null && success) {
            viewModelScope.launch {
                repository.markAsReviewed(pendingUri, "deleted")
                _uiState.value = _uiState.value.copy(
                    pendingDeleteUri = null,
                    deleteIntentSender = null
                )
                moveToNextPhoto()
            }
        } else {
            _uiState.value = _uiState.value.copy(
                pendingDeleteUri = null,
                deleteIntentSender = null
            )
        }
    }

    private fun deletePhotoDirectly(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                contentResolver.delete(uri, null, null)
                repository.markAsReviewed(uri, "deleted")
                moveToNextPhoto()
            } catch (e: Exception) {
                // Handle error - photo might be protected
            }
        }
    }

    private fun moveToNextPhoto() {
        val currentState = _uiState.value
        val newPhotos = currentState.photos.toMutableList()

        if (currentState.currentIndex < newPhotos.size) {
            newPhotos.removeAt(currentState.currentIndex)
        }

        val newIndex = if (currentState.currentIndex >= newPhotos.size) {
            maxOf(0, newPhotos.size - 1)
        } else {
            currentState.currentIndex
        }

        _uiState.value = currentState.copy(
            photos = newPhotos,
            currentIndex = newIndex,
            reviewedCount = currentState.reviewedCount + 1
        )
    }

    fun resetAllReviews() {
        viewModelScope.launch {
            repository.resetAllReviews()
            loadPhotos()
        }
    }

    fun dismissCelebration() {
        _uiState.value = _uiState.value.copy(showCelebration = false)
    }

    fun loadCurrentPhotoAlbum() {
        val currentPhoto = _uiState.value.currentPhoto ?: return
        viewModelScope.launch {
            val albumInfo = repository.getPhotoAlbumInfo(currentPhoto)
            _uiState.value = _uiState.value.copy(currentPhotoAlbum = albumInfo)
        }
    }

    fun loadFavouriteStatus() {
        val currentPhoto = _uiState.value.currentPhoto ?: return
        viewModelScope.launch {
            val isFav = repository.isPhotoFavourite(currentPhoto)
            _uiState.value = _uiState.value.copy(isCurrentPhotoFavourite = isFav)
        }
    }

    fun toggleFavourite() {
        val currentPhoto = _uiState.value.currentPhoto ?: return
        val newValue = !_uiState.value.isCurrentPhotoFavourite
        viewModelScope.launch {
            repository.setPhotoFavourite(currentPhoto, newValue)
            _uiState.value = _uiState.value.copy(isCurrentPhotoFavourite = newValue)
        }
    }

    fun movePhotoToAlbum(albumName: String) {
        val currentPhoto = _uiState.value.currentPhoto ?: return
        val sourceAlbumId = _uiState.value.currentPhotoAlbum?.bucketId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMovingPhoto = true, moveError = null)
            when (val result = repository.movePhotoToAlbum(currentPhoto, albumName)) {
                is MoveResult.Success -> {
                    // Update the current photo's album info
                    val newAlbumInfo = repository.getPhotoAlbumInfo(result.newUri)
                    _uiState.value = _uiState.value.copy(
                        isMovingPhoto = false,
                        currentPhotoAlbum = newAlbumInfo
                    )
                    // Update folder counts locally (no reload, no reordering)
                    updateFolderCountsAfterMove(sourceAlbumId, albumName)
                }
                is MoveResult.RequiresPermission -> {
                    _uiState.value = _uiState.value.copy(
                        isMovingPhoto = false,
                        moveIntentSender = result.intentSender,
                        pendingMoveAlbum = result.targetAlbum,
                        pendingMoveSourceAlbumId = sourceAlbumId
                    )
                }
                is MoveResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isMovingPhoto = false,
                        moveError = result.message
                    )
                }
                is MoveResult.AlreadyInAlbum -> {
                    _uiState.value = _uiState.value.copy(
                        isMovingPhoto = false,
                        moveError = "Already in this album"
                    )
                }
            }
        }
    }

    fun onMovePermissionResult(granted: Boolean) {
        val currentPhoto = _uiState.value.currentPhoto
        val targetAlbum = _uiState.value.pendingMoveAlbum
        val sourceAlbumId = _uiState.value.pendingMoveSourceAlbumId

        if (granted && currentPhoto != null && targetAlbum != null) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isMovingPhoto = true,
                    moveIntentSender = null,
                    pendingMoveAlbum = null,
                    pendingMoveSourceAlbumId = null
                )
                when (val result = repository.completeMoveAfterPermission(currentPhoto, targetAlbum)) {
                    is MoveResult.Success -> {
                        val newAlbumInfo = repository.getPhotoAlbumInfo(result.newUri)
                        _uiState.value = _uiState.value.copy(
                            isMovingPhoto = false,
                            currentPhotoAlbum = newAlbumInfo
                        )
                        // Update folder counts locally (no reload, no reordering)
                        updateFolderCountsAfterMove(sourceAlbumId, targetAlbum)
                    }
                    is MoveResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isMovingPhoto = false,
                            moveError = result.message
                        )
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(isMovingPhoto = false)
                    }
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(
                moveIntentSender = null,
                pendingMoveAlbum = null,
                pendingMoveSourceAlbumId = null
            )
        }
    }

    fun clearMoveError() {
        _uiState.value = _uiState.value.copy(moveError = null)
    }

    /**
     * Update folder counts locally after moving a photo.
     * This avoids reloading and re-sorting the folder list.
     */
    private fun updateFolderCountsAfterMove(sourceAlbumId: Long?, targetAlbumName: String) {
        val updatedFolders = _uiState.value.availableFolders.map { folder ->
            when {
                folder.bucketId == sourceAlbumId -> folder.copy(photoCount = folder.photoCount - 1)
                folder.displayName == targetAlbumName -> folder.copy(photoCount = folder.photoCount + 1)
                else -> folder
            }
        }
        _uiState.value = _uiState.value.copy(availableFolders = updatedFolders)
    }
}
