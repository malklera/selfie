package ar.mimbi.Selfie.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
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
                                Toast.makeText(context, "Configuración guardada", Toast.LENGTH_SHORT).show()
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
                            Text("Ruta: ${formatPathForDisplay(context, path)}", style = MaterialTheme.typography.bodySmall)
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
                        Text("Ruta: ${formatPathForDisplay(context, destinationPath)}")
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
                    Toast.makeText(context, "Configuración guardada", Toast.LENGTH_SHORT).show()
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

private fun formatPathForDisplay(context: Context, uriString: String?): String {
    if (uriString.isNullOrEmpty()) return "Ninguna"

    val internalStorageLabels = listOf(
        "/storage/emulated/0" to "Almacenamiento interno",
        "/sdcard" to "Almacenamiento interno",
        "/mnt/sdcard" to "Almacenamiento interno"
    )

    for ((prefix, label) in internalStorageLabels) {
        if (uriString.startsWith(prefix)) {
            return uriString.replaceFirst(prefix, label)
        }
    }

    try {
        val uri = Uri.parse(uriString)
        if (uri.scheme == "content") {
            var resolvedUri = uri
            
            // 1. Resolve SAF Media Documents to real MediaStore URIs to get path info
            if (DocumentsContract.isDocumentUri(context, uri)) {
                if ("com.android.providers.media.documents" == uri.authority) {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":")
                    if (split.size >= 2) {
                        val type = split[0]
                        val id = split[1]
                        val baseUri = when (type) {
                            "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            else -> null
                        }
                        if (baseUri != null) resolvedUri = Uri.withAppendedPath(baseUri, id)
                    }
                }
            }

            // 2. Try Database Resolution (MediaStore/Resolved SAF)
            try {
                val projection = mutableListOf(OpenableColumns.DISPLAY_NAME)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
                }
                projection.add(MediaStore.MediaColumns.DATA)

                context.contentResolver.query(resolvedUri, projection.toTypedArray(), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                        var path: String? = null
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val relPathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                            if (relPathIndex != -1) path = cursor.getString(relPathIndex)
                        }
                        
                        if (path.isNullOrEmpty()) {
                            val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (dataIndex != -1) {
                                val fullPath = cursor.getString(dataIndex)
                                if (!fullPath.isNullOrEmpty()) path = fullPath.substringBeforeLast('/', "")
                            }
                        }

                        if (!path.isNullOrEmpty()) {
                            val cleanPath = path.trim('/')
                            val friendlyPath = if (cleanPath.startsWith("0/")) {
                                "Almacenamiento interno/" + cleanPath.substringAfter("0/")
                            } else {
                                cleanPath.replace("emulated/0", "Almacenamiento interno")
                            }
                            return "$friendlyPath/$name".replace("//", "/")
                        }
                    }
                }
            } catch (e: Exception) { /* Ignore */ }

            // 3. SAF Tree/Document Manual Parsing (for File Manager picks)
            var docId: String? = null
            try {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    docId = DocumentsContract.getDocumentId(uri)
                } else {
                    docId = try { DocumentsContract.getTreeDocumentId(uri) } catch (e: Exception) { null }
                }
            } catch (e: Exception) { /* Ignore */ }

            if (docId == null) {
                val decodedUri = Uri.decode(uriString)
                if (decodedUri.contains("primary:")) {
                    docId = "primary:" + decodedUri.substringAfter("primary:")
                }
            }

            if (docId != null) {
                if (docId.startsWith("primary:")) {
                    val relativePath = docId.substringAfter("primary:").trim('/')
                    return if (relativePath.isEmpty()) "Almacenamiento interno" else "Almacenamiento interno/$relativePath"
                } else if (docId.contains(":")) {
                    val volume = docId.substringBefore(":")
                    val path = docId.substringAfter(":").trim('/')
                    return if (volume == "primary") {
                        if (path.isEmpty()) "Almacenamiento interno" else "Almacenamiento interno/$path"
                    } else {
                        "$volume/$path"
                    }
                }
            }

            // 4. Last resort: Filename from OpenableColumns or URI segments
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrEmpty()) return name
                    }
                }
            } catch (e: Exception) { /* Ignore */ }

            val decoded = Uri.decode(uriString)
            val segments = decoded.split("/").filter { it.isNotEmpty() }
            val lastSegment = segments.lastOrNull()
            if (lastSegment != null) {
                if (lastSegment.contains("primary:")) {
                    return "Almacenamiento interno/" + lastSegment.substringAfter("primary:")
                }
                return lastSegment
            }
            return decoded
        } else if (uri.scheme == "file") {
            val path = uri.path
            if (path != null) {
                for ((prefix, label) in internalStorageLabels) {
                    if (path.startsWith(prefix)) return path.replaceFirst(prefix, label)
                }
                return path
            }
        }
    } catch (e: Exception) { /* Ignore */ }

    return Uri.decode(uriString)
}
