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
import ar.mimbi.Selfie.printing.printer.PrinterManager
import ar.mimbi.Selfie.printing.printer.WifiPrinter
import ar.mimbi.Selfie.printing.printer.WifiPrinterDiscovery
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiPrinterSearchScreen(
    printerManager: PrinterManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val wifiDiscovery = remember { WifiPrinterDiscovery(context) }
    val discoveredPrinters by wifiDiscovery.discoveredPrinters.collectAsState()
    val isSearching by wifiDiscovery.isSearching.collectAsState()

    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("9100") }
    var manualName by remember { mutableStateOf("Impresora Wi-Fi Manual") }

    DisposableEffect(Unit) {
        wifiDiscovery.startDiscovery()
        onDispose {
            wifiDiscovery.stopDiscovery()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buscar Impresoras Wi-Fi") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { wifiDiscovery.startDiscovery() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Buscar nuevamente")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(
                                    text = if (isSearching) "Buscando en la red local..." else "Búsqueda finalizada",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = if (isSearching) "Detectando impresoras disponibles" else "${discoveredPrinters.size} impresora(s) encontrada(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Discovered printers header
            item {
                Text(
                    text = "Impresoras detectadas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (discoveredPrinters.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSearching) "Escaneando red..." else "No se encontraron impresoras automáticamente.\nPuede conectar una manualmente por IP a continuación.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(discoveredPrinters) { printer ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    val wifiPrinter = WifiPrinter(printer.ipAddress, printer.port)
                                    printerManager.setPrinter(
                                        wifiPrinter,
                                        "${printer.name} (${printer.ipAddress})",
                                        printerType = "WIFI",
                                        ipAddress = printer.ipAddress,
                                        port = printer.port
                                    )
                                    printerManager.connect()
                                    wifiDiscovery.stopDiscovery()
                                    onBack()
                                }
                            },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(printer.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("IP: ${printer.ipAddress}:${printer.port}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // Manual Connection Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Conexión Manual por IP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = manualName,
                            onValueChange = { manualName = it },
                            label = { Text("Nombre") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualIp,
                                onValueChange = { manualIp = it },
                                label = { Text("IP (ej. 192.168.1.50)") },
                                modifier = Modifier.weight(2f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = manualPort,
                                onValueChange = { manualPort = it },
                                label = { Text("Puerto") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Button(
                            onClick = {
                                val portInt = manualPort.toIntOrNull() ?: 9100
                                if (manualIp.isNotBlank()) {
                                    scope.launch {
                                        val wifiPrinter = WifiPrinter(manualIp.trim(), portInt)
                                        printerManager.setPrinter(
                                            wifiPrinter,
                                            "$manualName (${manualIp.trim()})",
                                            printerType = "WIFI",
                                            ipAddress = manualIp.trim(),
                                            port = portInt
                                        )
                                        printerManager.connect()
                                        wifiDiscovery.stopDiscovery()
                                        onBack()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = manualIp.isNotBlank()
                        ) {
                            Text("Conectar por IP Manual")
                        }
                    }
                }
            }
        }
    }
}
