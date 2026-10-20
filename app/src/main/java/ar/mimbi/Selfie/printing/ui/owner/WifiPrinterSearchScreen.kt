package ar.mimbi.Selfie.printing.ui.owner

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.mimbi.Selfie.printing.printer.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiPrinterSearchScreen(
    printerManager: PrinterManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) {
            focusManager.clearFocus()
        }
    }

    val unifiedDiscovery = remember { UnifiedPrinterDiscovery(context) }
    val discoveredPrinters by unifiedDiscovery.discoveredPrinters.collectAsState()
    val isSearching by unifiedDiscovery.isSearching.collectAsState()

    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        unifiedDiscovery.startDiscovery()
        onDispose {
            unifiedDiscovery.stopDiscovery()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buscar Impresoras") },
                navigationIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        onBack()
                    }) {
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
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            focusManager.clearFocus()
                        }
                    )
                }
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Búsqueda Automática
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Impresoras Encontradas",
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
                                onClick = {
                                    focusManager.clearFocus()
                                    unifiedDiscovery.startDiscovery()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Buscar nuevamente")
                            }
                        }
                    }

                    Text(
                        text = when {
                            isSearching -> "Buscando impresoras por Wi-Fi, Bluetooth y USB..."
                            discoveredPrinters.isEmpty() -> "No se encontraron impresoras activas por Wi-Fi, Bluetooth o USB."
                            else -> "Se encontraron ${discoveredPrinters.size} impresora(s):"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (discoveredPrinters.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            discoveredPrinters.forEach { printer ->
                                DiscoveredPrinterCard(
                                    printer = printer,
                                    onClick = {
                                        focusManager.clearFocus()
                                        scope.launch {
                                            when (printer.connectionType) {
                                                PrinterConnectionType.WIFI_MDNS, PrinterConnectionType.WIFI_SUBNET, PrinterConnectionType.WIFI_DIRECT -> {
                                                    val ip = printer.ipAddress ?: return@launch
                                                    val port = printer.port ?: 9100
                                                    val wifiPrinter = WifiPrinter(ip, port)
                                                    printerManager.setPrinter(
                                                        printer = wifiPrinter,
                                                        printerName = "${printer.name} ($ip)",
                                                        printerType = "WIFI",
                                                        ipAddress = ip,
                                                        port = port
                                                    )
                                                }
                                                PrinterConnectionType.USB_HOST -> {
                                                    val devName = printer.usbDevice?.deviceName ?: printer.name
                                                    val usbPrinter = UsbPrinter(context, devName)
                                                    printerManager.setPrinter(
                                                        printer = usbPrinter,
                                                        printerName = printer.name,
                                                        printerType = "USB",
                                                        deviceIdentifier = devName
                                                    )
                                                }
                                                PrinterConnectionType.BLUETOOTH -> {
                                                    val mac = printer.bluetoothAddress ?: return@launch
                                                    val btPrinter = BluetoothPrinter(mac, printer.name)
                                                    printerManager.setPrinter(
                                                        printer = btPrinter,
                                                        printerName = printer.name,
                                                        printerType = "BLUETOOTH",
                                                        deviceIdentifier = mac
                                                    )
                                                }
                                                PrinterConnectionType.SYSTEM_ANDROID -> {
                                                    val androidPrinter = StandardAndroidPrinter(context)
                                                    printerManager.setPrinter(
                                                        printer = androidPrinter,
                                                        printerName = "Servicio de Impresión Android (Standard)",
                                                        printerType = "SYSTEM_ANDROID"
                                                    )
                                                }
                                            }

                                            val success = printerManager.connect()
                                            if (success) {
                                                Toast.makeText(context, "Conectado y verificado exitosamente a ${printer.name}", Toast.LENGTH_SHORT).show()
                                                unifiedDiscovery.stopDiscovery()
                                                onBack()
                                            } else {
                                                val errorMsg = (printerManager.getStatus() as? PrinterStatus.Error)?.message ?: "Error de conexión"
                                                Toast.makeText(context, "Error al conectar con ${printer.name}: $errorMsg", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                )
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
                        text = "Conexión Manual por IP Wi-Fi",
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

                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() || char == '.' }) manualIp = it
                        },
                        label = { Text("IPv4") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = manualPort,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() }) manualPort = it
                        },
                        label = { Text("Puerto (por defecto 9100)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            val portInt = manualPort.toIntOrNull() ?: 9100
                            if (manualIp.isNotBlank()) {
                                scope.launch {
                                    val wifiPrinter = WifiPrinter(manualIp.trim(), portInt)
                                    val printerName = if (manualName.isNotBlank()) {
                                        "$manualName (${manualIp.trim()})"
                                    } else {
                                        "Impresora Wi-Fi (${manualIp.trim()})"
                                    }
                                    printerManager.setPrinter(
                                        printer = wifiPrinter,
                                        printerName = printerName,
                                        printerType = "WIFI",
                                        ipAddress = manualIp.trim(),
                                        port = portInt
                                    )
                                    val success = printerManager.connect()
                                    if (success) {
                                        Toast.makeText(context, "Conectado y verificado exitosamente a $printerName", Toast.LENGTH_SHORT).show()
                                        unifiedDiscovery.stopDiscovery()
                                        onBack()
                                    } else {
                                        val errorMsg = (printerManager.getStatus() as? PrinterStatus.Error)?.message ?: "Error de conexión"
                                        Toast.makeText(context, "Error al conectar con ${manualIp.trim()}:$portInt: $errorMsg", Toast.LENGTH_LONG).show()
                                    }
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

@Composable
fun DiscoveredPrinterCard(
    printer: DiscoveredPrinter,
    onClick: () -> Unit
) {
    val connectionLabel = when (printer.connectionType) {
        PrinterConnectionType.WIFI_MDNS, PrinterConnectionType.WIFI_SUBNET, PrinterConnectionType.WIFI_DIRECT -> "Wi-Fi"
        PrinterConnectionType.BLUETOOTH -> "Bluetooth"
        PrinterConnectionType.USB_HOST -> "USB"
        PrinterConnectionType.SYSTEM_ANDROID -> "Sistema Android"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Main Printer Name as Card Title
            Text(
                text = printer.name,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            // Connection type + Address/Details
            Text(
                text = "Conexión: $connectionLabel • ${printer.details}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
