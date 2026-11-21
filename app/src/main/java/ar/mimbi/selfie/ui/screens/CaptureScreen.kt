package ar.mimbi.selfie.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect as AndroidRect
import kotlin.math.roundToInt
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap

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

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        cameraGranted = isGranted
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var kioskState by rememberSaveable { mutableStateOf(KioskState.WELCOME) }
    Log.d("CaptureScreen", "CaptureScreen composed (kioskState = $kioskState)")
    var countdown by rememberSaveable { mutableIntStateOf(config.countdownSeconds) }
    var capturedUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSaveFailed by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var captureBoxBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var captureAttempt by remember { mutableIntStateOf(0) }
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
                        context.contentResolver.openInputStream(uriString.toUri())?.use { input ->
                            BitmapFactory.decodeStream(input)
                        }
                    } catch (_: Exception) {
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
                    context.contentResolver.openInputStream(path.toUri())?.use { input ->
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
    LaunchedEffect(cameraGranted, selectedResolution, lifecycleOwner) {
        if (!cameraGranted) return@LaunchedEffect

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
        LaunchedEffect(kioskState, captureAttempt, isFolderCheckPassed, cameraGranted) {
            if (kioskState == KioskState.COUNTDOWN && isFolderCheckPassed && cameraGranted) {
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
                            isSaveFailed = false
                            saveErrorMessage = null
                            capturedUriString = null
                            kioskState = KioskState.CAPTURED
                        }
                    },
                    onPhotoSaved = { uri ->
                        ContextCompat.getMainExecutor(context).execute {
                            capturedUriString = uri.toString()
                            isSaveFailed = false
                            saveErrorMessage = null
                        }
                    },
                    onSaveFailed = { error ->
                        ContextCompat.getMainExecutor(context).execute {
                            isSaveFailed = true
                            saveErrorMessage = error
                            showRedToast(context, error)
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

                    if (isSaveFailed) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = Color(0xFFD32F2F),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "⚠️ NO SE PUDO GUARDAR LA FOTO EN EL ALMACENAMIENTO\n(La impresión está deshabilitada)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

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
                                        isSaveFailed = false
                                        saveErrorMessage = null
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
                                val isPrintEnabled = !isSaveFailed && capturedUriString != null
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            UserActionTracker.trackAction("Tocar botón de imprimir")
                                            showCopiesMenu = true
                                        },
                                        enabled = isPrintEnabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = Color.White,
                                            disabledContainerColor = Color.Transparent,
                                            disabledContentColor = Color.Gray
                                        ),
                                        border = BorderStroke(1.5.dp, if (isPrintEnabled) Color.Gray else Color.DarkGray),
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text(
                                            text = if (showIconOnly) "🖨️" else "🖨️ IMPRIMIR",
                                            color = if (isPrintEnabled) Color.White else Color.Gray,
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
                                isSaveFailed = false
                                saveErrorMessage = null
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

        // LAYER 5: Permission Denied Overlay
        if (!cameraGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "📷",
                        fontSize = 64.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Permiso de cámara requerido",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "La aplicación necesita acceso a la cámara para tomar fotos.",
                        color = Color.LightGray,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "Conceder permiso",
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        ) {
                            Text(
                                text = "Abrir ajustes",
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Settings button on top (available on Welcome, Captured, or Permission screens)
        if (kioskState == KioskState.WELCOME || kioskState == KioskState.CAPTURED || !cameraGranted) {
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

        val orientMatrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> orientMatrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> orientMatrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                orientMatrix.postRotate(180f)
                orientMatrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                orientMatrix.postRotate(90f)
                orientMatrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> orientMatrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                orientMatrix.postRotate(270f)
                orientMatrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> orientMatrix.postRotate(270f)
        }

        val rawRect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val orientedBounds = RectF()
        orientMatrix.mapRect(orientedBounds, rawRect)
        orientMatrix.postTranslate(-orientedBounds.left, -orientedBounds.top)

        val orientedWidth = orientedBounds.width().roundToInt()
        val orientedHeight = orientedBounds.height().roundToInt()

        // Front camera mirroring around center
        val totalTransform = Matrix(orientMatrix)
        totalTransform.postScale(-1f, 1f, orientedWidth / 2f, orientedHeight / 2f)

        val outW = outputRegion.width
        val outH = outputRegion.height
        val srcCropRect = outputRegion.cropRect.toAndroidRect()

        // Clamp crop rect to oriented bounds
        srcCropRect.left = srcCropRect.left.coerceIn(0, orientedWidth)
        srcCropRect.top = srcCropRect.top.coerceIn(0, orientedHeight)
        srcCropRect.right = srcCropRect.right.coerceIn(srcCropRect.left, orientedWidth)
        srcCropRect.bottom = srcCropRect.bottom.coerceIn(srcCropRect.top, orientedHeight)

        Log.d("CaptureScreen", "Output region R: ${outW}x${outH} (aspect ${outputRegion.aspect})")

        // Translate transform matrix so crop origin aligns with (0,0) of result bitmap
        totalTransform.postTranslate(-srcCropRect.left.toFloat(), -srcCropRect.top.toFloat())

        val resultBitmap = createBitmap(outW, outH)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        canvas.drawBitmap(bitmap, totalTransform, paint)
        bitmap.recycle()

        if (overlay != null) {
            val dstRect = AndroidRect(0, 0, outW, outH)
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

@Suppress("DEPRECATION")
private fun showRedToast(context: Context, message: String) {
    ContextCompat.getMainExecutor(context).execute {
        try {
            val textView = android.widget.TextView(context).apply {
                text = message
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(0xFFD32F2F.toInt())
                setPadding(32, 24, 32, 24)
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val toast = Toast(context.applicationContext).apply {
                duration = Toast.LENGTH_LONG
                view = textView
            }
            toast.show()
        } catch (_: Exception) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
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
    onSaveFailed: (String) -> Unit,
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

                    // Show result screen immediately!
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
                            onSaveFailed("Paso 3: Falló la creación del archivo final")
                        }
                    }
                } catch (e: Exception) {
                    image.close()
                    ErrorLogger.log("Paso 3: Error de sistema al procesar/guardar (${e.localizedMessage})")
                    Log.e("CaptureScreen", "Error saving file from RAM", e)
                    onSaveFailed("Paso 3: Error de sistema al procesar/guardar (${e.localizedMessage})")
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
    } catch (_: Exception) {
        0L
    }
}

private fun saveBitmapToDestination(context: Context, bitmap: Bitmap, destinationPath: String): Uri? {
    val name = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        .format(System.currentTimeMillis()) + ".jpg"

    val destUri = try { destinationPath.toUri() } catch (e: Exception) { null }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = if (destinationPath.contains("Pictures/")) {
                val sub = destinationPath.substringAfter("Pictures/").trim('/')
                if (sub.isNotEmpty()) "${Environment.DIRECTORY_PICTURES}/$sub" else Environment.DIRECTORY_PICTURES
            } else if (destinationPath.startsWith(Environment.DIRECTORY_PICTURES)) {
                destinationPath
            } else {
                "${Environment.DIRECTORY_PICTURES}/selfie"
            }

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            }

            val imageUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: run {
                    ErrorLogger.log("Paso 3: MediaStore - Falló inserción")
                    Log.e("CaptureScreen", "Paso 3: MediaStore - Falló inserción")
                    return null
                }

            try {
                context.contentResolver.openOutputStream(imageUri)?.use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)) {
                        ErrorLogger.log("Paso 3: MediaStore - Falló compresión de bitmap")
                        return null
                    }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(imageUri, values, null, null)
                return imageUri
            } catch (e: Exception) {
                ErrorLogger.log("Paso 3: MediaStore - Error al escribir archivo: ${e.message}")
                Log.e("CaptureScreen", "Paso 3: MediaStore - Error al escribir archivo", e)
                try { context.contentResolver.delete(imageUri, null, null) } catch (_: Exception) {}
                return null
            }
        } else {
            val fullPath = if (!destinationPath.startsWith("/")) {
                "${Environment.getExternalStorageDirectory().path}/$destinationPath"
            } else {
                destinationPath
            }
            val dir = File(fullPath)
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
}

private fun checkDestinationWritable(context: Context, path: String): String? {
    try {
        val uri = try { path.toUri() } catch (e: Exception) { null }
        if (uri?.scheme == "content") {
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            if (documentFile == null || !documentFile.exists()) {
                return "Configuración: La carpeta elegida ya no existe"
            }
            if (!documentFile.canWrite()) {
                return "Configuración: No tiene permiso para escribir en esa carpeta"
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return null
            } else {
                val fullPath = if (!path.startsWith("/")) {
                    "${Environment.getExternalStorageDirectory().path}/$path"
                } else {
                    path
                }
                val dir = File(fullPath)
                if (!dir.exists() && !dir.mkdirs()) {
                    return "Configuración: No se puede crear la carpeta en $fullPath"
                }
                if (!dir.canWrite()) {
                    return "Configuración: Sin permiso de escritura en $fullPath"
                }
            }
        }
    } catch (e: Exception) {
        return "Error al verificar carpeta: ${e.localizedMessage}"
    }
    return null
}
