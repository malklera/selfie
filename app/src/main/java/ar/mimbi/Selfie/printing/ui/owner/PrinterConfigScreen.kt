package ar.mimbi.Selfie.printing.ui.owner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val status by printerManager.statusFlow.collectAsState()
    val printerName by printerManager.currentPrinterName.collectAsState()
    val scope = rememberCoroutineScope()

    val wifiDiscovery = remember { WifiPrinterDiscovery(context) }
    val discoveredPrinters by wifiDiscovery.discoveredPrinters.collectAsState()
    val isSearching by wifiDiscovery.isSearching.collectAsState()

    var showSearchDialog by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("9100") }
    var manualName by remember { mutableStateOf("Impresora Wi-Fi Manual") }

    DisposableEffect(Unit) {
        onDispose {
            wifiDiscovery.stopDiscovery()
        }
    }

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
                            onClick = {
                                wifiDiscovery.startDiscovery()
                                showSearchDialog = true
                            },
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
                        is PrinterStatus.Disconnected -> "Desconectada"
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
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Conectar / Verificar")
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            printerManager.disconnect()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Desconectar")
                }
            }
        }
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = {
                wifiDiscovery.stopDiscovery()
                showSearchDialog = false
            },
            title = { Text("Buscar Impresoras Wi-Fi") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isSearching) "Buscando en la red..." else "Búsqueda finalizada")
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            IconButton(onClick = { wifiDiscovery.startDiscovery() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reintentar")
                            }
                        }
                    }

                    if (discoveredPrinters.isNotEmpty()) {
                        Text("Impresoras encontradas:", fontWeight = FontWeight.Bold)
                        LazyColumn(
                            modifier = Modifier.height(150.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(discoveredPrinters) { printer ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                val wifiPrinter = WifiPrinter(printer.ipAddress, printer.port)
                                                printerManager.setPrinter(wifiPrinter, "${printer.name} (${printer.ipAddress})", printerType = "WIFI", ipAddress = printer.ipAddress, port = printer.port)
                                                printerManager.connect()
                                                wifiDiscovery.stopDiscovery()
                                                showSearchDialog = false
                                            }
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(printer.name, fontWeight = FontWeight.Bold)
                                        Text("IP: ${printer.ipAddress}:${printer.port}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    } else {
                        Text("No se encontraron impresoras automáticas. Ingrese IP manualmente:")
                    }

                    HorizontalDivider()

                    Text("Conexión Manual por IP:", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        label = { Text("Nombre") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { manualIp = it },
                            label = { Text("IP (ej. 192.168.1.50)") },
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = manualPort,
                            onValueChange = { manualPort = it },
                            label = { Text("Puerto") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            val portInt = manualPort.toIntOrNull() ?: 9100
                            if (manualIp.isNotBlank()) {
                                scope.launch {
                                    val wifiPrinter = WifiPrinter(manualIp.trim(), portInt)
                                    printerManager.setPrinter(wifiPrinter, "$manualName (${manualIp.trim()})", printerType = "WIFI", ipAddress = manualIp.trim(), port = portInt)
                                    printerManager.connect()
                                    wifiDiscovery.stopDiscovery()
                                    showSearchDialog = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = manualIp.isNotBlank()
                    ) {
                        Text("Conectar por IP Manual")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    wifiDiscovery.stopDiscovery()
                    showSearchDialog = false
                }) {
                    Text("Cerrar")
                }
            }
        )
    }
}
