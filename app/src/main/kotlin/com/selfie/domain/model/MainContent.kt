package com.selfie.domain.model

sealed interface MainContent {
    data class CoverImage(val uri: String) : MainContent
    data object DefaultText : MainContent
}
