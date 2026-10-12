package ar.mimbi.Selfie.printing.ui.owner

import android.widget.Toast
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
    val hasPrinterSelected = !printerName.isNullOrBlank()
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
                    Button(
                        onClick = onNavigateToWifiSearch,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Buscar Wi-Fi")
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
                        enabled = hasPrinterSelected,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Quitar Impresora")
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

                    val currentName = printerName
                    if (hasPrinterSelected && currentName != null) {
                        Text(text = currentName, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))

                        val statusText = when (val s = status) {
                            is PrinterStatus.Ready -> "Lista"
                            is PrinterStatus.Printing -> "Imprimiendo..."
                            is PrinterStatus.Disconnected -> "Desconectada"
                            is PrinterStatus.Error -> "Error: ${s.message}"
                        }

                        Text(text = statusText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text(text = "Sin impresora configurada", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val success = printerManager.connect()
                            if (success) {
                                Toast.makeText(context, "Impresora conectada y verificada exitosamente", Toast.LENGTH_SHORT).show()
                            } else {
                                val errorMsg = (printerManager.getStatus() as? PrinterStatus.Error)?.message ?: "Error de conexión"
                                Toast.makeText(context, "Error al conectar/verificar: $errorMsg", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = hasPrinterSelected
                ) {
                    Text("Conectar / Verificar")
                }

                Button(
                    onClick = {
                        scope.launch {
                            printerManager.disconnect()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = status is PrinterStatus.Ready || status is PrinterStatus.Printing
                ) {
                    Text("Desconectar")
                }
            }
        }
    }
}
