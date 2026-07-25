package com.selfie.ui.gallery

import androidx.lifecycle.ViewModel
import com.selfie.data.gallery.GalleryRepository
import com.selfie.data.preferences.PreferencesRepository

import androidx.lifecycle.viewModelScope
import com.selfie.domain.model.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.net.Uri

class GalleryViewModel(
    private val galleryRepository: GalleryRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _photos = MutableStateFlow<List<Uri>>(emptyList())
    val photos = _photos.asStateFlow()

    val appConfig: StateFlow<AppConfig?> = preferencesRepository.appConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun loadPhotos() {
        viewModelScope.launch {
            val config = preferencesRepository.appConfig.first()
            _photos.value = galleryRepository.getPhotos(config.saveFolderUri)
        }
    }
}
