package com.example.photocleanup.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photocleanup.PhotoCleanupApp
import com.example.photocleanup.data.AlbumGroup
import com.example.photocleanup.data.MenuMode
import com.example.photocleanup.data.MonthGroup
import com.example.photocleanup.data.PhotoRepository
import com.example.photocleanup.data.RecentPhotosStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MenuUiState(
    val isLoading: Boolean = true,
    val menuMode: MenuMode = MenuMode.BY_DATE,
    val monthGroups: List<MonthGroup> = emptyList(),
    val albumGroups: List<AlbumGroup> = emptyList(),
    val recentPhotosStats: RecentPhotosStats? = null,
    val hasAnyUnreviewedPhotos: Boolean = true
)

class MenuViewModel(application: Application) : AndroidViewModel(application) {
    private val database = (application as PhotoCleanupApp).database
    private val repository = PhotoRepository(application, database.reviewedPhotoDao())

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
            val recentStats = repository.getRecentPhotosStats()

            // Check if there are any unreviewed photos
            val hasUnreviewed = monthGroups.isNotEmpty() || albumGroups.isNotEmpty()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                monthGroups = monthGroups,
                albumGroups = albumGroups,
                recentPhotosStats = recentStats,
                hasAnyUnreviewedPhotos = hasUnreviewed
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
