package com.selfie.ui.capture

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.selfie.R
import com.selfie.domain.model.ButtonSlot
import com.selfie.ui.components.FloatingButtonRow

@Composable
fun CaptureResultScreen(
    viewModel: CaptureResultViewModel,
    photoUri: Uri,
    onNavigateToMain: () -> Unit,
    onNavigateToPreview: () -> Unit,
    onNavigateToGallery: () -> Unit
) {
    val config by viewModel.appConfig.collectAsState()

    val buttons = listOf(
        ButtonSlot(
            id = "home",
            label = stringResource(R.string.btn_volver_inicio),
            icon = Icons.Default.Home,
            visible = config?.showToMainButton ?: true,
            onClick = onNavigateToMain
        ),
        ButtonSlot(
            id = "retake",
            label = stringResource(R.string.btn_otra_foto),
            icon = Icons.Default.Camera,
            visible = config?.showQuickRetakeButton ?: true,
            onClick = onNavigateToPreview
        ),
        ButtonSlot(
            id = "gallery",
            label = stringResource(R.string.btn_galeria),
            icon = Icons.Default.PhotoLibrary,
            visible = config?.showGalleryButton ?: true,
            onClick = onNavigateToGallery
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = photoUri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        FloatingButtonRow(
            buttons = buttons,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
