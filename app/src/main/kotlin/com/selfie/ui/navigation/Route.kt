package com.selfie.ui.navigation

sealed class Route(val path: String) {
    object Main : Route("main")
    object Preview : Route("preview/{isFront}") {
        fun createRoute(isFront: Boolean) = "preview/$isFront"
    }
    object CaptureResult : Route("capture_result/{photoUri}") {
        fun createRoute(photoUri: String) = "capture_result/$photoUri"
    }
    object Gallery : Route("gallery")
    object GalleryDetail : Route("gallery_detail/{photoUri}") {
        fun createRoute(photoUri: String) = "gallery_detail/$photoUri"
    }
    object Config : Route("config")
}
