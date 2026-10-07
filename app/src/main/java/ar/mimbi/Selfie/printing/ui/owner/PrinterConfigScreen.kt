package ar.mimbi.Selfie.printing.ui.owner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.mimbi.Selfie.printing.printer.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterConfigScreen(
    printerManager: PrinterManager,
    onNavigateToWifiSearch: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val status by printerManager.statusFlow.collectAsState()
    val printerName by printerManager.currentPrinterName.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración de Impresora") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Impresora Seleccionada", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = printerName, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onNavigateToWifiSearch,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Buscar Wi-Fi")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    printerManager.setPrinter(FakePrinter(), "Impresora de Prueba (Fake Printer)", printerType = "FAKE")
                                    printerManager.connect()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Usar Fake")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                val androidPrinter = StandardAndroidPrinter(context)
                                printerManager.setPrinter(
                                    printer = androidPrinter,
                                    printerName = "Servicio de Impresión Android (Standard)",
                                    printerType = "SYSTEM_ANDROID"
                                )
                                printerManager.connect()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Usar Servicio de Impresión Android (Standard)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                printerManager.clearPrinter(clearFromDb = true)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Quitar Impresora (Sin configurar)")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Estado de la Impresora", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    val statusText = when (val s = status) {
                        is PrinterStatus.Ready -> "Lista / Conectada"
                        is PrinterStatus.Printing -> "Imprimiendo..."
                        is PrinterStatus.Disconnected -> if (!printerManager.hasConfiguredPrinter()) "Sin impresora configurada" else "Desconectada"
                        is PrinterStatus.Error -> "Error: ${s.message}"
                    }

                    Text(text = statusText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            printerManager.connect()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = printerManager.hasConfiguredPrinter()
                ) {
                    Text("Conectar / Verificar")
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            printerManager.disconnect()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = printerManager.hasConfiguredPrinter()
                ) {
                    Text("Desconectar")
                }
            }
        }
    }
}
