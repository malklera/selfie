package ar.mimbi.Selfie.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import ar.mimbi.Selfie.data.AppConfig
import ar.mimbi.Selfie.data.CameraResolutionHelper
import ar.mimbi.Selfie.data.ErrorLogger
import ar.mimbi.Selfie.data.UserActionTracker
import ar.mimbi.Selfie.ui.components.SecretSettingsButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    
    var countdown by rememberSaveable { mutableStateOf(config.countdownSeconds) }
    var isCaptured by rememberSaveable { mutableStateOf(false) }
    var capturedUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var captureBoxBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var captureAttempt by remember { mutableStateOf(0) }
    var isFolderCheckPassed by remember { mutableStateOf(false) }
    var folderErrorMessage by remember { mutableStateOf<String?>(null) }
    
    val selectedResolution = remember(config.pictureResolution) {
        CameraResolutionHelper.parseResolution(config.pictureResolution)
            ?: CameraResolutionHelper.getDefaultResolution(context)
    }

    // 1. Pre-check folder access
    LaunchedEffect(config.destinationPath) {
        val result = checkDestinationWritable(context, config.destinationPath)
        if (result == null) {
            isFolderCheckPassed = true
            folderErrorMessage = null
        } else {
            isFolderCheckPassed = false
            folderErrorMessage = result
            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
        }
    }
    
    // Reload bitmap if we have a saved URI
    LaunchedEffect(capturedUriString) {
        capturedUriString?.let { uriString ->
            if (capturedBitmap == null) {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                capturedBitmap = bitmap
            }
        }
    }

    // Load capture box bitmap
    LaunchedEffect(config.captureBoxPath) {
        config.captureBoxPath?.let { path ->
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(path))?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                } catch (e: Exception) {
                    ErrorLogger.log("Error loading capture box: ${e.message}")
                    null
                }
            }
            captureBoxBitmap = bitmap
        }
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    val imageCapture = remember(selectedResolution) {
        val resolutionSelectorBuilder = ResolutionSelector.Builder()
        if (selectedResolution != null) {
            val sensorSize = Size(
                maxOf(selectedResolution.width, selectedResolution.height),
                minOf(selectedResolution.width, selectedResolution.height)
            )
            resolutionSelectorBuilder.setResolutionStrategy(
                ResolutionStrategy(sensorSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            val aspectStrategy = if (selectedResolution.is3_4) {
                AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            } else {
                AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            }
            resolutionSelectorBuilder.setAspectRatioStrategy(aspectStrategy)
        }

        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .setResolutionSelector(resolutionSelectorBuilder.build())
            .build()
    }

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

    LaunchedEffect(selectedResolution) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            imageCapture.targetRotation = rotation

            val resolutionSelectorBuilder = ResolutionSelector.Builder()
            if (selectedResolution != null) {
                val sensorSize = Size(
                    maxOf(selectedResolution.width, selectedResolution.height),
                    minOf(selectedResolution.width, selectedResolution.height)
                )
                resolutionSelectorBuilder.setResolutionStrategy(
                    ResolutionStrategy(sensorSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                )
                val aspectStrategy = if (selectedResolution.is3_4) {
                    AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                } else {
                    AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                }
                resolutionSelectorBuilder.setAspectRatioStrategy(aspectStrategy)
            }
            
            val preview = Preview.Builder()
                .setTargetRotation(rotation)
                .setResolutionSelector(resolutionSelectorBuilder.build())
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
                ErrorLogger.log("Use case binding failed: ${exc.message}")
                Log.e("CaptureScreen", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(isCaptured, isPreviewReady, captureAttempt, isFolderCheckPassed) {
        if (!isCaptured && isPreviewReady && isFolderCheckPassed) {
            // Wait a bit if this is a retry
            if (captureAttempt > 0) delay(2000)
            
            // Reset countdown when starting a new capture session
            countdown = config.countdownSeconds
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            
            // Small buffer to ensure UI is settled
            delay(200)

            val pWidth = if (previewView.width > 0) previewView.width else context.resources.displayMetrics.widthPixels
            val pHeight = if (previewView.height > 0) previewView.height else context.resources.displayMetrics.heightPixels
            
            takePhoto(
                context = context,
                imageCapture = imageCapture,
                captureBoxBitmap = captureBoxBitmap,
                destinationPath = config.destinationPath,
                previewWidth = pWidth,
                previewHeight = pHeight,
                executor = cameraExecutor,
                onCaptured = { uri, bitmap ->
                    ContextCompat.getMainExecutor(context).execute {
                        capturedUriString = uri.toString()
                        capturedBitmap = bitmap
                        isCaptured = true
                    }
                },
                onError = { error ->
                    ContextCompat.getMainExecutor(context).execute {
                        ErrorLogger.log("Capture error: $error")
                        Log.e("CaptureScreen", "Capture error: $error")
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        captureAttempt++
                    }
                }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!isCaptured) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            captureBoxBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
            
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
                    contentScale = ContentScale.Fit
                )
            }
            Button(
                onClick = {
                    UserActionTracker.trackAction("Tocar para otra foto")
                    onNavigateToMain()
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
            ) {
                Text("Toca para otra foto")
            }

            SecretSettingsButton(
                onNavigateToConfig = onNavigateToConfig,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(58.dp)
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

private fun loadProcessAndCropBitmap(
    context: Context,
    file: File,
    previewWidth: Int,
    previewHeight: Int,
    overlay: Bitmap? = null
): Bitmap? {
    try {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null

        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        Log.d("CaptureScreen", "EXIF Orientation: $orientation, Raw Size: ${bitmap.width}x${bitmap.height}")

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
        
        // Front camera mirroring to match preview viewfinder
        matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)

        val orientedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        // Perform center crop to match preview aspect ratio (What You See Is What You Get)
        var finalBitmap = orientedBitmap
        if (previewWidth > 0 && previewHeight > 0) {
            val previewRatio = previewWidth.toFloat() / previewHeight.toFloat()
            val imgRatio = orientedBitmap.width.toFloat() / orientedBitmap.height.toFloat()

            if (kotlin.math.abs(previewRatio - imgRatio) > 0.005f) {
                val cropped = if (previewRatio < imgRatio) {
                    // Preview is taller/narrower -> crop width
                    val targetW = (orientedBitmap.height * previewRatio).toInt().coerceIn(1, orientedBitmap.width)
                    val startX = ((orientedBitmap.width - targetW) / 2).coerceAtLeast(0)
                    Bitmap.createBitmap(orientedBitmap, startX, 0, targetW, orientedBitmap.height)
                } else {
                    // Preview is wider/shorter -> crop height
                    val targetH = (orientedBitmap.width / previewRatio).toInt().coerceIn(1, orientedBitmap.height)
                    val startY = ((orientedBitmap.height - targetH) / 2).coerceAtLeast(0)
                    Bitmap.createBitmap(orientedBitmap, 0, startY, orientedBitmap.width, targetH)
                }
                Log.d("CaptureScreen", "Bitmap cropped from ${orientedBitmap.width}x${orientedBitmap.height} to ${cropped.width}x${cropped.height}")
                finalBitmap = cropped
            }
        }

        // Apply overlay if provided
        if (overlay != null) {
            val mergedBitmap = Bitmap.createBitmap(finalBitmap.width, finalBitmap.height, finalBitmap.config ?: Bitmap.Config.ARGB_8888)
            val canvas = Canvas(mergedBitmap)
            canvas.drawBitmap(finalBitmap, 0f, 0f, null)
            
            // Resize overlay to fit the base bitmap exactly (deforming if necessary, as requested)
            val scaledOverlay = Bitmap.createScaledBitmap(overlay, finalBitmap.width, finalBitmap.height, true)
            canvas.drawBitmap(scaledOverlay, 0f, 0f, null)
            finalBitmap = mergedBitmap
        }

        Log.d("CaptureScreen", "Bitmap processed. Size: ${finalBitmap.width}x${finalBitmap.height}")
        return finalBitmap
    } catch (e: Exception) {
        ErrorLogger.log("Error processing captured bitmap: ${e.message}")
        Log.e("CaptureScreen", "Error processing captured bitmap", e)
        return null
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    captureBoxBitmap: Bitmap?,
    destinationPath: String,
    previewWidth: Int,
    previewHeight: Int,
    executor: ExecutorService,
    onCaptured: (Uri, Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    val tempFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

    Log.d("CaptureScreen", "Paso 1: Iniciando captura en archivo temporal")
    imageCapture.takePicture(
        outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                try {
                    Log.d("CaptureScreen", "Paso 2: Foto capturada. Procesando, recortando y aplicando overlay...")
                    val processedBitmap = loadProcessAndCropBitmap(
                        context = context,
                        file = tempFile,
                        previewWidth = previewWidth,
                        previewHeight = previewHeight,
                        overlay = captureBoxBitmap
                    )
                    if (processedBitmap == null) {
                        ErrorLogger.log("Paso 2: Falló el procesamiento de la imagen")
                        onError("Paso 2: Falló el procesamiento de la imagen")
                        return
                    }

                    Log.d("CaptureScreen", "Paso 3: Guardando imagen en destino final...")
                    val finalUri = saveBitmapToDestination(context, processedBitmap, destinationPath)
                    if (finalUri != null) {
                        Log.d("CaptureScreen", "Paso 3: Guardado completado con éxito")
                        onCaptured(finalUri, processedBitmap)
                    } else {
                        ErrorLogger.log("Paso 3: Falló la creación del archivo final")
                        onError("Paso 3: Falló la creación del archivo final")
                    }
                } catch (e: Exception) {
                    ErrorLogger.log("Paso 3: Error de sistema al guardar (${e.localizedMessage})")
                    Log.e("CaptureScreen", "Error saving file", e)
                    onError("Paso 3: Error de sistema al guardar (${e.localizedMessage})")
                } finally {
                    if (tempFile.exists()) tempFile.delete()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                ErrorLogger.log("Photo capture failed: ${exception.message}")
                Log.e("CaptureScreen", "Photo capture failed: ${exception.message}", exception)
                val msg = when (exception.imageCaptureError) {
                    ImageCapture.ERROR_FILE_IO -> "Paso 1: Error de disco (Espacio o Permiso)"
                    ImageCapture.ERROR_CAMERA_CLOSED -> "Paso 1: Cámara cerrada"
                    else -> "Paso 1: Falló el obturador (Error ${exception.imageCaptureError})"
                }
                onError(msg)
                if (tempFile.exists()) tempFile.delete()
            }
        }
        override fun onError(exception: ImageCaptureException) {
            Log.e("CaptureScreen", "Photo capture failed", exception)
        }
    })
}

private fun saveBitmapToDestination(context: Context, bitmap: Bitmap, destinationPath: String): Uri? {
    val name = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        .format(System.currentTimeMillis()) + ".jpg"

    val destUri = try { Uri.parse(destinationPath) } catch (e: Exception) { null }

    if (destUri?.scheme == "content") {
        val documentFile = DocumentFile.fromTreeUri(context, destUri)
        if (documentFile == null || !documentFile.exists()) {
            ErrorLogger.log("Paso 3: SAF - Carpeta no existe")
            Log.e("CaptureScreen", "Paso 3: SAF - Carpeta no existe")
            return null
        }
        
        val newFile = documentFile.createFile("image/jpeg", name) ?: run {
            ErrorLogger.log("Paso 3: SAF - No se pudo crear archivo")
            Log.e("CaptureScreen", "Paso 3: SAF - No se pudo crear archivo")
            return null
        }
        
        context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        return newFile.uri
    } else {
        val dir = File(destinationPath)
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e("CaptureScreen", "Paso 3: File - No se pudo crear carpeta")
            return null
        }
        
        val targetFile = File(dir, name)
        targetFile.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        return Uri.fromFile(targetFile)
    }
}

private fun checkDestinationWritable(context: Context, path: String): String? {
    try {
        val uri = try { Uri.parse(path) } catch (e: Exception) { null }
        if (uri?.scheme == "content") {
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            if (documentFile == null || !documentFile.exists()) {
                return "Configuración: La carpeta elegida ya no existe"
            }
            if (!documentFile.canWrite()) {
                return "Configuración: No tiene permiso para escribir en esa carpeta"
            }
        } else {
            val dir = File(path)
            if (!dir.exists() && !dir.mkdirs()) {
                return "Configuración: No se puede crear la carpeta en $path"
            }
            if (!dir.canWrite()) {
                return "Configuración: Sin permiso de escritura en $path"
            }
        }
    } catch (e: Exception) {
        return "Error al verificar carpeta: ${e.localizedMessage}"
    }
    return null
}
