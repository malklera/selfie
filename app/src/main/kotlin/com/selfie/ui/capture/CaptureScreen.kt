package com.selfie.ui.capture

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.selfie.R
import com.selfie.ui.components.ConfigButton
import com.selfie.ui.components.CountdownOverlay
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onNavigateToConfig: () -> Unit,
    onNavigateToMain: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsState()
    val config by viewModel.config.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            viewModel.startCountdown(config.countdownSeconds)
        } else {
            viewModel.onCaptureError(context.getString(R.string.camera_permission_required))
        }
    }

    LaunchedEffect(key1 = hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Set up CameraX components
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val previewView = remember { PreviewView(context) }
    var isCameraReady by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = hasCameraPermission) {
        if (hasCameraPermission) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                
                // Select the best available camera
                val cameraSelector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> {
                        Log.d("CaptureScreen", "Using Front Camera")
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> {
                        Log.d("CaptureScreen", "Front camera not found, using Back Camera")
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    else -> {
                        Log.e("CaptureScreen", "No cameras found on device")
                        viewModel.onCaptureError("No se encontró ninguna cámara en el dispositivo")
                        return@addListener
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                    isCameraReady = true
                    Log.d("CaptureScreen", "Camera bound successfully")
                } catch (e: Exception) {
                    Log.e("CaptureScreen", "Camera binding failed", e)
                    viewModel.onCaptureError("Fallo al iniciar cámara: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    // Trigger picture capture when state becomes Capturing
    LaunchedEffect(key1 = state, key2 = isCameraReady) {
        if (state is CaptureState.Capturing) {
            if (isCameraReady) {
                takePhoto(
                    context = context,
                    imageCapture = imageCapture,
                    destinationPath = config.destinationPath,
                    executor = cameraExecutor,
                    onSuccess = { path ->
                        viewModel.onPhotoCaptured(path)
                    },
                    onError = { error ->
                        viewModel.onCaptureError(error)
                    }
                )
            } else {
                Log.w("CaptureScreen", "Capture triggered but camera not ready yet")
                // We could wait or show a loading state, but for now we'll just log
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val currentState = state) {
            is CaptureState.Loading -> {
                // Display logo-1920_1920.png scaled as splash
                Image(
                    painter = painterResource(id = R.drawable.logo_splash),
                    contentDescription = "Loading logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is CaptureState.Countdown, is CaptureState.Capturing -> {
                if (hasCameraPermission) {
                    AndroidView(
                        factory = {
                            previewView.apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    if (currentState is CaptureState.Countdown) {
                        CountdownOverlay(remainingSeconds = currentState.remaining)
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = context.getString(R.string.camera_permission_required),
                            color = Color.White,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            is CaptureState.Result -> {
                // Show the picture just taken fullscreen
                val bitmap = remember(currentState.photoPath) {
                    loadBitmapFromPath(context, currentState.photoPath)
                }

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Selfie",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Error al cargar la foto",
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    }
                }

                // Display a button at the center bottom to take another picture, which will take us to main.
                Button(
                    onClick = onNavigateToMain,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                ) {
                    Text(
                        text = "Toma otra foto",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Display the configuration button in the bottom right corner (the same way as in main)
                ConfigButton(
                    onClick = onNavigateToConfig,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                )
            }
            is CaptureState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Error: ${currentState.message}",
                        color = Color.Red,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    destinationPath: String,
    executor: Executor,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    val fileName = "$timeStamp.jpg"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Scoped storage path setup
        val relativePath = if (destinationPath.startsWith("Pictures/")) {
            destinationPath
        } else {
            "Pictures/$destinationPath"
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val savedUri = outputFileResults.savedUri
                    if (savedUri != null) {
                        onSuccess(savedUri.toString())
                    } else {
                        onError("El URI de salida es nulo")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CaptureScreen", "Photo capture failed: ${exception.message}", exception)
                    onError(exception.message ?: "Error desconocido al capturar")
                }
            }
        )
    } else {
        // Legacy storage path setup
        val storageDir = if (destinationPath.startsWith("/")) {
            File(destinationPath)
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), destinationPath)
        }

        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val photoFile = File(storageDir, fileName)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    onSuccess(photoFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CaptureScreen", "Photo capture failed: ${exception.message}", exception)
                    onError(exception.message ?: "Error desconocido al capturar")
                }
            }
        )
    }
}

/**
 * Helper to load a bitmap from a content URI or raw file path.
 */
private fun loadBitmapFromPath(context: Context, path: String): Bitmap? {
    return try {
        if (path.startsWith("content://") || path.startsWith("file://")) {
            val uri = Uri.parse(path)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } else {
            BitmapFactory.decodeFile(path)
        }
    } catch (e: Exception) {
        Log.e("CaptureScreen", "Error loading bitmap", e)
        null
    }
}

