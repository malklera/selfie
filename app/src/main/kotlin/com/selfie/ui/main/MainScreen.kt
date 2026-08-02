package com.selfie.ui.main

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfie.domain.model.MainContent
import com.selfie.ui.components.ConfigButton

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToCapture: () -> Unit,
    onNavigateToConfig: () -> Unit
) {
    val content by viewModel.content.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Background content: cover image or default text
        when (val currentContent = content) {
            is MainContent.CoverImage -> {
                val context = LocalContext.current
                val bitmap = remember(currentContent.uri) {
                    try {
                        val uri = Uri.parse(currentContent.uri)
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            BitmapFactory.decodeStream(inputStream)
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            is MainContent.DefaultText -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Toca aquí para foto",
                        color = Color.White,
                        fontSize = 32.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Transparent overlay for tap detection — navigates to capture
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onNavigateToCapture() }
                    )
                }
        )

        // Floating config button at bottom-right
        ConfigButton(
            onClick = onNavigateToConfig,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        )
    }
}
