package com.selfie.ui.navigation

import android.net.Uri

sealed class Route(val route: String) {
    data object Main : Route("main")
    data object Config : Route("config")
    data object Capture : Route("capture")
    data object CaptureResult : Route("capture_result/{photoPath}") {
        fun createRoute(photoPath: String): String =
            "capture_result/${Uri.encode(photoPath)}"
    }
}
