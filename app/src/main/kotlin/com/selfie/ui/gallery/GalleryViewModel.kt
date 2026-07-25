package com.selfie.ui.gallery

import androidx.lifecycle.ViewModel
import com.selfie.data.gallery.GalleryRepository
import com.selfie.data.preferences.PreferencesRepository

class GalleryViewModel(
    private val galleryRepository: GalleryRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel()
