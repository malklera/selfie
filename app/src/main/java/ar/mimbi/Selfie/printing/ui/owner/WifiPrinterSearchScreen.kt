package ar.mimbi.Selfie.printing.ui.owner

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Búsqueda en Red Local
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Búsqueda en Red Local",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(
                                onClick = { wifiDiscovery.startDiscovery() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Buscar nuevamente")
                            }
                        }
                    }

                    Text(
                        text = when {
                            isSearching -> "Buscando impresoras en la red..."
                            discoveredPrinters.isEmpty() -> "No se encontraron impresoras automáticas."
                            else -> "${discoveredPrinters.size} impresora(s) encontrada(s):"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (discoveredPrinters.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            discoveredPrinters.forEach { printer ->
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
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                printer.name,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Text(
                                                "IP: ${printer.ipAddress}:${printer.port}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            "Conectar",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Card 2: Conexión Manual por IP
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            onValueChange = {
                                if (it.all { char -> char.isDigit() || char == '.' }) manualIp = it
                            },
                            label = { Text("IP (ej. 192.168.1.50)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(2f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = manualPort,
                            onValueChange = {
                                if (it.all { char -> char.isDigit() }) manualPort = it
                            },
                            label = { Text("Puerto") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
