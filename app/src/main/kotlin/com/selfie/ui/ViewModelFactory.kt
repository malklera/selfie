package com.selfie.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.selfie.data.gallery.GalleryRepository
import com.selfie.data.preferences.PreferencesRepository
import com.selfie.ui.main.MainViewModel
import com.selfie.ui.preview.PreviewViewModel
import com.selfie.ui.capture.CaptureResultViewModel
import com.selfie.ui.gallery.GalleryViewModel
import com.selfie.ui.config.ConfigViewModel

class ViewModelFactory(
    private val preferencesRepository: PreferencesRepository,
    private val galleryRepository: GalleryRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                MainViewModel(preferencesRepository) as T
            }
            modelClass.isAssignableFrom(PreviewViewModel::class.java) -> {
                PreviewViewModel(preferencesRepository) as T
            }
            modelClass.isAssignableFrom(CaptureResultViewModel::class.java) -> {
                CaptureResultViewModel(preferencesRepository) as T
            }
            modelClass.isAssignableFrom(GalleryViewModel::class.java) -> {
                GalleryViewModel(galleryRepository, preferencesRepository) as T
            }
            modelClass.isAssignableFrom(ConfigViewModel::class.java) -> {
                ConfigViewModel(preferencesRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
