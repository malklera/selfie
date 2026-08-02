package ar.mimbi.Selfie.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import ar.mimbi.Selfie.data.AppConfig
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CaptureScreen(
    config: AppConfig,
    onNavigateToMain: () -> Unit,
    onNavigateToConfig: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var countdown by remember { mutableStateOf(config.countdownSeconds) }
    var isCaptured by remember { mutableStateOf(false) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CaptureScreen", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(isCaptured) {
        if (!isCaptured) {
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            takePhoto(context, imageCapture, config.destinationPath, cameraExecutor) { uri ->
                capturedImageUri = uri
                isCaptured = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!isCaptured) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = countdown.toString(),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 120.sp
                )
            }
        } else {
            capturedImageUri?.let { uri ->
                val bitmap = BitmapFactory.decodeFile(uri.path)
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            Button(
                onClick = onNavigateToMain,
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
            ) {
                Text("Toca para otra foto")
            }
        }

        IconButton(
            onClick = onNavigateToConfig,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(64.dp)
                .background(Color.Black.copy(alpha = 0.2f))
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Configuración",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    destinationPath: String,
    executor: ExecutorService,
    onCaptured: (Uri) -> Unit
) {
    val dir = File(destinationPath)
    if (!dir.exists()) dir.mkdirs()
    
    val name = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        .format(System.currentTimeMillis()) + ".jpg"
    val file = File(dir, name)

    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    imageCapture.takePicture(
        outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(file)
                onCaptured(savedUri)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CaptureScreen", "Photo capture failed: ${exception.message}", exception)
            }
        }
    )
}
