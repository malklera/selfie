package ar.mimbi.Selfie.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.util.Log
import android.view.Surface
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
    Log.d("CaptureScreen", "CaptureScreen composed")
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var countdown by remember { mutableStateOf(config.countdownSeconds) }
    var isCaptured by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { 
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build() 
    }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            imageCapture.targetRotation = rotation
            
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setTargetRotation(rotation)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                val useCaseGroupBuilder = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageCapture)
                previewView.viewPort?.let { useCaseGroupBuilder.setViewPort(it) }
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, useCaseGroupBuilder.build()
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
                capturedBitmap = loadAndCorrectBitmap(uri)
                isCaptured = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!isCaptured) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = countdown.toString(),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 120.sp
                )
            }
        } else {
            capturedBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            Button(
                onClick = {
                    Log.d("CaptureScreen", "Retake Button clicked")
                    onNavigateToMain()
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
            ) {
                Text("Toca para otra foto")
            }
        }

        // Standard Button for Settings
        Button(
            onClick = {
                Log.d("CaptureScreen", "Settings Button clicked")
                onNavigateToConfig()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Configuración"
            )
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            Log.d("CaptureScreen", "CaptureScreen disposed")
            cameraExecutor.shutdown()
        }
    }
}

private fun loadAndCorrectBitmap(uri: Uri): Bitmap? {
    val path = uri.path ?: return null
    try {
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        val exif = ExifInterface(path)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.postRotate(180f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: Exception) {
        Log.e("CaptureScreen", "Error correcting bitmap", e)
        return null
    }
}

private fun takePhoto(context: Context, imageCapture: ImageCapture, destinationPath: String, executor: ExecutorService, onCaptured: (Uri) -> Unit) {
    val dir = File(destinationPath)
    if (!dir.exists()) dir.mkdirs()
    val name = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(System.currentTimeMillis()) + ".jpg"
    val file = File(dir, name)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            onCaptured(Uri.fromFile(file))
        }
        override fun onError(exception: ImageCaptureException) {
            Log.e("CaptureScreen", "Photo capture failed", exception)
        }
    })
}
