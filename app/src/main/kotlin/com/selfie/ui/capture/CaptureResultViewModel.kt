package com.selfie.ui.capture

import androidx.lifecycle.ViewModel
import com.selfie.data.preferences.PreferencesRepository

import androidx.lifecycle.viewModelScope
import com.selfie.domain.model.AppConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class CaptureResultViewModel(private val preferencesRepository: PreferencesRepository) : ViewModel() {
    val appConfig: StateFlow<AppConfig?> = preferencesRepository.appConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
