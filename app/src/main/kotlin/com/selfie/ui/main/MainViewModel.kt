package com.selfie.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfie.data.preferences.PreferencesRepository
import com.selfie.domain.model.MainContent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    val content: StateFlow<MainContent> = preferencesRepository.getConfig()
        .map { config ->
            if (config.coverImageUri != null) {
                MainContent.CoverImage(config.coverImageUri)
            } else {
                MainContent.DefaultText
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainContent.DefaultText
        )
}
