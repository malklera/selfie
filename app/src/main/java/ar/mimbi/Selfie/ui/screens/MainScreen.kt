package ar.mimbi.Selfie.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.mimbi.Selfie.data.AppConfig
import android.graphics.BitmapFactory
import java.io.File

@Composable
fun MainScreen(
    config: AppConfig,
    onNavigateToCapture: () -> Unit,
    onNavigateToConfig: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full-screen clickable area for capture
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onNavigateToCapture() }
        ) {
            if (config.portadaPath != null && File(config.portadaPath).exists()) {
                val bitmap = BitmapFactory.decodeFile(config.portadaPath)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    DefaultMessage()
                }
            } else {
                DefaultMessage()
            }
        }

        // Settings button on top
        IconButton(
            onClick = onNavigateToConfig,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(64.dp)
                .background(Color.Black.copy(alpha = 0.4f), shape = CircleShape)
                .clip(CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Configuración",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(48.dp)
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
