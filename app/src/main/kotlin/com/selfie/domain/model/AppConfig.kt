package com.selfie.domain.model

import android.net.Uri

data class AppConfig(
    val defaultCameraFront: Boolean = true,
    val showFlipButton: Boolean = true,
    val countdownDurationSeconds: Int = 3,
    val overlayImageUri: Uri? = null,
    val saveFolderUri: Uri? = null,
    val mainContent: MainContent = MainContent.None,
    val showToMainButton: Boolean = true,
    val showQuickRetakeButton: Boolean = true,
    val showGalleryButton: Boolean = true
)
