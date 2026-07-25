package com.selfie.ui.preview

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.selfie.ui.components.CountdownOverlay
import com.selfie.ui.components.TransparentOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun PreviewScreen(
    viewModel: PreviewViewModel,
    isFront: Boolean,
    onPhotoCaptured: (Uri) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val config by viewModel.appConfig.collectAsState()
    val countdown by viewModel.countdown.collectAsState()
    
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(isFront) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val cameraSelector = if (isFront) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            // Handle error
        }
    }

    LaunchedEffect(config) {
        val duration = config?.countdownDurationSeconds ?: 3
        viewModel.startCountdown(duration) {
            capturePhoto(context, imageCapture, config?.saveFolderUri) { uri ->
                onPhotoCaptured(uri)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        TransparentOverlay(imageUri = config?.overlayImageUri)
        
        CountdownOverlay(seconds = countdown)
    }
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture,
    folderUri: Uri?,
    onCaptured: (Uri) -> Unit
) {
    val folder = folderUri?.let { DocumentFile.fromTreeUri(context, it) }
    val fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date()) + ".jpg"
    
    val file = folder?.createFile("image/jpeg", fileName)
    val outputUri = file?.uri

    if (outputUri == null) {
        // Fallback or handle error
        return
    }

    val outputStream = context.contentResolver.openOutputStream(outputUri)
    if (outputStream == null) return

    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputStream).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onCaptured(outputUri)
            }

            override fun onError(exception: ImageCaptureException) {
                // Handle error
            }
        }
    )
}
