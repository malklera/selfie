package com.selfie.ui.config

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.selfie.R
import com.selfie.domain.model.MainContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    viewModel: ConfigViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val config by viewModel.appConfig.collectAsState()
    val scrollState = rememberScrollState()

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        viewModel.updateSaveFolder(context, uri)
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.updateOverlayImage(context, uri)
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.updateMainContent(context, "IMAGE", uri)
    }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.updateMainContent(context, "VIDEO", uri)
    }

    val gifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.updateMainContent(context, "GIF", uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.config_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // Camera Default
            Text(stringResource(R.string.config_camera_default), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = config?.defaultCameraFront == true, onClick = { viewModel.updateCamera(true) })
                Text(stringResource(R.string.config_camera_front))
                Spacer(Modifier.padding(horizontal = 8.dp))
                RadioButton(selected = config?.defaultCameraFront == false, onClick = { viewModel.updateCamera(false) })
                Text(stringResource(R.string.config_camera_back))
            }

            // Flip Button
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.config_show_flip_button))
                Spacer(Modifier.weight(1f))
                Switch(checked = config?.showFlipButton == true, onCheckedChange = { viewModel.updateFlipButton(it) })
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Countdown
            Text(
                "${stringResource(R.string.config_countdown_duration)}: ${config?.countdownDurationSeconds}",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = config?.countdownDurationSeconds?.toFloat() ?: 3f,
                onValueChange = { viewModel.updateCountdown(it.toInt()) },
                valueRange = 0f..30f,
                steps = 30
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Save Folder
            Text(stringResource(R.string.config_save_folder), style = MaterialTheme.typography.titleMedium)
            Text(config?.saveFolderUri?.toString() ?: "No seleccionada", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { folderLauncher.launch(null) }) {
                Text(stringResource(R.string.select_folder))
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Overlay Image
            Text(stringResource(R.string.config_overlay_image), style = MaterialTheme.typography.titleMedium)
            Text(config?.overlayImageUri?.toString() ?: "Ninguna", style = MaterialTheme.typography.bodySmall)
            Row {
                Button(onClick = { overlayLauncher.launch(arrayOf("image/*")) }) {
                    Text(stringResource(R.string.select_file))
                }
                Spacer(Modifier.padding(horizontal = 4.dp))
                Button(onClick = { viewModel.updateOverlayImage(context, null) }) {
                    Text("Quitar")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Main Content
            Text(stringResource(R.string.config_main_content), style = MaterialTheme.typography.titleMedium)
            val currentContent = config?.mainContent ?: MainContent.None
            val currentUri = when (currentContent) {
                is MainContent.StaticImage -> currentContent.uri
                is MainContent.Video -> currentContent.uri
                is MainContent.AnimatedGif -> currentContent.uri
                else -> null
            }
            Text("Actual: ${currentContent::class.simpleName} - ${currentUri ?: "Ninguno"}", style = MaterialTheme.typography.bodySmall)
            
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { viewModel.updateMainContent(context, "NONE", null) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.config_content_none))
                }
                Spacer(Modifier.padding(horizontal = 2.dp))
                Button(onClick = { imageLauncher.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.config_content_image))
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { videoLauncher.launch(arrayOf("video/*")) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.config_content_video))
                }
                Spacer(Modifier.padding(horizontal = 2.dp))
                Button(onClick = { gifLauncher.launch(arrayOf("image/gif")) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.config_content_gif))
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // Buttons Visibility
            Text("Botones en resultado", style = MaterialTheme.typography.titleMedium)
            VisibilityToggle(stringResource(R.string.config_show_to_main), config?.showToMainButton ?: true) { viewModel.updateButtonVisibility("to_main", it) }
            VisibilityToggle(stringResource(R.string.config_show_retake), config?.showQuickRetakeButton ?: true) { viewModel.updateButtonVisibility("retake", it) }
            VisibilityToggle(stringResource(R.string.config_show_gallery), config?.showGalleryButton ?: true) { viewModel.updateButtonVisibility("gallery", it) }
        }
    }
}

@Composable
fun VisibilityToggle(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label)
        Spacer(Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
