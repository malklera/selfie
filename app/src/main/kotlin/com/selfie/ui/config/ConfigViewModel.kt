package com.selfie.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfie.data.preferences.PreferencesRepository
import com.selfie.domain.model.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ConfigUiState(
    val coverImageUri: String? = null,
    val countdownText: String = "3",
    val destinationPath: String = "Pictures/selfie",
    val hasUnsavedChanges: Boolean = false
)

class ConfigViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfigUiState())
    val uiState: StateFlow<ConfigUiState> = _uiState.asStateFlow()

    private var savedConfig = AppConfig()

    init {
        viewModelScope.launch {
            val config = preferencesRepository.getConfig().first()
            savedConfig = config
            _uiState.value = ConfigUiState(
                coverImageUri = config.coverImageUri,
                countdownText = config.countdownSeconds.toString(),
                destinationPath = config.destinationPath,
                hasUnsavedChanges = false
            )
        }
    }

    fun updateCoverImage(uri: String?) {
        _uiState.value = _uiState.value.copy(
            coverImageUri = uri,
            hasUnsavedChanges = true
        )
    }

    fun clearCoverImage() {
        _uiState.value = _uiState.value.copy(
            coverImageUri = null,
            hasUnsavedChanges = true
        )
    }

    fun updateCountdown(text: String) {
        _uiState.value = _uiState.value.copy(
            countdownText = text,
            hasUnsavedChanges = true
        )
    }

    fun updateDestination(path: String) {
        _uiState.value = _uiState.value.copy(
            destinationPath = path,
            hasUnsavedChanges = true
        )
    }

    fun save(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val state = _uiState.value
            val seconds = state.countdownText.toIntOrNull()?.coerceAtLeast(1) ?: 3
            val config = AppConfig(
                coverImageUri = state.coverImageUri,
                countdownSeconds = seconds,
                destinationPath = state.destinationPath
            )
            preferencesRepository.save(config)
            savedConfig = config
            _uiState.value = state.copy(
                countdownText = seconds.toString(),
                hasUnsavedChanges = false
            )
            onComplete()
        }
    }

    fun discard() {
        _uiState.value = ConfigUiState(
            coverImageUri = savedConfig.coverImageUri,
            countdownText = savedConfig.countdownSeconds.toString(),
            destinationPath = savedConfig.destinationPath,
            hasUnsavedChanges = false
        )
    }
}
