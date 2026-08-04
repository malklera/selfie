package ar.mimbi.Selfie.ui.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ar.mimbi.Selfie.data.AppConfig

@Composable
fun ConfigurationScreen(
    initialConfig: AppConfig,
    onSave: (AppConfig) -> Unit,
    onClose: () -> Unit
) {
    Log.d("ConfigScreen", "ConfigurationScreen composed")
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // High-visibility header row with large standard buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                Log.d("ConfigScreen", "Header Close clicked")
                if (hasChanges) showUnsavedDialog = true else onClose()
            }) {
                Text("CERRAR")
            }
            
            Text("Configuración", style = MaterialTheme.typography.headlineSmall)
            
            Button(onClick = {
                Log.d("ConfigScreen", "Header Save clicked")
                val finalSeconds = countdownSeconds.toIntOrNull() ?: 3
                onSave(AppConfig(portadaPath, finalSeconds, destinationPath))
            }) {
                Text("GUARDAR")
            }
        }

        HorizontalDivider()

        // Content Column - Scroll disabled temporarily to rule it out
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Portada
            Column {
                Text("Portada", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Vista previa: ${portadaPath ?: "Ninguna"}")
                Button(onClick = { 
                    Log.d("ConfigScreen", "Select Image clicked")
                    imagePicker.launch("image/*") 
                }) {
                    Text("Seleccionar Imagen")
                }
            }

            // Cuenta regresiva
            Column {
                Text("Cuenta regresiva", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = countdownSeconds,
                    onValueChange = { 
                        if (it.all { char -> char.isDigit() }) countdownSeconds = it 
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Destino
            Column {
                Text("Destino", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ruta: $destinationPath")
                Button(onClick = { 
                    Log.d("ConfigScreen", "Select Folder clicked")
                    directoryPicker.launch(null) 
                }) {
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
                    Log.d("ConfigScreen", "Dialog Save clicked")
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
                        Log.d("ConfigScreen", "Dialog Discard clicked")
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
