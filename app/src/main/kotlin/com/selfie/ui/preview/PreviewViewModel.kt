package com.selfie.ui.preview

import androidx.lifecycle.ViewModel
import com.selfie.data.preferences.PreferencesRepository

import androidx.lifecycle.viewModelScope
import com.selfie.domain.model.AppConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PreviewViewModel(private val preferencesRepository: PreferencesRepository) : ViewModel() {

    val appConfig: StateFlow<AppConfig?> = preferencesRepository.appConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _countdown = MutableStateFlow(0)
    val countdown = _countdown.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing = _isCapturing.asStateFlow()

    private var captureTriggered = false

    fun startCountdown(duration: Int, onCapture: () -> Unit) {
        if (captureTriggered) return
        captureTriggered = true
        
        viewModelScope.launch {
            for (i in duration downTo 0) {
                _countdown.value = i
                if (i == 0) {
                    _isCapturing.value = true
                    onCapture()
                    break
                }
                delay(1000)
            }
        }
    }
}
