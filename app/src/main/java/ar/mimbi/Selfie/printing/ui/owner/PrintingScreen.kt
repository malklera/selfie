package ar.mimbi.Selfie.printing.ui.owner

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.mimbi.Selfie.data.AppConfig
import ar.mimbi.Selfie.data.UserActionTracker
import ar.mimbi.Selfie.printing.queue.PrintQueueManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PrintingScreen(
    initialConfig: AppConfig,
    onSave: (AppConfig) -> Unit,
    onBack: () -> Unit,
    onNavigateToPrinterConfig: () -> Unit,
    onNavigateToTemplateConfig: () -> Unit,
    onNavigateToQueueManagement: () -> Unit,
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

    val maxPrintFocusRequester = remember { BringIntoViewRequester() }
    var maxPrintFocused by remember { mutableStateOf(false) }
    LaunchedEffect(isKeyboardVisible, maxPrintFocused) {
        if (isKeyboardVisible && maxPrintFocused) {
            maxPrintFocusRequester.bringIntoView()
        }
    }

    var maxPrintCount by rememberSaveable { mutableStateOf(initialConfig.maxPrintCount.toString()) }
    var printCount by rememberSaveable { mutableIntStateOf(initialConfig.printCount) }
    var wasResetToZero by rememberSaveable { mutableStateOf(false) }
    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }

    val hasChanges = maxPrintCount != initialConfig.maxPrintCount.toString() ||
            printCount != initialConfig.printCount

    BackHandler(enabled = hasChanges) {
        focusManager.clearFocus()
        UserActionTracker.trackAction("Cerrar pantalla de impresión (atrás físico/gesto)")
        showUnsavedDialog = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Impresión") },
                navigationIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        UserActionTracker.trackAction("Volver desde pantalla de impresión")
                        if (hasChanges) {
                            showUnsavedDialog = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        UserActionTracker.trackAction("Guardar configuración de impresión")
                        val finalMaxPrintCount = maxPrintCount.toIntOrNull() ?: 0
                        if (wasResetToZero) {
                            coroutineScope.launch {
                                queueManager?.clearPrintedItems()
                            }
                            wasResetToZero = false
                        }
                        onSave(
                            initialConfig.copy(
                                maxPrintCount = finalMaxPrintCount,
                                printCount = printCount
                            )
                        )
                        Toast.makeText(context, "Configuración guardada", Toast.LENGTH_SHORT).show()
                        onBack()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Guardar")
                    }
                }
            )
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
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    modifier = Modifier
                        .width(80.dp)
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
                    },
                    enabled = printCount > 0
                ) {
                    Text("Resetear a 0")
                }
            }

            HorizontalDivider()

            // Sistema de Impresión
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    val finalMaxPrintCount = maxPrintCount.toIntOrNull() ?: 0
                    if (wasResetToZero) {
                        coroutineScope.launch {
                            queueManager?.clearPrintedItems()
                        }
                        wasResetToZero = false
                    }
                    onSave(
                        initialConfig.copy(
                            maxPrintCount = finalMaxPrintCount,
                            printCount = printCount
                        )
                    )
                    Toast.makeText(context, "Configuración guardada", Toast.LENGTH_SHORT).show()
                    showUnsavedDialog = false
                    onBack()
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        focusManager.clearFocus()
                        showUnsavedDialog = false
                        onBack()
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
