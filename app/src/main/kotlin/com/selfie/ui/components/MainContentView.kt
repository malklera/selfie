package com.selfie.ui.components

import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.selfie.domain.model.MainContent

@Composable
fun MainContentView(
    content: MainContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (content is MainContent.None) {
            Text(
                text = "Toca para foto",
                color = Color.White,
                fontSize = 80.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        when (content) {
            is MainContent.None -> {
                // Keep black background
            }
            is MainContent.StaticImage -> {
                AsyncImage(
                    model = content.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            is MainContent.AnimatedGif -> {
                AsyncImage(
                    model = content.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            is MainContent.Video -> {
                AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            setVideoURI(content.uri)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Robust clickable overlay using pointerInput and zIndex
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onClick() })
                }
        )
    }
}
