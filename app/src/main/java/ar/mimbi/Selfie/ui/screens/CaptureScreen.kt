package ar.mimbi.Selfie.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect as AndroidRect
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import ar.mimbi.Selfie.data.AppConfig
import ar.mimbi.Selfie.data.CameraResolutionHelper
import ar.mimbi.Selfie.data.ConfigDataStore
import ar.mimbi.Selfie.data.DecorationFit
import ar.mimbi.Selfie.data.ErrorLogger
import ar.mimbi.Selfie.data.ImageGeometryHelper
import ar.mimbi.Selfie.data.OutputRegion
import ar.mimbi.Selfie.data.UserActionTracker
import ar.mimbi.Selfie.ui.components.SecretSettingsButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()

    var countdown by rememberSaveable { mutableStateOf(config.countdownSeconds) }
    var isCaptured by rememberSaveable { mutableStateOf(false) }
    var capturedUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var captureBoxBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var captureAttempt by remember { mutableStateOf(0) }
    var isFolderCheckPassed by remember { mutableStateOf(false) }
    var folderErrorMessage by remember { mutableStateOf<String?>(null) }

    val selectedResolution = remember(config.inputResolution) {
        CameraResolutionHelper.parseResolution(config.inputResolution)
            ?: CameraResolutionHelper.getMaxResolution(context)
    }

    var realSensorSize by remember { mutableStateOf<Size?>(null) }

    val displayMetrics = context.resources.displayMetrics
    val screenWidthPx = displayMetrics.widthPixels.toFloat()
    val screenHeightPx = displayMetrics.heightPixels.toFloat()
    val screenAspect = if (screenHeightPx > 0f) screenWidthPx / screenHeightPx else 1.0f

    val frameW = realSensorSize?.width ?: selectedResolution?.width ?: 1920
    val frameH = realSensorSize?.height ?: selectedResolution?.height ?: 1080

    val outputRegion = remember(frameW, frameH, config.previewFit, config.fixedAspect, screenAspect) {
        ImageGeometryHelper.calculateOutputRegion(
            frameW = frameW,
            frameH = frameH,
            previewFit = config.previewFit,
            fixedAspect = config.fixedAspect,
            screenAspect = screenAspect
        )
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
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(100)
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

            val previewSelectorBuilder = ResolutionSelector.Builder()
            if (selectedResolution != null) {
                val aspectStrategy = if (selectedResolution.is3_4) {
                    AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                } else {
                    AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                }
                previewSelectorBuilder.setAspectRatioStrategy(aspectStrategy)
            }

            val preview = Preview.Builder()
                .setTargetRotation(rotation)
                .setResolutionSelector(previewSelectorBuilder.build())
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageCapture
                )
                imageCapture.resolutionInfo?.resolution?.let { res ->
                    realSensorSize = res
                    Log.d("CaptureScreen", "Bound ImageCapture real resolution: ${res.width}x${res.height}")
                }
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

            takePhoto(
                context = context,
                imageCapture = imageCapture,
                captureBoxBitmap = captureBoxBitmap,
                destinationPath = config.destinationPath,
                outputRegion = outputRegion,
                decorationFit = config.decorationFit,
                executor = cameraExecutor,
                onCaptured = { uri, bitmap, savedSizeBytes ->
                    ContextCompat.getMainExecutor(context).execute {
                        capturedUriString = uri.toString()
                        capturedBitmap = bitmap
                        isCaptured = true

                        if (outputRegion.totalPixels > 0 && savedSizeBytes > 0) {
                            val newBpp = savedSizeBytes.toDouble() / outputRegion.totalPixels.toDouble()
                            val currentCount = config.savedSampleCount
                            val updatedCount = currentCount + 1
                            val updatedBpp = (config.lastSavedBytesPerPixel * currentCount + newBpp) / updatedCount
                            val updatedConfig = config.copy(
                                lastSavedBytesPerPixel = updatedBpp,
                                savedSampleCount = updatedCount
                            )
                            scope.launch(Dispatchers.IO) {
                                ConfigDataStore(context).saveConfig(updatedConfig)
                            }
                        }
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }

        val fittedSize = remember(containerWidthPx, containerHeightPx, outputRegion.aspect) {
            ImageGeometryHelper.fitSize(containerWidthPx, containerHeightPx, outputRegion.aspect)
        }

        val fittedWidthDp = with(density) { fittedSize.width.toDp() }
        val fittedHeightDp = with(density) { fittedSize.height.toDp() }

        if (!isCaptured) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(fittedWidthDp, fittedHeightDp)
                        .clipToBounds()
                ) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                    captureBoxBitmap?.let {
                        val contentScale = if (config.decorationFit == DecorationFit.COVER) {
                            ContentScale.Crop
                        } else {
                            ContentScale.FillBounds
                        }
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = contentScale
                        )
                    }
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
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp, bottom = 48.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "¡TU FOTO ESTÁ LISTA!",
                        color = Color.Yellow,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        capturedBitmap?.let { bitmap ->
                            val imageRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                            val containerRatio = maxWidth.value / maxHeight.value

                            val (displayWidth, displayHeight) = if (imageRatio > containerRatio) {
                                maxWidth to (maxWidth.value / imageRatio).dp
                            } else {
                                (maxHeight.value * imageRatio).dp to maxHeight
                            }

                            Box(
                                modifier = Modifier
                                    .size(displayWidth, displayHeight)
                                    .clip(RoundedCornerShape(32.dp))
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            UserActionTracker.trackAction("OTRA FOTO")
                            onNavigateToMain()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.5.dp, Color.Gray),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = "📸 OTRA FOTO",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }

                SecretSettingsButton(
                    onNavigateToConfig = onNavigateToConfig,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(58.dp)
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d("CaptureScreen", "CaptureScreen disposed")
            cameraExecutor.shutdown()
        }
    }
}

private fun loadProcessAndDecorateBitmap(
    context: Context,
    file: File,
    outputRegion: OutputRegion,
    decorationFit: DecorationFit,
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
        if (orientedBitmap != bitmap) {
            bitmap.recycle()
        }

        val outW = outputRegion.width
        val outH = outputRegion.height
        val srcCropRect = outputRegion.cropRect.toAndroidRect()

        Log.d("CaptureScreen", "Output region R: ${outW}x${outH} (aspect ${outputRegion.aspect})")

        val resultBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        val dstRect = AndroidRect(0, 0, outW, outH)
        canvas.drawBitmap(orientedBitmap, srcCropRect, dstRect, paint)
        orientedBitmap.recycle()

        if (overlay != null) {
            when (decorationFit) {
                DecorationFit.STRETCH -> {
                    val overlaySrc = AndroidRect(0, 0, overlay.width, overlay.height)
                    canvas.drawBitmap(overlay, overlaySrc, dstRect, paint)
                }
                DecorationFit.COVER -> {
                    val decoCrop = ImageGeometryHelper.coverCrop(
                        frameW = overlay.width,
                        frameH = overlay.height,
                        targetAspect = outputRegion.aspect
                    )
                    canvas.drawBitmap(overlay, decoCrop.toAndroidRect(), dstRect, paint)
                }
            }
        }

        Log.d("CaptureScreen", "Bitmap processed & decorated. Final size: ${resultBitmap.width}x${resultBitmap.height}")
        return resultBitmap
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
    outputRegion: OutputRegion,
    decorationFit: DecorationFit,
    executor: ExecutorService,
    onCaptured: (Uri, Bitmap, Long) -> Unit,
    onError: (String) -> Unit
) {
    val tempFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

    Log.d("CaptureScreen", "Paso 1: Iniciando captura en archivo temporal")
    imageCapture.takePicture(
        outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                try {
                    Log.d("CaptureScreen", "ImageCapture resolutionInfo: ${imageCapture.resolutionInfo?.resolution}")
                    Log.d("CaptureScreen", "Paso 2: Foto capturada. Procesando, recortando y aplicando overlay...")
                    val processedBitmap = loadProcessAndDecorateBitmap(
                        context = context,
                        file = tempFile,
                        outputRegion = outputRegion,
                        decorationFit = decorationFit,
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
                        val savedSizeBytes = getSavedFileSize(context, finalUri)
                        Log.d("CaptureScreen", "Saved JPEG: ${processedBitmap.width}x${processedBitmap.height}, $savedSizeBytes bytes")
                        Log.d("CaptureScreen", "Paso 3: Guardado completado con éxito")
                        onCaptured(finalUri, processedBitmap, savedSizeBytes)
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
    )
}

private fun getSavedFileSize(context: Context, uri: Uri): Long {
    return try {
        if (uri.scheme == "content") {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                pfd.statSize
            } ?: 0L
        } else {
            File(uri.path ?: "").length()
        }
    } catch (e: Exception) {
        0L
    }
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
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
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
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
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
