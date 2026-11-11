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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import ar.mimbi.Selfie.data.ConfigSessionState
import ar.mimbi.Selfie.data.UserActionTracker
import ar.mimbi.Selfie.printing.queue.PrintQueueManager
import ar.mimbi.Selfie.ui.components.EnforceAppOrientation
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
    queueManager: PrintQueueManager? = null,
    session: ConfigSessionState? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val activeSession = session ?: remember(initialConfig) {
        ConfigSessionState(initialConfig, 0, null)
    }

    EnforceAppOrientation(activeSession.appOrientation)

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

    BackHandler(enabled = true) {
        focusManager.clearFocus()
        UserActionTracker.trackAction("Volver desde pantalla de impresión (atrás físico/gesto)")
        onBack()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Impresión") },
                navigationIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        UserActionTracker.trackAction("Volver desde pantalla de impresión")
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
            // Switch: Mostrar botón
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Mostrar botón",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = activeSession.showPrintButton,
                    onCheckedChange = {
                        focusManager.clearFocus()
                        UserActionTracker.trackAction("Cambiar mostrar botón de impresión: $it")
                        activeSession.showPrintButton = it
                    }
                )
            }

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
                    value = activeSession.maxPrintCount,
                    onValueChange = { text ->
                        if (text.all { char -> char.isDigit() }) {
                            activeSession.maxPrintCount = text
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
                                if (activeSession.maxPrintCount == "0") {
                                    activeSession.maxPrintCount = ""
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
                        "Cantidad realizada: ${activeSession.printCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        UserActionTracker.trackAction("Reiniciar contador de impresiones a 0")
                        activeSession.printCount = 0
                        activeSession.wasResetToZero = true
                    },
                    enabled = activeSession.printCount > 0
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
}
