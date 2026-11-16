package ar.mimbi.selfie.ui.screens

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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.mimbi.selfie.data.AppConfig
import ar.mimbi.selfie.data.CameraResolutionHelper
import ar.mimbi.selfie.data.DecorationFit
import ar.mimbi.selfie.data.ErrorLogger
import ar.mimbi.selfie.data.ImageGeometryHelper
import ar.mimbi.selfie.data.OutputRegion
import ar.mimbi.selfie.data.UserActionTracker
import ar.mimbi.selfie.ui.components.SecretSettingsButton
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds

enum class KioskState {
    WELCOME,
    COUNTDOWN,
    CAPTURED
}

@Composable
fun CaptureScreen(
    config: AppConfig,
    onNavigateToConfig: () -> Unit,
    onImageReady: () -> Unit = {},
    onPrint: (photoUri: String, copies: Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var kioskState by rememberSaveable { mutableStateOf(KioskState.WELCOME) }
    Log.d("CaptureScreen", "CaptureScreen composed (kioskState = $kioskState)")
    var countdown by rememberSaveable { mutableStateOf(config.countdownSeconds) }
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

    // Pre-check folder access
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

    // Preload capture box bitmap into memory
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
        } ?: run {
            captureBoxBitmap = null
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
            val aspectStrategy = if (selectedResolution.is3To4) {
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

    // Observer for camera stream state
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

    // Bind CameraX Use Cases (runs upon composition, streams in background behind Welcome screen)
    LaunchedEffect(selectedResolution, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            imageCapture.targetRotation = rotation

            val previewSelectorBuilder = ResolutionSelector.Builder()
            if (selectedResolution != null) {
                val aspectStrategy = if (selectedResolution.is3To4) {
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
                    it.surfaceProvider = previewView.surfaceProvider
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

    // Unbind camera & release executor when this screen is disposed (e.g. entering ConfigurationScreen)
    DisposableEffect(Unit) {
        onDispose {
            Log.d("CaptureScreen", "CaptureScreen disposed: Unbinding camera and shutting down executor")
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                cameraProvider.unbindAll()
            } catch (e: Exception) {
                Log.e("CaptureScreen", "Error unbinding camera on dispose", e)
            }
            cameraExecutor.shutdown()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val isPortraitScreen = containerWidthPx < containerHeightPx
        val containerAspect = if (containerHeightPx > 0f) containerWidthPx / containerHeightPx else 1.0f

        val rawW = realSensorSize?.width ?: selectedResolution?.width ?: 1920
        val rawH = realSensorSize?.height ?: selectedResolution?.height ?: 1080

        val frameW = if (isPortraitScreen) minOf(rawW, rawH) else maxOf(rawW, rawH)
        val frameH = if (isPortraitScreen) maxOf(rawW, rawH) else minOf(rawW, rawH)

        val outputRegion = remember(frameW, frameH, config.previewFit, config.fixedAspect, containerAspect) {
            ImageGeometryHelper.calculateOutputRegion(
                frameW = frameW,
                frameH = frameH,
                previewFit = config.previewFit,
                fixedAspect = config.fixedAspect,
                screenAspect = containerAspect
            )
        }

        val fittedSize = remember(containerWidthPx, containerHeightPx, outputRegion.aspect) {
            ImageGeometryHelper.fitSize(containerWidthPx, containerHeightPx, outputRegion.aspect)
        }

        val fittedWidthDp = with(density) { fittedSize.width.toDp() }
        val fittedHeightDp = with(density) { fittedSize.height.toDp() }

        // Trigger countdown and photo capture when state changes to COUNTDOWN
        LaunchedEffect(kioskState, captureAttempt, isFolderCheckPassed) {
            if (kioskState == KioskState.COUNTDOWN && isFolderCheckPassed) {
                if (captureAttempt > 0) delay(2000.milliseconds)

                countdown = config.countdownSeconds
                while (countdown > 0) {
                    delay(1000.milliseconds)
                    countdown--
                }

                takePhoto(
                    context = context,
                    imageCapture = imageCapture,
                    captureBoxBitmap = captureBoxBitmap,
                    destinationPath = config.destinationPath,
                    outputRegion = outputRegion,
                    decorationFit = config.decorationFit,
                    executor = cameraExecutor,
                    scope = scope,
                    onPhotoProcessed = { bitmap ->
                        ContextCompat.getMainExecutor(context).execute {
                            capturedBitmap = bitmap
                            kioskState = KioskState.CAPTURED
                        }
                    },
                    onPhotoSaved = { uri ->
                        if (capturedUriString != uri.toString()) {
                            capturedUriString = uri.toString()
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

        // LAYER 1: Permanent Camera Preview Feed & Capture Box Overlay
        // AndroidView(previewView) is ALWAYS present in composition so SurfaceView is NEVER destroyed.
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

                if (kioskState == KioskState.COUNTDOWN) {
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
            }
        }

        // LAYER 2: Welcome Overlay (Front image or default message covering camera preview)
        if (kioskState == KioskState.WELCOME) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable {
                        UserActionTracker.trackAction("Tocar para capturar")
                        kioskState = KioskState.COUNTDOWN
                    }
            ) {
                if (config.frontPath != null) {
                    SubcomposeAsyncImage(
                        model = config.frontPath,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    ) {
                        val state = painter.state
                        LaunchedEffect(state) {
                            if (state is AsyncImagePainter.State.Success || state is AsyncImagePainter.State.Error) {
                                onImageReady()
                            }
                        }
                        if (state is AsyncImagePainter.State.Error || state is AsyncImagePainter.State.Empty) {
                            KioskDefaultMessage()
                        } else {
                            SubcomposeAsyncImageContent()
                        }
                    }
                } else {
                    KioskDefaultMessage()
                    LaunchedEffect(Unit) {
                        onImageReady()
                    }
                }
            }
        }

        // LAYER 3: Countdown Text Overlay
        if (kioskState == KioskState.COUNTDOWN) {
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

        // LAYER 4: Captured Result Overlay
        if (kioskState == KioskState.CAPTURED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
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

                    val remainingPrints = (config.maxPrintCount - config.printCount).coerceAtLeast(0)
                    if (config.showPrintButton && remainingPrints > 0) {
                        var showCopiesMenu by remember { mutableStateOf(false) }
                        var showRemainingPrintsDialog by remember { mutableStateOf(false) }
                        var selectedCopies by remember { mutableStateOf<Int?>(null) }
                        var showConfirmationDialog by remember { mutableStateOf(false) }

                        if (showRemainingPrintsDialog) {
                            AlertDialog(
                                onDismissRequest = { showRemainingPrintsDialog = false },
                                title = { Text("Impresiones restantes") },
                                text = { Text("Este es el número de impresiones restantes.") },
                                confirmButton = {
                                    TextButton(onClick = { showRemainingPrintsDialog = false }) {
                                        Text("Aceptar")
                                    }
                                }
                            )
                        }

                        if (showConfirmationDialog && selectedCopies != null) {
                            val copies = selectedCopies!!
                            AlertDialog(
                                onDismissRequest = {
                                    showConfirmationDialog = false
                                    selectedCopies = null
                                    showCopiesMenu = true
                                },
                                title = { Text("Confirmar impresión") },
                                text = { Text("¿Desea imprimir $copies ${if (copies == 1) "copia" else "copias"}?") },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showConfirmationDialog = false
                                            selectedCopies = null
                                            UserActionTracker.trackAction("Imprimir $copies copias")
                                            onPrint(capturedUriString ?: "", copies)
                                            Toast.makeText(
                                                context,
                                                "Foto agregada a la cola de impresión ($copies ${if (copies == 1) "copia" else "copias"})",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    ) {
                                        Text("Confirmar")
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            showConfirmationDialog = false
                                            selectedCopies = null
                                            showCopiesMenu = true
                                        }
                                    ) {
                                        Text("Cancelar")
                                    }
                                }
                            )
                        }

                        if (showCopiesMenu) {
                            Dialog(onDismissRequest = { showCopiesMenu = false }) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "Seleccionar cantidad de copias",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))

                                        for (row in 0..2) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            ) {
                                                for (col in 1..3) {
                                                    val copies = row * 3 + col
                                                    val isEnabled = copies <= remainingPrints
                                                    OutlinedButton(
                                                        onClick = {
                                                            showCopiesMenu = false
                                                            selectedCopies = copies
                                                            showConfirmationDialog = true
                                                        },
                                                        enabled = isEnabled,
                                                        modifier = Modifier.size(56.dp),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) {
                                                        Text(
                                                            text = "$copies",
                                                            fontSize = 18.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        TextButton(onClick = { showCopiesMenu = false }) {
                                            Text("Cancelar")
                                        }
                                    }
                                }
                            }
                        }

                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            val showIconOnly = maxWidth < 380.dp

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Button 1: Another picture
                                Button(
                                    onClick = {
                                        UserActionTracker.trackAction("OTRA FOTO")
                                        capturedBitmap = null
                                        capturedUriString = null
                                        kioskState = KioskState.WELCOME
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = Color.White
                                    ),
                                    border = BorderStroke(1.5.dp, Color.Gray),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text(
                                        text = if (showIconOnly) "📸" else "📸 OTRA FOTO",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }

                                // Button 2: Print & Counter
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            UserActionTracker.trackAction("Tocar botón de imprimir")
                                            showCopiesMenu = true
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = Color.White
                                        ),
                                        border = BorderStroke(1.5.dp, Color.Gray),
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text(
                                            text = if (showIconOnly) "🖨️" else "🖨️ IMPRIMIR",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }

                                    // Counter badge to the right of printing button
                                    Surface(
                                        onClick = {
                                            UserActionTracker.trackAction("Ver impresiones restantes")
                                            showRemainingPrintsDialog = true
                                        },
                                        shape = CircleShape,
                                        color = Color.DarkGray.copy(alpha = 0.8f),
                                        border = BorderStroke(1.dp, Color.Gray),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 10.dp)
                                        ) {
                                            Text(
                                                text = "$remainingPrints",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                UserActionTracker.trackAction("OTRA FOTO")
                                capturedBitmap = null
                                capturedUriString = null
                                kioskState = KioskState.WELCOME
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
                }
            }
        }

        // Settings button on top (available on Welcome and Captured screens)
        if (kioskState == KioskState.WELCOME || kioskState == KioskState.CAPTURED) {
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

@Composable
private fun KioskDefaultMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Toca aqui para foto",
            color = Color.White,
            fontSize = 32.sp
        )
    }
}

private fun loadProcessAndDecorateImageProxy(
    imageProxy: ImageProxy,
    outputRegion: OutputRegion,
    decorationFit: DecorationFit,
    overlay: Bitmap? = null
): Bitmap? {
    try {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

        val exif = ExifInterface(java.io.ByteArrayInputStream(bytes))
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )

        Log.d("CaptureScreen", "In-Memory EXIF Orientation: $orientation, Raw Size: ${bitmap.width}x${bitmap.height}")

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

        val orientedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (orientedBitmap != bitmap) {
            bitmap.recycle()
        }

        // Front camera mirroring around center
        val mirrorMatrix = Matrix().apply {
            postScale(-1f, 1f, orientedBitmap.width / 2f, orientedBitmap.height / 2f)
        }
        val mirroredBitmap = Bitmap.createBitmap(orientedBitmap, 0, 0, orientedBitmap.width, orientedBitmap.height, mirrorMatrix, true)
        if (mirroredBitmap != orientedBitmap) {
            orientedBitmap.recycle()
        }

        val outW = outputRegion.width
        val outH = outputRegion.height
        val srcCropRect = outputRegion.cropRect.toAndroidRect()

        // Clamp crop rect to mirroredBitmap bounds
        srcCropRect.left = srcCropRect.left.coerceIn(0, mirroredBitmap.width)
        srcCropRect.top = srcCropRect.top.coerceIn(0, mirroredBitmap.height)
        srcCropRect.right = srcCropRect.right.coerceIn(srcCropRect.left, mirroredBitmap.width)
        srcCropRect.bottom = srcCropRect.bottom.coerceIn(srcCropRect.top, mirroredBitmap.height)

        Log.d("CaptureScreen", "Output region R: ${outW}x${outH} (aspect ${outputRegion.aspect})")

        val resultBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        val dstRect = AndroidRect(0, 0, outW, outH)
        canvas.drawBitmap(mirroredBitmap, srcCropRect, dstRect, paint)
        mirroredBitmap.recycle()

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

        Log.d("CaptureScreen", "In-Memory Bitmap processed & decorated. Final size: ${resultBitmap.width}x${resultBitmap.height}")
        return resultBitmap
    } catch (e: Exception) {
        ErrorLogger.log("Error processing in-memory captured bitmap: ${e.message}")
        Log.e("CaptureScreen", "Error processing in-memory captured bitmap", e)
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
    scope: kotlinx.coroutines.CoroutineScope,
    onPhotoProcessed: (Bitmap) -> Unit,
    onPhotoSaved: (Uri) -> Unit,
    onError: (String) -> Unit
) {
    Log.d("CaptureScreen", "Paso 1: Iniciando captura en memoria (RAM)")
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureStarted() {
                super.onCaptureStarted()
                Log.d("CaptureScreen", "Obturador activado en el sensor de la cámara")
            }

            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    Log.d("CaptureScreen", "Paso 2: Foto capturada en RAM. Procesando, recortando y decorando...")
                    val processedBitmap = loadProcessAndDecorateImageProxy(
                        imageProxy = image,
                        outputRegion = outputRegion,
                        decorationFit = decorationFit,
                        overlay = captureBoxBitmap
                    )
                    image.close()

                    if (processedBitmap == null) {
                        ErrorLogger.log("Paso 2: Falló el procesamiento de la imagen en memoria")
                        onError("Paso 2: Falló el procesamiento de la imagen en memoria")
                        return
                    }

                    // Tweak 1: Show result screen immediately!
                    onPhotoProcessed(processedBitmap)

                    // Save to disk asynchronously in background without blocking UI
                    scope.launch(Dispatchers.IO) {
                        Log.d("CaptureScreen", "Paso 3: Guardando imagen final en destino (en segundo plano)...")
                        val finalUri = saveBitmapToDestination(context, processedBitmap, destinationPath)
                        if (finalUri != null) {
                            val savedSizeBytes = getSavedFileSize(context, finalUri)
                            Log.d("CaptureScreen", "Saved JPEG: ${processedBitmap.width}x${processedBitmap.height}, $savedSizeBytes bytes")
                            Log.d("CaptureScreen", "Paso 3: Guardado completado con éxito")
                            onPhotoSaved(finalUri)
                        } else {
                            ErrorLogger.log("Paso 3: Falló la creación del archivo final")
                            onError("Paso 3: Falló la creación del archivo final")
                        }
                    }
                } catch (e: Exception) {
                    image.close()
                    ErrorLogger.log("Paso 3: Error de sistema al procesar/guardar (${e.localizedMessage})")
                    Log.e("CaptureScreen", "Error saving file from RAM", e)
                    onError("Paso 3: Error de sistema al procesar/guardar (${e.localizedMessage})")
                }
            }

            override fun onError(exception: ImageCaptureException) {
                ErrorLogger.log("Photo capture failed: ${exception.message}")
                Log.e("CaptureScreen", "Photo capture failed: ${exception.message}", exception)
                val msg = when (exception.imageCaptureError) {
                    ImageCapture.ERROR_FILE_IO -> "Paso 1: Error de disco"
                    ImageCapture.ERROR_CAMERA_CLOSED -> "Paso 1: Cámara cerrada"
                    else -> "Paso 1: Falló el obturador (Error ${exception.imageCaptureError})"
                }
                onError(msg)
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
