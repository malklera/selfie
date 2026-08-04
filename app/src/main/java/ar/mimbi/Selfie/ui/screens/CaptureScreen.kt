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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.documentfile.provider.DocumentFile
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
    
    var countdown by rememberSaveable { mutableStateOf(config.countdownSeconds) }
    var isCaptured by rememberSaveable { mutableStateOf(false) }
    var capturedUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Reload bitmap if we have a saved URI
    LaunchedEffect(capturedUriString) {
        capturedUriString?.let { uriString ->
            if (capturedBitmap == null) {
                capturedBitmap = loadAndCorrectBitmap(context, Uri.parse(uriString))
            }
        }
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { 
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build() 
    }
    val previewView = remember { PreviewView(context) }

    var isPreviewReady by remember { mutableStateOf(false) }
    
    // Observer for camera stream state to ensure preview is visible
    DisposableEffect(previewView) {
        val observer = androidx.lifecycle.Observer<PreviewView.StreamState> { state ->
            if (state == PreviewView.StreamState.STREAMING) {
                isPreviewReady = true
            }
        }
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        onDispose {
            previewView.previewStreamState.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Set target rotation based on display
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
                
                // Use UseCaseGroup to ensure matching ViewPort
                val useCaseGroupBuilder = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageCapture)
                
                // Try to get ViewPort from PreviewView
                previewView.viewPort?.let {
                    useCaseGroupBuilder.setViewPort(it)
                }
                
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, useCaseGroupBuilder.build()
                )
            } catch (exc: Exception) {
                Log.e("CaptureScreen", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(isCaptured, isPreviewReady) {
        if (!isCaptured && isPreviewReady) {
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            takePhoto(context, imageCapture, config.destinationPath, cameraExecutor) { uri ->
                capturedUriString = uri.toString()
                capturedBitmap = loadAndCorrectBitmap(context, uri)
                isCaptured = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!isCaptured) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            
            if (isPreviewReady) {
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
                onClick = onNavigateToMain,
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
            ) {
                Text("Toca para otra foto")
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
    }
    
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }
}

private fun loadAndCorrectBitmap(context: Context, uri: Uri): Bitmap? {
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
        inputStream.close()

        val exifInputStream = context.contentResolver.openInputStream(uri) ?: return null
        val exif = ExifInterface(exifInputStream)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        exifInputStream.close()

        Log.d("CaptureScreen", "EXIF Orientation: $orientation")

        // Handle EXIF orientation - complete handling
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        
        // Now we have a "natural" image. To match front camera preview (mirrored), 
        // we flip it horizontally.
        matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)

        val processedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        Log.d("CaptureScreen", "Bitmap processed. Size: ${processedBitmap.width}x${processedBitmap.height}")
        return processedBitmap
    } catch (e: Exception) {
        Log.e("CaptureScreen", "Error correcting bitmap", e)
        return null
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    destinationPath: String,
    executor: ExecutorService,
    onCaptured: (Uri) -> Unit
) {
    val name = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        .format(System.currentTimeMillis()) + ".jpg"

    val uri = try { Uri.parse(destinationPath) } catch (e: Exception) { null }
    if (uri?.scheme == "content") {
        val documentFile = DocumentFile.fromTreeUri(context, uri)
        val newFile = documentFile?.createFile("image/jpeg", name)
        newFile?.let {
            val outputOptions = ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                it.uri,
                android.content.ContentValues()
            ).build()

            imageCapture.takePicture(
                outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        onCaptured(it.uri)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("CaptureScreen", "Photo capture failed: ${exception.message}", exception)
                    }
                }
            )
            return
        }
    }
    
    // Fallback to File API
    val dir = File(destinationPath)
    if (!dir.exists()) dir.mkdirs()
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
