package com.selfie.ui.config

import androidx.lifecycle.ViewModel
import com.selfie.data.preferences.PreferencesRepository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.selfie.domain.model.AppConfig
import com.selfie.domain.model.MainContent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConfigViewModel(private val preferencesRepository: PreferencesRepository) : ViewModel() {

    val appConfig: StateFlow<AppConfig?> = preferencesRepository.appConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateCamera(isFront: Boolean) {
        viewModelScope.launch {
            appConfig.value?.let {
                preferencesRepository.updateConfig(it.copy(defaultCameraFront = isFront))
            }
        }
    }

    fun updateFlipButton(show: Boolean) {
        viewModelScope.launch {
            appConfig.value?.let {
                preferencesRepository.updateConfig(it.copy(showFlipButton = show))
            }
        }
    }

    fun updateCountdown(seconds: Int) {
        viewModelScope.launch {
            appConfig.value?.let {
                preferencesRepository.updateConfig(it.copy(countdownDurationSeconds = seconds))
            }
        }
    }

    fun updateOverlayImage(context: Context, uri: Uri?) {
        viewModelScope.launch {
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            appConfig.value?.let {
                preferencesRepository.updateConfig(it.copy(overlayImageUri = uri))
            }
        }
    }

    fun updateSaveFolder(context: Context, uri: Uri?) {
        viewModelScope.launch {
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            appConfig.value?.let {
                preferencesRepository.updateConfig(it.copy(saveFolderUri = uri))
            }
        }
    }

    fun updateMainContent(context: Context, type: String, uri: Uri?) {
        viewModelScope.launch {
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val content = when (type) {
                "IMAGE" -> uri?.let { MainContent.StaticImage(it) } ?: MainContent.None
                "VIDEO" -> uri?.let { MainContent.Video(it) } ?: MainContent.None
                "GIF" -> uri?.let { MainContent.AnimatedGif(it) } ?: MainContent.None
                else -> MainContent.None
            }
            appConfig.value?.let {
                preferencesRepository.updateConfig(it.copy(mainContent = content))
            }
        }
    }

    fun updateButtonVisibility(id: String, show: Boolean) {
        viewModelScope.launch {
            appConfig.value?.let { config ->
                val newConfig = when (id) {
                    "to_main" -> config.copy(showToMainButton = show)
                    "retake" -> config.copy(showQuickRetakeButton = show)
                    "gallery" -> config.copy(showGalleryButton = show)
                    else -> config
                }
                preferencesRepository.updateConfig(newConfig)
            }
        }
    }
}
