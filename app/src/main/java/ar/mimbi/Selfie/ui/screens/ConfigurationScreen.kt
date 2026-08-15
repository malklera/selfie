package ar.mimbi.Selfie.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.mimbi.Selfie.data.AppConfig
import ar.mimbi.Selfie.BuildConfig
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationScreen(
    initialConfig: AppConfig,
    onSave: (AppConfig) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var portadaPath by remember { mutableStateOf(initialConfig.portadaPath) }
    var countdownSeconds by remember { mutableStateOf(initialConfig.countdownSeconds.toString()) }
    var destinationPath by remember { mutableStateOf(initialConfig.destinationPath) }
    
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val hasChanges = portadaPath != initialConfig.portadaPath ||
            countdownSeconds != initialConfig.countdownSeconds.toString() ||
            destinationPath != initialConfig.destinationPath

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            portadaPath = it.toString()
        }
    }

    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            destinationPath = it.toString()
        }
    }

    SelectionContainer {
        Scaffold(
            topBar = {
                DisableSelection {
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
                DisableSelection {
                    // Portada
                    Column {
                        Text("Portada", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        portadaPath?.let { path ->
                            Text("Ruta: $path", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            AsyncImage(
                                model = path,
                                contentDescription = "Vista previa de portada",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(Color.Gray.copy(alpha = 0.2f)),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        } ?: Text("Ninguna imagen seleccionada")

                        Button(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
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
                                if (it.all { char -> char.isDigit() }) countdownSeconds =
                                    it
                            },
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

                    // Acerca de
                    Column {
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Acerca de", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                Column {
                    val versionText = if (BuildConfig.GIT_TAG.isNotEmpty()) {
                        "${BuildConfig.GIT_TAG} (${BuildConfig.GIT_COMMIT})"
                    } else {
                        BuildConfig.GIT_COMMIT
                    }
                    Text(
                        "Versión: $versionText",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("Licencia: MIT", style = MaterialTheme.typography.bodyMedium)
                    Text("Autor: github.com/malklera", style = MaterialTheme.typography.bodyMedium)
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
