package com.selfie.domain.model

data class AppConfig(
    val coverImageUri: String? = null,
    val countdownSeconds: Int = 3,
    val destinationPath: String = "Pictures/selfie"
)
