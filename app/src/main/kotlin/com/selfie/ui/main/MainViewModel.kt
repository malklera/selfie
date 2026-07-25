package com.selfie.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfie.data.preferences.PreferencesRepository
import com.selfie.domain.model.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _sessionCameraFront = MutableStateFlow<Boolean?>(null)
    val sessionCameraFront = _sessionCameraFront.asStateFlow()

    val appConfig: StateFlow<AppConfig?> = preferencesRepository.appConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val effectiveCameraFront: StateFlow<Boolean> = combine(
        appConfig,
        _sessionCameraFront
    ) { config, sessionFront ->
        sessionFront ?: config?.defaultCameraFront ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun toggleCamera() {
        _sessionCameraFront.value = !effectiveCameraFront.value
    }
}
