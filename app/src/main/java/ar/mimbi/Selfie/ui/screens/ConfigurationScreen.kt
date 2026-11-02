package ar.mimbi.Selfie.ui.screens

import android.content.pm.ActivityInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import java.io.File
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.mimbi.Selfie.BuildConfig
import ar.mimbi.Selfie.findActivity
import ar.mimbi.Selfie.data.AppConfig
import ar.mimbi.Selfie.data.AppOrientation
import ar.mimbi.Selfie.data.CameraResolution
import ar.mimbi.Selfie.data.CameraResolutionHelper
import ar.mimbi.Selfie.data.DecorationFit
import ar.mimbi.Selfie.data.ErrorLogger
import ar.mimbi.Selfie.data.FixedAspect
import ar.mimbi.Selfie.data.ImageGeometryHelper
import ar.mimbi.Selfie.data.PreviewFit
import ar.mimbi.Selfie.data.UserActionTracker
import ar.mimbi.Selfie.ui.components.SecretSettingsButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConfigurationScreen(
    initialConfig: AppConfig,
    onSave: (AppConfig) -> Unit,
    onClose: () -> Unit,
    onNavigateToErrorHistory: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) {
            focusManager.clearFocus()
        }
    }

    val countdownFocusRequester = remember { BringIntoViewRequester() }
    var countdownFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isKeyboardVisible, countdownFocused) {
        if (isKeyboardVisible && countdownFocused) {
            countdownFocusRequester.bringIntoView()
        }
    }

    var portadaPath by rememberSaveable { mutableStateOf(initialConfig.portadaPath) }
    var captureBoxPath by rememberSaveable { mutableStateOf(initialConfig.captureBoxPath) }
    var countdownSeconds by rememberSaveable { mutableStateOf(initialConfig.countdownSeconds.toString()) }
    var destinationPath by rememberSaveable { mutableStateOf(initialConfig.destinationPath) }

    val nativeSizes = remember { CameraResolutionHelper.getNativeSizes(context) }
    val defaultResKey = remember { nativeSizes.sizes.firstOrNull()?.key }

    var pictureResolution by rememberSaveable {
        mutableStateOf(initialConfig.inputResolution ?: defaultResKey)
    }
    var previewFit by rememberSaveable { mutableStateOf(initialConfig.previewFit) }
    var fixedAspect by rememberSaveable { mutableStateOf(initialConfig.fixedAspect) }
    var decorationFit by rememberSaveable { mutableStateOf(initialConfig.decorationFit) }
    var appOrientation by rememberSaveable { mutableStateOf(initialConfig.appOrientation) }

    DisposableEffect(appOrientation) {
        val activity = context.findActivity()
        if (activity != null) {
            val targetOrientation = when (appOrientation) {
                AppOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                AppOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                AppOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            }
            Log.d("ConfigScreen", "Applying live requestedOrientation=$targetOrientation for $appOrientation")
            activity.requestedOrientation = targetOrientation
        }
        onDispose {}
    }

    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }

    val displayMetrics = context.resources.displayMetrics
    val screenW = displayMetrics.widthPixels
    val screenH = displayMetrics.heightPixels
    val screenAspect = if (screenH > 0) screenW.toFloat() / screenH.toFloat() else 1.0f
    val screenAspectFormatted = ImageGeometryHelper.formatAspectRatio(screenW, screenH)

    val portadaDimensions by produceState<Pair<Int, Int>?>(initialValue = null, key1 = portadaPath) {
        value = withContext(Dispatchers.IO) {
            getImageDimensions(context, portadaPath)
        }
    }

    val captureBoxDimensions by produceState<Pair<Int, Int>?>(initialValue = null, key1 = captureBoxPath) {
        value = withContext(Dispatchers.IO) {
            getImageDimensions(context, captureBoxPath)
        }
    }

    val hasChanges = portadaPath != initialConfig.portadaPath ||
            captureBoxPath != initialConfig.captureBoxPath ||
            countdownSeconds != initialConfig.countdownSeconds.toString() ||
            destinationPath != initialConfig.destinationPath ||
            pictureResolution != (initialConfig.inputResolution ?: defaultResKey) ||
            previewFit != initialConfig.previewFit ||
            fixedAspect != initialConfig.fixedAspect ||
            decorationFit != initialConfig.decorationFit ||
            appOrientation != initialConfig.appOrientation

    BackHandler(enabled = hasChanges) {
        focusManager.clearFocus()
        UserActionTracker.trackAction("Cerrar configuración (atrás físico/gesto)")
        showUnsavedDialog = true
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        UserActionTracker.trackAction("Seleccionar imagen de portada")
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                ErrorLogger.log("Error al tomar permisos de imagen: ${e.message}")
                e.printStackTrace()
            }
            portadaPath = it.toString()
        }
    }

    val captureBoxPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        UserActionTracker.trackAction("Seleccionar cuadro de captura")
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                ErrorLogger.log("Error al tomar permisos de cuadro de captura: ${e.message}")
                e.printStackTrace()
            }
            captureBoxPath = it.toString()
        }
    }

    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        UserActionTracker.trackAction("Seleccionar carpeta de destino")
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                ErrorLogger.log("Error al tomar permisos de carpeta: ${e.message}")
                e.printStackTrace()
            }
            destinationPath = it.toString()
        }
    }

    SelectionContainer {
        Scaffold(
            topBar = {
                DisableSelection {
                    CenterAlignedTopAppBar(
                        title = { Text("Configuración") },
                        actions = {
                            IconButton(onClick = {
                                focusManager.clearFocus()
                                UserActionTracker.trackAction("Guardar configuración")
                                val finalSeconds = countdownSeconds.toIntOrNull() ?: 0
                                onSave(
                                    AppConfig(
                                        portadaPath = portadaPath,
                                        captureBoxPath = captureBoxPath,
                                        countdownSeconds = finalSeconds,
                                        destinationPath = destinationPath,
                                        inputResolution = pictureResolution,
                                        previewFit = previewFit,
                                        fixedAspect = fixedAspect,
                                        decorationFit = decorationFit,
                                        appOrientation = appOrientation,
                                        lastSavedBytesPerPixel = initialConfig.lastSavedBytesPerPixel,
                                        savedSampleCount = initialConfig.savedSampleCount
                                    )
                                )
                                Toast.makeText(context, "Configuración guardada", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.Save, contentDescription = "Guardar")
                            }
                            IconButton(onClick = {
                                focusManager.clearFocus()
                                UserActionTracker.trackAction("Cerrar configuración")
                                if (hasChanges) {
                                    showUnsavedDialog = true
                                } else {
                                    onClose()
                                }
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar")
                            }
                        }
                    )
                }
            }
        ) { padding ->
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                focusManager.clearFocus()
                            }
                        )
                    }
                    .padding(padding)
                    .padding(16.dp)
                    .imePadding()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                DisableSelection {
                    // Portada
                    Column {
                        Text("Portada", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        portadaPath?.let { path ->
                            SubcomposeAsyncImage(
                                model = path,
                                contentDescription = "Vista previa de portada",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(Color.Gray.copy(alpha = 0.2f)),
                                contentScale = ContentScale.Fit
                            ) {
                                val state = painter.state
                                if (state is AsyncImagePainter.State.Error || state is AsyncImagePainter.State.Empty) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Error al cargar imagen", color = Color.Red)
                                    }
                                } else {
                                    SubcomposeAsyncImageContent()
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            portadaDimensions?.let { (w, h) ->
                                val portadaAspect = w.toFloat() / h.toFloat()
                                val portadaAspectFormatted = ImageGeometryHelper.formatAspectRatio(w, h)
                                if (abs(portadaAspect - screenAspect) / screenAspect > 0.02f) {
                                    Text(
                                        text = "⚠️ La relación de aspecto de la portada ($portadaAspectFormatted) no coincide con la de la pantalla ($screenAspectFormatted).",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }

                            Text(
                                "Ruta: ${formatPathForDisplay(context, path)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        } ?: Text("Ninguna imagen seleccionada")

                        Button(onClick = {
                            focusManager.clearFocus()
                            imagePicker.launch(arrayOf("image/*"))
                        }) {
                            Text("Seleccionar Imagen")
                        }
                    }

                    // Cuadro de captura
                    Column {
                        Text("Cuadro de captura", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        captureBoxPath?.let { path ->
                            SubcomposeAsyncImage(
                                model = path,
                                contentDescription = "Vista previa de cuadro de captura",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(Color.Gray.copy(alpha = 0.2f)),
                                contentScale = ContentScale.Fit
                            ) {
                                val state = painter.state
                                if (state is AsyncImagePainter.State.Error || state is AsyncImagePainter.State.Empty) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Error al cargar imagen", color = Color.Red)
                                    }
                                } else {
                                    SubcomposeAsyncImageContent()
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            captureBoxDimensions?.let { (w, h) ->
                                val captureBoxAspect = w.toFloat() / h.toFloat()
                                val captureBoxAspectFormatted = ImageGeometryHelper.formatAspectRatio(w, h)
                                if (abs(captureBoxAspect - screenAspect) / screenAspect > 0.02f) {
                                    Text(
                                        text = "⚠️ La relación de aspecto del cuadro ($captureBoxAspectFormatted) no coincide con la de la pantalla ($screenAspectFormatted).",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }

                            Text(
                                "Ruta: ${formatPathForDisplay(context, path)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        } ?: Text("Ninguna imagen seleccionada")

                        Button(onClick = {
                            focusManager.clearFocus()
                            captureBoxPicker.launch(arrayOf("image/*"))
                        }) {
                            Text("Seleccionar Imagen")
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Ajuste del cuadro:", style = MaterialTheme.typography.titleMedium)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = decorationFit == DecorationFit.STRETCH,
                                    onClick = {
                                        UserActionTracker.trackAction("Ajuste del cuadro: STRETCH")
                                        decorationFit = DecorationFit.STRETCH
                                    }
                                )
                                Text("STRETCH", Modifier.clickable { decorationFit = DecorationFit.STRETCH })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = decorationFit == DecorationFit.COVER,
                                    onClick = {
                                        UserActionTracker.trackAction("Ajuste del cuadro: COVER")
                                        decorationFit = DecorationFit.COVER
                                    }
                                )
                                Text("COVER", Modifier.clickable { decorationFit = DecorationFit.COVER })
                            }
                        }
                        Text(
                            text = "STRETCH deforma si la proporción no coincide; COVER recorta los bordes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Cuenta regresiva
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Cuenta regresiva",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = countdownSeconds,
                            onValueChange = {
                                if (it.all { char -> char.isDigit() }) countdownSeconds =
                                    it
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                            modifier = Modifier
                                .width(80.dp)
                                .bringIntoViewRequester(countdownFocusRequester)
                                .onFocusChanged { focusState ->
                                    countdownFocused = focusState.isFocused
                                    if (focusState.isFocused) {
                                        coroutineScope.launch {
                                            countdownFocusRequester.bringIntoView()
                                        }
                                        if (countdownSeconds == "0") {
                                            countdownSeconds = ""
                                        }
                                    }
                                }
                        )
                    }

                    // Destino
                    Column {
                        Text("Destino", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ruta: ${formatPathForDisplay(context, destinationPath)}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(onClick = {
                                focusManager.clearFocus()
                                directoryPicker.launch(null)
                            }) {
                                Text("Seleccionar Carpeta")
                            }
                            Button(onClick = {
                                focusManager.clearFocus()
                                openFolder(context, destinationPath)
                            }) {
                                Text("Abrir")
                            }
                        }
                    }

                    // Tamaño de la imagen
                    Column {
                        Text("Tamaño de la imagen", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))

                        var dropdownExpanded by remember { mutableStateOf(false) }
                        val menuScrollState = rememberScrollState()

                        val currentResolutionObj = remember(pictureResolution, nativeSizes) {
                            nativeSizes.sizes.firstOrNull { it.key == pictureResolution }
                                ?: CameraResolutionHelper.parseResolution(pictureResolution)
                                ?: nativeSizes.sizes.firstOrNull()
                        }

                        fun getResolutionDisplayText(res: CameraResolution): String {
                            val isMax = nativeSizes.sizes.firstOrNull()?.key == res.key
                            return if (isMax) {
                                "Máxima calidad (${res.width} x ${res.height})"
                            } else {
                                "${res.width} x ${res.height}"
                            }
                        }

                        val displayText = currentResolutionObj?.let { getResolutionDisplayText(it) }
                            ?: (pictureResolution ?: "Máxima calidad")

                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = displayText,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            )

                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                scrollState = menuScrollState,
                                modifier = Modifier.heightIn(max = 280.dp)
                            ) {
                                if (nativeSizes.sizes.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Sin resoluciones compatibles encontradas") },
                                        onClick = { dropdownExpanded = false }
                                    )
                                } else {
                                    nativeSizes.sizes.forEach { res ->
                                        val isSelected = res.key == currentResolutionObj?.key
                                        val itemText = getResolutionDisplayText(res)
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = itemText,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            trailingIcon = {
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Seleccionado",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            },
                                            modifier = if (isSelected) {
                                                Modifier.background(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                )
                                            } else Modifier,
                                            onClick = {
                                                UserActionTracker.trackAction("Seleccionar resolución ${res.displayText}")
                                                pictureResolution = res.key
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Vista previa
                    Column {
                        Text("Vista previa", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = previewFit == PreviewFit.FIT,
                                onClick = {
                                    UserActionTracker.trackAction("Vista previa: Ajustar")
                                    previewFit = PreviewFit.FIT
                                }
                            )
                            Text("Ajustar (completa sin recortar)", Modifier.clickable { previewFit = PreviewFit.FIT })
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = previewFit == PreviewFit.FILL,
                                onClick = {
                                    UserActionTracker.trackAction("Vista previa: Rellenar")
                                    previewFit = PreviewFit.FILL
                                }
                            )
                            Text("Rellenar (pantalla completa)", Modifier.clickable { previewFit = PreviewFit.FILL })
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = previewFit == PreviewFit.FIXED_ASPECT,
                                onClick = {
                                    UserActionTracker.trackAction("Vista previa: Relación fija")
                                    previewFit = PreviewFit.FIXED_ASPECT
                                }
                            )
                            Text("Relación fija", Modifier.clickable { previewFit = PreviewFit.FIXED_ASPECT })
                        }

                        if (previewFit == PreviewFit.FIXED_ASPECT) {
                            var aspectDropdownExpanded by remember { mutableStateOf(false) }
                            val aspectMenuScrollState = rememberScrollState()

                            fun getAspectDisplayText(aspect: FixedAspect): String = when (aspect) {
                                FixedAspect.R3_4 -> "3:4"
                                FixedAspect.R9_16 -> "9:16"
                                FixedAspect.R4_3 -> "4:3"
                                FixedAspect.R16_9 -> "16:9"
                            }

                            ExposedDropdownMenuBox(
                                expanded = aspectDropdownExpanded,
                                onExpandedChange = { aspectDropdownExpanded = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 32.dp, top = 8.dp)
                            ) {
                                OutlinedTextField(
                                    value = getAspectDisplayText(fixedAspect),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Proporción") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aspectDropdownExpanded) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                )

                                ExposedDropdownMenu(
                                    expanded = aspectDropdownExpanded,
                                    onDismissRequest = { aspectDropdownExpanded = false },
                                    scrollState = aspectMenuScrollState,
                                    modifier = Modifier.heightIn(max = 280.dp)
                                ) {
                                    FixedAspect.entries.forEach { aspect ->
                                        val isSelected = aspect == fixedAspect
                                        val itemText = getAspectDisplayText(aspect)
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = itemText,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                            },
                                            trailingIcon = {
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Seleccionado",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            },
                                            modifier = if (isSelected) {
                                                Modifier.background(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                )
                                            } else Modifier,
                                            onClick = {
                                                UserActionTracker.trackAction("Relación fija: $itemText")
                                                fixedAspect = aspect
                                                aspectDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Orientación de la aplicación
                    Column {
                        Text("Orientación de la aplicación", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = appOrientation == AppOrientation.PORTRAIT,
                                onClick = {
                                    UserActionTracker.trackAction("Orientación: Vertical")
                                    appOrientation = AppOrientation.PORTRAIT
                                }
                            )
                            Text("Vertical (Portrait)", Modifier.clickable {
                                UserActionTracker.trackAction("Orientación: Vertical")
                                appOrientation = AppOrientation.PORTRAIT
                            })
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = appOrientation == AppOrientation.LANDSCAPE,
                                onClick = {
                                    UserActionTracker.trackAction("Orientación: Horizontal")
                                    appOrientation = AppOrientation.LANDSCAPE
                                }
                            )
                            Text("Horizontal (Landscape)", Modifier.clickable {
                                UserActionTracker.trackAction("Orientación: Horizontal")
                                appOrientation = AppOrientation.LANDSCAPE
                            })
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = appOrientation == AppOrientation.AUTO,
                                onClick = {
                                    UserActionTracker.trackAction("Orientación: Automática")
                                    appOrientation = AppOrientation.AUTO
                                }
                            )
                            Text("Automática (Auto)", Modifier.clickable {
                                UserActionTracker.trackAction("Orientación: Automática")
                                appOrientation = AppOrientation.AUTO
                            })
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Fuerza la orientación de la app e ignora el bloqueo de rotación del sistema.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Diagnóstico de imagen
                    Column {
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Diagnóstico de imagen", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))

                        val displayMetrics = context.resources.displayMetrics
                        val screenW = displayMetrics.widthPixels
                        val screenH = displayMetrics.heightPixels
                        val screenAspect = if (screenH > 0) screenW.toFloat() / screenH.toFloat() else 1.0f
                        val screenAspectFormatted = ImageGeometryHelper.formatAspectRatio(screenW, screenH)

                        val currentRes = nativeSizes.sizes.firstOrNull { it.key == pictureResolution }
                            ?: nativeSizes.sizes.firstOrNull()

                        val inputW = currentRes?.width ?: 1920
                        val inputH = currentRes?.height ?: 1080
                        val inputAspectFormatted = ImageGeometryHelper.formatAspectRatio(inputW, inputH)

                        val frameW = if (screenW >= screenH) maxOf(inputW, inputH) else minOf(inputW, inputH)
                        val frameH = if (screenW >= screenH) minOf(inputW, inputH) else maxOf(inputW, inputH)

                        val outRegion = ImageGeometryHelper.calculateOutputRegion(
                            frameW = frameW,
                            frameH = frameH,
                            previewFit = previewFit,
                            fixedAspect = fixedAspect,
                            screenAspect = screenAspect
                        )

                        val outputW = outRegion.width
                        val outputH = outRegion.height
                        val outputAspectFormatted = ImageGeometryHelper.formatAspectRatio(outputW, outputH)

                        val estimatedMb = CameraResolutionHelper.estimateFileSize(outRegion.totalPixels, initialConfig.lastSavedBytesPerPixel)
                        val estimatedMbFormatted = String.format(Locale.US, "%.1f", estimatedMb)

                        val pantallaText = "Pantalla: ${screenW}x${screenH} ($screenAspectFormatted)"
                        val entradaText = "Entrada: ${inputW}x${inputH} ($inputAspectFormatted)"
                        val salidaText = "Salida: ${outputW}x${outputH} ($outputAspectFormatted)"

                        Text(
                            pantallaText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    copyToClipboard(context, pantallaText)
                                }
                                .padding(vertical = 4.dp)
                        )
                        Text(
                            entradaText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    copyToClipboard(context, entradaText)
                                }
                                .padding(vertical = 4.dp)
                        )
                        Text(
                            salidaText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    copyToClipboard(context, salidaText)
                                }
                                .padding(vertical = 4.dp)
                        )
                    }

                    // Mantenimiento
                    Column {
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Mantenimiento", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                UserActionTracker.trackAction("Ver historial de errores")
                                onNavigateToErrorHistory()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Ver Historial de Errores")
                        }
                    }

                    // Acerca de
                    Column {
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Acerca de", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Column {
                    val versionText = if (BuildConfig.GIT_TAG.isNotEmpty()) {
                        "${BuildConfig.GIT_TAG} (${BuildConfig.GIT_COMMIT})"
                    } else {
                        BuildConfig.GIT_COMMIT
                    }
                    val fullVersionStr = "Versión: $versionText"
                    val fullLicenseStr = "Licencia: MIT"
                    val fullAuthorStr = "Autor: github.com/malklera"

                    Text(
                        fullVersionStr,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                copyToClipboard(context, fullVersionStr)
                            }
                            .padding(vertical = 4.dp)
                    )
                    Text(
                        fullLicenseStr,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                copyToClipboard(context, fullLicenseStr)
                            }
                            .padding(vertical = 4.dp)
                    )
                    Text(
                        fullAuthorStr,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                copyToClipboard(context, fullAuthorStr)
                            }
                            .padding(vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Cambios sin guardar") },
            text = { Text("Tiene cambios sin guardar. ¿Qué desea hacer?") },
            confirmButton = {
                TextButton(onClick = {
                    focusManager.clearFocus()
                    Log.d("ConfigScreen", "Dialog Save clicked")
                    val finalSeconds = countdownSeconds.toIntOrNull() ?: 0
                    onSave(
                        AppConfig(
                            portadaPath = portadaPath,
                            captureBoxPath = captureBoxPath,
                            countdownSeconds = finalSeconds,
                            destinationPath = destinationPath,
                            inputResolution = pictureResolution,
                            previewFit = previewFit,
                            fixedAspect = fixedAspect,
                            decorationFit = decorationFit,
                            appOrientation = appOrientation,
                            lastSavedBytesPerPixel = initialConfig.lastSavedBytesPerPixel,
                            savedSampleCount = initialConfig.savedSampleCount
                        )
                    )
                    Toast.makeText(context, "Configuración guardada", Toast.LENGTH_SHORT).show()
                    showUnsavedDialog = false
                    onClose()
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        focusManager.clearFocus()
                        Log.d("ConfigScreen", "Dialog Discard clicked")
                        showUnsavedDialog = false
                        onClose()
                    }) {
                        Text("Descartar")
                    }
                    TextButton(onClick = {
                        focusManager.clearFocus()
                        showUnsavedDialog = false
                    }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
}

private fun formatPathForDisplay(context: Context, uriString: String?): String {
    if (uriString.isNullOrEmpty()) return "Ninguna"

    val internalStorageLabels = listOf(
        "/storage/emulated/0" to "Almacenamiento interno",
        "/sdcard" to "Almacenamiento interno",
        "/mnt/sdcard" to "Almacenamiento interno"
    )

    for ((prefix, label) in internalStorageLabels) {
        if (uriString.startsWith(prefix)) {
            return uriString.replaceFirst(prefix, label)
        }
    }

    try {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "content") {
            var resolvedUri = uri

            // 1. Resolve SAF Media Documents to real MediaStore URIs to get path info
            if (DocumentsContract.isDocumentUri(context, uri)) {
                if ("com.android.providers.media.documents" == uri.authority) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":")
                    if (split.size >= 2) {
                        val type = split[0]
                        val id = split[1]
                        val baseUri = when (type) {
                            "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            else -> null
                        }
                        if (baseUri != null) resolvedUri = Uri.withAppendedPath(baseUri, id)
                    }
                }
            }

            // 2. Try Database Resolution (MediaStore/Resolved SAF)
            try {
                val projection = mutableListOf(OpenableColumns.DISPLAY_NAME)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
                }
                projection.add(MediaStore.MediaColumns.DATA)

                context.contentResolver.query(resolvedUri, projection.toTypedArray(), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                        var path: String? = null

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val relPathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                            if (relPathIndex != -1) path = cursor.getString(relPathIndex)
                        }

                        if (path.isNullOrEmpty()) {
                            val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (dataIndex != -1) {
                                val fullPath = cursor.getString(dataIndex)
                                if (!fullPath.isNullOrEmpty()) path = fullPath.substringBeforeLast('/', "")
                            }
                        }

                        if (!path.isNullOrEmpty()) {
                            val cleanPath = path.trim('/')
                            val friendlyPath = if (cleanPath.startsWith("0/")) {
                                "Almacenamiento interno/" + cleanPath.substringAfter("0/")
                            } else {
                                cleanPath.replace("emulated/0", "Almacenamiento interno")
                            }
                            return "$friendlyPath/$name".replace("//", "/")
                        }
                    }
                }
            } catch (e: Exception) { /* Ignore */
            }

            // 3. SAF Tree/Document Manual Parsing (for File Manager picks)
            var docId: String? = null
            try {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    docId = DocumentsContract.getDocumentId(uri)
                } else {
                    docId = try {
                        DocumentsContract.getTreeDocumentId(uri)
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) { /* Ignore */
            }

            if (docId == null) {
                val decodedUri = Uri.decode(uriString)
                if (decodedUri.contains("primary:")) {
                    docId = "primary:" + decodedUri.substringAfter("primary:")
                }
            }

            if (docId != null) {
                if (docId.startsWith("primary:")) {
                    val relativePath = docId.substringAfter("primary:").trim('/')
                    return if (relativePath.isEmpty()) "Almacenamiento interno" else "Almacenamiento interno/$relativePath"
                } else if (docId.contains(":")) {
                    val volume = docId.substringBefore(":")
                    val path = docId.substringAfter(":").trim('/')
                    return if (volume == "primary") {
                        if (path.isEmpty()) "Almacenamiento interno" else "Almacenamiento interno/$path"
                    } else {
                        "$volume/$path"
                    }
                }
            }

            // 4. Last resort: Filename from OpenableColumns or URI segments
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val name = cursor.getString(0)
                            if (!name.isNullOrEmpty()) return name
                        }
                    }
            } catch (e: Exception) { /* Ignore */
            }

            val decoded = Uri.decode(uriString)
            val segments = decoded.split("/").filter { it.isNotEmpty() }
            val lastSegment = segments.lastOrNull()
            if (lastSegment != null) {
                if (lastSegment.contains("primary:")) {
                    return "Almacenamiento interno/" + lastSegment.substringAfter("primary:")
                }
                return lastSegment
            }
            return decoded
        } else if (uri.scheme == "file") {
            val path = uri.path
            if (path != null) {
                for ((prefix, label) in internalStorageLabels) {
                    if (path.startsWith(prefix)) return path.replaceFirst(prefix, label)
                }
                return path
            }
        }
    } catch (e: Exception) { /* Ignore */
    }

    return Uri.decode(uriString)
}

private fun copyToClipboard(context: Context, text: String, label: String = "Selfie") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
    }
}

private fun getImageDimensions(context: Context, pathOrUri: String?): Pair<Int, Int>? {
    if (pathOrUri.isNullOrBlank()) return null
    return try {
        val uri = Uri.parse(pathOrUri)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        var decoded = false
        if (uri.scheme == "content" || uri.scheme == "file") {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                    decoded = options.outWidth > 0 && options.outHeight > 0
                }
            } catch (_: Exception) {}
        }
        if (!decoded) {
            val filePath = if (uri.scheme == "file") uri.path else pathOrUri
            if (!filePath.isNullOrBlank()) {
                BitmapFactory.decodeFile(filePath, options)
            }
        }
        if (options.outWidth > 0 && options.outHeight > 0) {
            Pair(options.outWidth, options.outHeight)
        } else null
    } catch (e: Exception) {
        null
    }
}

private fun openFolder(context: Context, path: String?) {
    if (path.isNullOrEmpty()) {
        Toast.makeText(context, "No hay carpeta seleccionada", Toast.LENGTH_SHORT).show()
        return
    }

    UserActionTracker.trackAction("Abrir carpeta de destino")

    val intentsToTry = mutableListOf<Intent>()

    if (path.startsWith("content://")) {
        val uri = Uri.parse(path)
        val isTree = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            DocumentsContract.isTreeUri(uri)
        } else {
            uri.path?.contains("/tree/") == true
        }
        if (isTree) {
            try {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                intentsToTry.add(Intent("android.provider.action.BROWSE").apply {
                    setData(docUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(docUri, "resource/folder")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(docUri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                ErrorLogger.log("Error al construir URI de árbol: ${e.message}")
            }
        }
        intentsToTry.add(Intent("android.provider.action.BROWSE").apply {
            setData(uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "resource/folder")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        intentsToTry.add(Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } else {
        val file = File(path)
        if (!file.exists()) {
            try {
                file.mkdirs()
            } catch (e: Exception) {
                ErrorLogger.log("Error al crear carpeta antes de abrir: ${e.message}")
            }
        }

        if (path.startsWith("/storage/emulated/0") || path.startsWith("/sdcard")) {
            val relativePath = path.removePrefix("/storage/emulated/0").removePrefix("/sdcard").removePrefix("/")
            val docId = if (relativePath.isEmpty()) "primary:" else "primary:$relativePath"
            val docUri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
            val treeUri = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", docId)

            intentsToTry.add(Intent("android.provider.action.BROWSE").apply {
                setData(docUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            intentsToTry.add(Intent("android.provider.action.BROWSE").apply {
                setData(treeUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(treeUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }

        val fileUri = Uri.parse("file://$path")
        intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "resource/folder")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "vnd.android.document/directory")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        intentsToTry.add(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "*/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    val knownFileManagerPackages = listOf(
        "com.sec.android.app.myfiles",
        "com.google.android.apps.nfiles",
        "com.google.android.documentsui",
        "com.android.documentsui",
        "com.mi.android.globalFileexplorer",
        "com.coloros.filemanager",
        "com.motorola.filemanager"
    )

    for (pkg in knownFileManagerPackages) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intentsToTry.add(launchIntent)
        }
    }

    var success = false
    for (intent in intentsToTry) {
        try {
            context.startActivity(intent)
            success = true
            break
        } catch (e: Exception) {
            // Try next intent
        }
    }

    if (!success) {
        ErrorLogger.log("No se pudo abrir la carpeta $path")
        Toast.makeText(context, "No se encontró una app para abrir la carpeta", Toast.LENGTH_SHORT).show()
    }
}

