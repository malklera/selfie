package com.selfie.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.selfie.data.preferences.PreferencesRepository
import com.selfie.ui.capture.CaptureViewModel
import com.selfie.ui.config.ConfigViewModel
import com.selfie.ui.main.MainViewModel

class ViewModelFactory(
    private val preferencesRepository: PreferencesRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) ->
                MainViewModel(preferencesRepository) as T
            modelClass.isAssignableFrom(ConfigViewModel::class.java) ->
                ConfigViewModel(preferencesRepository) as T
            modelClass.isAssignableFrom(CaptureViewModel::class.java) ->
                CaptureViewModel(preferencesRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
