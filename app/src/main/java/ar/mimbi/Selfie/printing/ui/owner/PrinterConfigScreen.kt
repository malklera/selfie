package ar.mimbi.Selfie.printing.ui.owner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.mimbi.Selfie.printing.printer.PrinterManager
import ar.mimbi.Selfie.printing.printer.PrinterStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterConfigScreen(
    printerManager: PrinterManager,
    onBack: () -> Unit
) {
    val status by printerManager.statusFlow.collectAsState()
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
                    Text(text = "Impresora de Prueba / Abstracción Générica (Fake Printer)", style = MaterialTheme.typography.bodyMedium)
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
}
