package com.selfie.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.selfie.domain.model.MainContent
import com.selfie.ui.components.CameraFlipButton
import com.selfie.ui.components.MainContentView

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToPreview: (isFront: Boolean) -> Unit,
    onNavigateToConfig: () -> Unit
) {
    val config by viewModel.appConfig.collectAsState()
    val isFront by viewModel.effectiveCameraFront.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Content (Video/Image/GIF/None)
        MainContentView(
            content = config?.mainContent ?: MainContent.None,
            modifier = Modifier
                .fillMaxSize()
                .clickable { onNavigateToPreview(isFront) }
        )

        // Config Tap Zone (Top Right)
        Box(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.TopEnd)
                .clickable { onNavigateToConfig() }
        )

        // Flip Camera Button
        if (config?.showFlipButton == true) {
            CameraFlipButton(
                onClick = { viewModel.toggleCamera() },
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}
