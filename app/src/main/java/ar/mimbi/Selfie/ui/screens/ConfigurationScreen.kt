package ar.mimbi.Selfie.ui.screens

import android.content.Context
import android.content.Intent
import android.util.Log
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.mimbi.Selfie.data.AppConfig
import ar.mimbi.Selfie.data.CameraResolution
import ar.mimbi.Selfie.data.CameraResolutionHelper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.launch
import ar.mimbi.Selfie.data.ErrorLogger
import ar.mimbi.Selfie.data.UserActionTracker
import ar.mimbi.Selfie.BuildConfig
import coil.compose.SubcomposeAsyncImage
import ar.mimbi.Selfie.printing.queue.PrintQueueManager
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImageContent
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigurationScreen(
    initialConfig: AppConfig,
    onSave: (AppConfig) -> Unit,
    onClose: () -> Unit,
    onNavigateToErrorHistory: () -> Unit,
    onNavigateToPrinterConfig: () -> Unit = {},
    onNavigateToTemplateConfig: () -> Unit = {},
    onNavigateToQueueManagement: () -> Unit = {},
    queueManager: PrintQueueManager? = null
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

    val maxPrintFocusRequester = remember { BringIntoViewRequester() }
    var maxPrintFocused by remember { mutableStateOf(false) }
    LaunchedEffect(isKeyboardVisible, maxPrintFocused) {
        if (isKeyboardVisible && maxPrintFocused) {
            maxPrintFocusRequester.bringIntoView()
        }
    }
    var portadaPath by remember { mutableStateOf(initialConfig.portadaPath) }
    var captureBoxPath by remember { mutableStateOf(initialConfig.captureBoxPath) }
    var countdownSeconds by remember { mutableStateOf(initialConfig.countdownSeconds.toString()) }
    var destinationPath by remember { mutableStateOf(initialConfig.destinationPath) }

    val resolutionsPair = remember { CameraResolutionHelper.getSupportedResolutions(context) }
    val supported9_16 = resolutionsPair.first
    val supported3_4 = resolutionsPair.second
    val defaultResKey = remember {
        CameraResolutionHelper.getDefaultResolution(context)?.key
    }
    var pictureResolution by remember {
        mutableStateOf(initialConfig.pictureResolution ?: defaultResKey)
    }

    var showPrintButton by remember { mutableStateOf(initialConfig.showPrintButton) }
    var maxPrintCount by remember { mutableStateOf(initialConfig.maxPrintCount.toString()) }
    var printCount by remember { mutableStateOf(initialConfig.printCount) }
    var wasResetToZero by remember { mutableStateOf(false) }
    
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val hasChanges = portadaPath != initialConfig.portadaPath ||
            captureBoxPath != initialConfig.captureBoxPath ||
            countdownSeconds != initialConfig.countdownSeconds.toString() ||
            destinationPath != initialConfig.destinationPath ||
            pictureResolution != (initialConfig.pictureResolution ?: defaultResKey) ||
            showPrintButton != initialConfig.showPrintButton ||
            maxPrintCount != initialConfig.maxPrintCount.toString() ||
            printCount != initialConfig.printCount

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
                                val finalMaxPrintCount = maxPrintCount.toIntOrNull() ?: 0
                                if (wasResetToZero) {
                                    coroutineScope.launch {
                                        queueManager?.clearPrintedItems()
                                    }
                                    wasResetToZero = false
                                }
                                onSave(
                                    AppConfig(
                                        portadaPath = portadaPath,
                                        captureBoxPath = captureBoxPath,
                                        countdownSeconds = finalSeconds,
                                        destinationPath = destinationPath,
                                        pictureResolution = pictureResolution,
                                        showPrintButton = showPrintButton,
                                        maxPrintCount = finalMaxPrintCount,
                                        printCount = printCount
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
                            Text("Ruta: ${formatPathForDisplay(context, path)}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val displayMetrics = context.resources.displayMetrics
                            val screenAspect = displayMetrics.widthPixels.toFloat() / displayMetrics.heightPixels.toFloat()
                            var imageAspect by remember { mutableStateOf<Float?>(null) }

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
                                if (state is AsyncImagePainter.State.Success) {
                                    val size = state.painter.intrinsicSize
                                    if (size.width > 0 && size.height > 0) {
                                        imageAspect = size.width / size.height
                                    }
                                }
                                
                                if (state is AsyncImagePainter.State.Error || state is AsyncImagePainter.State.Empty) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Error al cargar imagen", color = Color.Red)
                                    }
                                } else {
                                    SubcomposeAsyncImageContent()
                                }
                            }
                            
                            imageAspect?.let { aspect ->
                                val diff = abs(aspect - screenAspect)
                                if (diff > 0.01f) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "⚠️ La relación de aspecto de la imagen no coincide con la de la pantalla. Se verá deformada.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
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
                            Text("Ruta: ${formatPathForDisplay(context, path)}", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))

                            val displayMetrics = context.resources.displayMetrics
                            val screenAspect = displayMetrics.widthPixels.toFloat() / displayMetrics.heightPixels.toFloat()
                            var imageAspect by remember { mutableStateOf<Float?>(null) }

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
                                if (state is AsyncImagePainter.State.Success) {
                                    val size = state.painter.intrinsicSize
                                    if (size.width > 0 && size.height > 0) {
                                        imageAspect = size.width / size.height
                                    }
                                }

                                if (state is AsyncImagePainter.State.Error || state is AsyncImagePainter.State.Empty) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Error al cargar imagen", color = Color.Red)
                                    }
                                } else {
                                    SubcomposeAsyncImageContent()
                                }
                            }

                            imageAspect?.let { aspect ->
                                val diff = abs(aspect - screenAspect)
                                if (diff > 0.01f) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "⚠️ La relación de aspecto de la imagen no coincide con la de la pantalla. Se verá deformada.",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        } ?: Text("Ninguna imagen seleccionada")

                        Button(onClick = {
                            focusManager.clearFocus()
                            captureBoxPicker.launch(arrayOf("image/*"))
                        }) {
                            Text("Seleccionar Imagen")
                        }
                    }

                    // Cuenta regresiva
                    Column {
                        Text("Cuenta regresiva", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = countdownSeconds,
                            onValueChange = {
                                if (it.all { char -> char.isDigit() }) countdownSeconds =
                                    it
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
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
                        Button(onClick = {
                            focusManager.clearFocus()
                            directoryPicker.launch(null)
                        }) {
                            Text("Seleccionar Carpeta")
                        }
                    }

                    // Calidad de las fotos
                    Column {
                        Text("Calidad de las fotos", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))

                        var dropdownExpanded by remember { mutableStateOf(false) }
                        val menuScrollState = rememberScrollState()
                        val density = LocalDensity.current

                        val currentResolutionObj = remember(pictureResolution, supported9_16, supported3_4) {
                            (supported9_16 + supported3_4).firstOrNull { it.key == pictureResolution }
                                ?: CameraResolutionHelper.parseResolution(pictureResolution)
                        }

                        val displayText = currentResolutionObj?.let { res ->
                            val ratioLabel = if (res.is9_16) " (9:16)" else if (res.is3_4) " (3:4)" else ""
                            "${res.displayText}$ratioLabel"
                        } ?: (pictureResolution ?: "Predeterminada (9:16)")

                        // Auto-scroll to selected resolution when opened
                        LaunchedEffect(dropdownExpanded) {
                            if (dropdownExpanded) {
                                val targetY = with(density) {
                                    val itemHeight = 48.dp.toPx()
                                    val headerHeight = 36.dp.toPx()
                                    val dividerHeight = 8.dp.toPx()
                                    val paddingOffset = 40.dp.toPx()

                                    val index9_16 = supported9_16.indexOfFirst { it.key == pictureResolution }
                                    val index3_4 = supported3_4.indexOfFirst { it.key == pictureResolution }

                                    when {
                                        index9_16 >= 0 -> {
                                            (headerHeight + index9_16 * itemHeight - paddingOffset).coerceAtLeast(0f)
                                        }
                                        index3_4 >= 0 -> {
                                            val prevHeight = headerHeight + supported9_16.size * itemHeight + dividerHeight
                                            (prevHeight + headerHeight + index3_4 * itemHeight - paddingOffset).coerceAtLeast(0f)
                                        }
                                        else -> 0f
                                    }
                                }
                                if (targetY > 0) {
                                    menuScrollState.scrollTo(targetY.toInt())
                                }
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = displayText,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Resolución") },
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
                                if (supported9_16.isEmpty() && supported3_4.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Sin resoluciones compatibles encontradas") },
                                        onClick = { dropdownExpanded = false }
                                    )
                                } else {
                                    if (supported9_16.isNotEmpty()) {
                                        Text(
                                            text = "Relación 9:16",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                        supported9_16.forEach { res ->
                                            val isSelected = res.key == pictureResolution
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = res.displayText,
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
                                                    Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                                } else Modifier,
                                                onClick = {
                                                    UserActionTracker.trackAction("Seleccionar resolución ${res.displayText} (9:16)")
                                                    pictureResolution = res.key
                                                    dropdownExpanded = false
                                                }
                                            )
                                        }
                                    }

                                    if (supported3_4.isNotEmpty()) {
                                        if (supported9_16.isNotEmpty()) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                        Text(
                                            text = "Relación 3:4",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                        supported3_4.forEach { res ->
                                            val isSelected = res.key == pictureResolution
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = res.displayText,
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
                                                    Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                                } else Modifier,
                                                onClick = {
                                                    UserActionTracker.trackAction("Seleccionar resolución ${res.displayText} (3:4)")
                                                    pictureResolution = res.key
                                                    dropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pantalla
                    Column {
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Pantalla", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        val displayMetrics = context.resources.displayMetrics
                        val w = displayMetrics.widthPixels
                        val h = displayMetrics.heightPixels
                        
                        fun calculateGcd(a: Int, b: Int): Int = if (b == 0) a else calculateGcd(b, a % b)
                        val common = calculateGcd(w, h)
                        val aspectW = w / common
                        val aspectH = h / common

                        Text("Resolución: $w x $h", style = MaterialTheme.typography.bodyMedium)
                        Text("Relación de aspecto: $aspectW:$aspectH", style = MaterialTheme.typography.bodyMedium)
                    }

                    // Impresión
                    Column {
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Impresión", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Switch: Mostrar botón
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mostrar botón", style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = showPrintButton,
                                onCheckedChange = {
                                    UserActionTracker.trackAction("Cambiar mostrar botón de impresión: $it")
                                    showPrintButton = it
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Máximo número de impresiones
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Máximo número de impresiones",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = maxPrintCount,
                                onValueChange = { text ->
                                    if (text.all { char -> char.isDigit() }) {
                                        maxPrintCount = text
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .width(100.dp)
                                    .bringIntoViewRequester(maxPrintFocusRequester)
                                    .onFocusChanged { focusState ->
                                        maxPrintFocused = focusState.isFocused
                                        if (focusState.isFocused) {
                                            coroutineScope.launch {
                                                maxPrintFocusRequester.bringIntoView()
                                            }
                                            if (maxPrintCount == "0") {
                                                maxPrintCount = ""
                                            }
                                        }
                                    }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Impresos + Botón para resetear a 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Impresos", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Cantidad realizada: $printCount",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    UserActionTracker.trackAction("Reiniciar contador de impresiones a 0")
                                    printCount = 0
                                    wasResetToZero = true
                                }
                            ) {
                                Text("Resetear a 0")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Sistema de Impresión
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Configuración del Sistema de Impresión",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        focusManager.clearFocus()
                                        onNavigateToPrinterConfig()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Configuración de Impresora", fontWeight = FontWeight.Bold)
                                        Text("Estado, conexión y adaptador de impresora", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        focusManager.clearFocus()
                                        onNavigateToTemplateConfig()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Plantillas de Impresión", fontWeight = FontWeight.Bold)
                                        Text("Configurar distribución de fotos por página (1, 2, 4 fotos)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        focusManager.clearFocus()
                                        onNavigateToQueueManagement()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Gestión de Cola de Impresión", fontWeight = FontWeight.Bold)
                                        Text("Ver lotes, imprimir pendientes/restantes y reintentar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
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
                    Text(
                        "Versión: $versionText",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("Licencia: MIT", style = MaterialTheme.typography.bodyMedium)
                    Text("Autor: github.com/malklera", style = MaterialTheme.typography.bodyMedium)
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
                    val finalMaxPrintCount = maxPrintCount.toIntOrNull() ?: 0
                    if (wasResetToZero) {
                        coroutineScope.launch {
                            queueManager?.clearPrintedItems()
                        }
                        wasResetToZero = false
                    }
                    onSave(
                        AppConfig(
                            portadaPath = portadaPath,
                            captureBoxPath = captureBoxPath,
                            countdownSeconds = finalSeconds,
                            destinationPath = destinationPath,
                            pictureResolution = pictureResolution,
                            showPrintButton = showPrintButton,
                            maxPrintCount = finalMaxPrintCount,
                            printCount = printCount
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
            } catch (e: Exception) { /* Ignore */ }

            // 3. SAF Tree/Document Manual Parsing (for File Manager picks)
            var docId: String? = null
            try {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    docId = DocumentsContract.getDocumentId(uri)
                } else {
                    docId = try { DocumentsContract.getTreeDocumentId(uri) } catch (e: Exception) { null }
                }
            } catch (e: Exception) { /* Ignore */ }

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
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrEmpty()) return name
                    }
                }
            } catch (e: Exception) { /* Ignore */ }

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
    } catch (e: Exception) { /* Ignore */ }

    return Uri.decode(uriString)
}
