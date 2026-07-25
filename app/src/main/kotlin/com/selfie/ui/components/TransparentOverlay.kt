package com.selfie.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
fun TransparentOverlay(
    imageUri: Uri?,
    modifier: Modifier = Modifier
) {
    if (imageUri == null) return

    AsyncImage(
        model = imageUri,
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Fit
    )
}
