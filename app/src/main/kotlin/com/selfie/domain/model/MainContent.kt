package com.selfie.domain.model

import android.net.Uri

sealed class MainContent {
    object None : MainContent()
    data class StaticImage(val uri: Uri) : MainContent()
    data class Video(val uri: Uri) : MainContent()
    data class AnimatedGif(val uri: Uri) : MainContent()
}
