package com.selfie.ui.capture

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfie.data.preferences.PreferencesRepository
import com.selfie.domain.model.AppConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface CaptureState {
    data object Loading : CaptureState
    data class Countdown(val remaining: Int) : CaptureState
    data object Capturing : CaptureState
    data class Result(val photoPath: String) : CaptureState
    data class Error(val message: String) : CaptureState
}

class CaptureViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow<CaptureState>(CaptureState.Loading)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            val cfg = preferencesRepository.getConfig().first()
            _config.value = cfg
            startCountdown(cfg.countdownSeconds)
        }
    }

    fun startCountdown(seconds: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in seconds downTo 1) {
                _state.value = CaptureState.Countdown(i)
                delay(1_000)
            }
            _state.value = CaptureState.Capturing
        }
    }

    fun onPhotoCaptured(photoPath: String) {
        _state.value = CaptureState.Result(photoPath)
    }

    fun onCaptureError(message: String) {
        _state.value = CaptureState.Error(message)
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}
