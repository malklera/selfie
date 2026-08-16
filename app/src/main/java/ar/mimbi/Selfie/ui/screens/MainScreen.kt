package ar.mimbi.Selfie.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.mimbi.Selfie.data.AppConfig
import ar.mimbi.Selfie.data.UserActionTracker
import ar.mimbi.Selfie.ui.components.SecretSettingsButton
import coil.compose.SubcomposeAsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImageContent

@Composable
fun MainScreen(
    config: AppConfig,
    onNavigateToCapture: () -> Unit,
    onNavigateToConfig: () -> Unit,
    onImageReady: () -> Unit = {}
) {
    var isReady by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full-screen clickable area for capture
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    UserActionTracker.trackAction("Tocar para capturar")
                    onNavigateToCapture()
                }
        ) {
            if (config.portadaPath != null) {
                SubcomposeAsyncImage(
                    model = config.portadaPath,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                ) {
                    val state = painter.state
                    
                    LaunchedEffect(state) {
                        if (state is AsyncImagePainter.State.Success || state is AsyncImagePainter.State.Error) {
                            isReady = true
                            onImageReady()
                        }
                    }

                    if (state is AsyncImagePainter.State.Error || state is AsyncImagePainter.State.Empty) {
                        DefaultMessage()
                    } else {
                        SubcomposeAsyncImageContent()
                    }
                }
            } else {
                DefaultMessage()
                // If there's no image path, the screen is essentially ready
                LaunchedEffect(Unit) {
                    isReady = true
                    onImageReady()
                }
            }
        }

        // Settings button on top
        if (isReady) {
            SecretSettingsButton(
                onNavigateToConfig = onNavigateToConfig,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(58.dp)
            )
        }
    }
}

@Composable
fun DefaultMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Toca aqui para foto",
            color = Color.White,
            fontSize = 32.sp
        )
    }
}
