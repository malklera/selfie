package ar.mimbi.Selfie.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.mimbi.Selfie.data.AppConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationScreen(
    initialConfig: AppConfig,
    onSave: (AppConfig) -> Unit,
    onClose: () -> Unit
) {
    var portadaPath by remember { mutableStateOf(initialConfig.portadaPath) }
    var countdownSeconds by remember { mutableStateOf(initialConfig.countdownSeconds.toString()) }
    var destinationPath by remember { mutableStateOf(initialConfig.destinationPath) }
    
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val hasChanges = portadaPath != initialConfig.portadaPath ||
            countdownSeconds != initialConfig.countdownSeconds.toString() ||
            destinationPath != initialConfig.destinationPath

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { portadaPath = it.toString() }
    }

    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { destinationPath = it.toString() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Configuración") },
                actions = {
                    IconButton(onClick = {
                        val finalSeconds = countdownSeconds.toIntOrNull() ?: 3
                        onSave(AppConfig(portadaPath, finalSeconds, destinationPath))
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Guardar")
                    }
                    IconButton(onClick = {
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
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Portada
            Column {
                Text("Portada", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Vista previa: ${portadaPath ?: "Ninguna"}")
                Button(onClick = { imagePicker.launch("image/*") }) {
                    Text("Seleccionar Imagen")
                }
            }

            // Cuenta regresiva
            Column {
                Text("Cuenta regresiva", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = countdownSeconds,
                    onValueChange = { if (it.all { char -> char.isDigit() }) countdownSeconds = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Destino
            Column {
                Text("Destino", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ruta: $destinationPath")
                Button(onClick = { directoryPicker.launch(null) }) {
                    Text("Seleccionar Carpeta")
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
                    val finalSeconds = countdownSeconds.toIntOrNull() ?: 3
                    onSave(AppConfig(portadaPath, finalSeconds, destinationPath))
                    showUnsavedDialog = false
                    onClose()
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showUnsavedDialog = false
                        onClose()
                    }) {
                        Text("Descartar")
                    }
                    TextButton(onClick = { showUnsavedDialog = false }) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
}
