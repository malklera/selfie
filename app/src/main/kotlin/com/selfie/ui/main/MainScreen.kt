package com.selfie.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
    var configTapCount by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Content (Video/Image/GIF/None)
        MainContentView(
            content = config?.mainContent ?: MainContent.None,
            onClick = { onNavigateToPreview(isFront) },
            modifier = Modifier.fillMaxSize()
        )

        // Flip Camera Button
        if (config?.showFlipButton == true) {
            CameraFlipButton(
                onClick = { viewModel.toggleCamera() },
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // Settings target: requires 5 taps to open config.
        // Declared last and given a high zIndex so it is always hit-tested
        // above the full-screen content overlay. A large rectangular area so
        // it is easy to tap without accidentally hitting the overlay.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(200.dp)
                .zIndex(10f)
                .background(Color(0xCC222222))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        configTapCount++
                        if (configTapCount >= 5) {
                            configTapCount = 0
                            onNavigateToConfig()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (configTapCount > 0) "Settings ($configTapCount/5)" else "Settings",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
