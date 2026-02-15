package com.stashortrash.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stashortrash.app.PhotoCleanupApp
import com.stashortrash.app.data.AlbumGroup
import com.stashortrash.app.data.AppPreferences
import com.stashortrash.app.data.MenuMode
import com.stashortrash.app.data.MonthGroup
import com.stashortrash.app.data.PhotoRepository
import com.stashortrash.app.data.AllMediaStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MenuUiState(
    val isLoading: Boolean = true,
    val menuMode: MenuMode = MenuMode.BY_DATE,
    val monthGroups: List<MonthGroup> = emptyList(),
    val albumGroups: List<AlbumGroup> = emptyList(),
    val allMediaStats: AllMediaStats? = null,
    val hasAnyUnreviewedPhotos: Boolean = true,
    val mostRecentMediaUri: Uri? = null,
    val randomMediaUri: Uri? = null
)

class MenuViewModel(application: Application) : AndroidViewModel(application) {
    private val database = (application as PhotoCleanupApp).database
    private val repository = PhotoRepository(application, database.reviewedPhotoDao())
    private val appPreferences = AppPreferences(application)

    val isPremium: Boolean get() = appPreferences.isPremium

    private val _uiState = MutableStateFlow(MenuUiState())
    val uiState: StateFlow<MenuUiState> = _uiState.asStateFlow()

    init {
        loadMenuData()
    }

    fun loadMenuData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Load all data in parallel
            val monthGroups = repository.getPhotosByMonth()
            val albumGroups = repository.getPhotosByAlbum()
            val allMediaStats = repository.getAllMediaStats()
            val mostRecentUri = repository.getMostRecentMediaUri()
            val randomUri = repository.getRandomThumbnailUri()

            // Check if there are any unreviewed photos
            val hasUnreviewed = monthGroups.isNotEmpty() || albumGroups.isNotEmpty()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                monthGroups = monthGroups,
                albumGroups = albumGroups,
                allMediaStats = allMediaStats,
                hasAnyUnreviewedPhotos = hasUnreviewed,
                mostRecentMediaUri = mostRecentUri,
                randomMediaUri = randomUri
            )
        }
    }

    fun setMenuMode(mode: MenuMode) {
        _uiState.value = _uiState.value.copy(menuMode = mode)
    }

    fun refreshData() {
        loadMenuData()
    }
}
