package ar.mimbi.Selfie.printing.ui.owner

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.mimbi.Selfie.data.AppOrientation
import ar.mimbi.Selfie.data.UserActionTracker
import ar.mimbi.Selfie.printing.model.PrintItem
import ar.mimbi.Selfie.printing.model.PrintItemStatus
import ar.mimbi.Selfie.printing.model.PrintTemplate
import ar.mimbi.Selfie.printing.queue.PrintQueueManager
import ar.mimbi.Selfie.ui.components.EnforceAppOrientation
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.ceil

private enum class StatusFilter(val label: String) {
    ALL("Todos"),
    PENDING("Pendientes"),
    SENT("Enviados"),
    PRINTED("Impresos"),
    FAILED("Fallidos")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueManagementScreen(
    queueManager: PrintQueueManager,
    appOrientation: AppOrientation? = null,
    onBack: () -> Unit
) {
    EnforceAppOrientation(appOrientation)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val allItems by queueManager.allItemsFlow.collectAsState(initial = emptyList())
    val activeTemplate by queueManager.activeTemplateFlow.collectAsState(initial = null)

    var selectedItemIds by remember { mutableStateOf(setOf<Long>()) }
    var currentFilter by remember { mutableStateOf(StatusFilter.ALL) }
    var showMenu by remember { mutableStateOf(false) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showClearPrintedConfirmDialog by remember { mutableStateOf(false) }

    // Filter items based on selected tab
    val filteredItems = remember(allItems, currentFilter) {
        when (currentFilter) {
            StatusFilter.ALL -> allItems
            StatusFilter.PENDING -> allItems.filter { it.status == PrintItemStatus.PENDING }
            StatusFilter.SENT -> allItems.filter { it.status == PrintItemStatus.SENT }
            StatusFilter.PRINTED -> allItems.filter { it.status == PrintItemStatus.PRINTED }
            StatusFilter.FAILED -> allItems.filter {
                it.status == PrintItemStatus.FAILED || it.status == PrintItemStatus.CANCELED
            }
        }
    }

    val isAllSelected = remember(filteredItems, selectedItemIds) {
        filteredItems.isNotEmpty() && filteredItems.all { it.id in selectedItemIds }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Gestión de Cola") },
                navigationIcon = {
                    IconButton(onClick = {
                        UserActionTracker.trackAction("Volver desde gestión de cola")
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Imprimir restantes pendientes") },
                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    val success = queueManager.printRemaining(context)
                                    if (success) {
                                        Toast.makeText(context, "Procesando pendientes...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Impresora no lista", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Reintentar fallidos") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    queueManager.retryFailed(context)
                                    Toast.makeText(context, "Reintentando fallidos...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Limpiar impresos") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showClearPrintedConfirmDialog = true
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Vaciar cola completa", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showClearConfirmDialog = true
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Active Template & Selection Info Banner
            activeTemplate?.let { template ->
                item(key = "template_banner") {
                    TemplateInfoBanner(
                        template = template,
                        selectedCount = selectedItemIds.size
                    )
                }
            }

            // Selection Controls & Action Bar
            item(key = "selection_controls") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    if (isAllSelected) {
                                        val filteredIds = filteredItems.map { it.id }.toSet()
                                        selectedItemIds = selectedItemIds - filteredIds
                                    } else {
                                        val filteredIds = filteredItems.map { it.id }.toSet()
                                        selectedItemIds = selectedItemIds + filteredIds
                                    }
                                }
                            ) {
                                Text(
                                    text = if (isAllSelected) "Desmarcar todo" else "Seleccionar todo",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "(${selectedItemIds.size}/${allItems.size})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Action buttons when items are selected
                        if (selectedItemIds.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val idsToPrint = selectedItemIds.toList()
                                        UserActionTracker.trackAction("Imprimir ${idsToPrint.size} elementos seleccionados")
                                        coroutineScope.launch {
                                            val success = queueManager.printSelectedItems(context, idsToPrint)
                                            if (success) {
                                                Toast.makeText(
                                                    context,
                                                    "Enviado al servicio de impresión de Android",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                selectedItemIds = emptySet()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Error al iniciar servicio de impresión",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Imprimir (${selectedItemIds.size})", maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = {
                                        showDeleteConfirmDialog = true
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Quitar (${selectedItemIds.size})", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // Filter Chips Horizontal Scrollable Row (LazyRow prevents text wrapping inside buttons)
            item(key = "filter_chips") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(StatusFilter.entries.toTypedArray(), key = { it.name }) { filter ->
                        val count = remember(allItems, filter) {
                            when (filter) {
                                StatusFilter.ALL -> allItems.size
                                StatusFilter.PENDING -> allItems.count { it.status == PrintItemStatus.PENDING }
                                StatusFilter.SENT -> allItems.count { it.status == PrintItemStatus.SENT }
                                StatusFilter.PRINTED -> allItems.count { it.status == PrintItemStatus.PRINTED }
                                StatusFilter.FAILED -> allItems.count {
                                    it.status == PrintItemStatus.FAILED || it.status == PrintItemStatus.CANCELED
                                }
                            }
                        }
                        FilterChip(
                            selected = currentFilter == filter,
                            onClick = { currentFilter = filter },
                            label = { Text("${filter.label} ($count)", maxLines = 1, overflow = TextOverflow.Clip) }
                        )
                    }
                }
            }

            // List of items
            if (filteredItems.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay elementos en la cola para este filtro",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    PrintItemCard(
                        item = item,
                        isSelected = item.id in selectedItemIds,
                        onToggleSelect = {
                            selectedItemIds = if (item.id in selectedItemIds) {
                                selectedItemIds - item.id
                            } else {
                                selectedItemIds + item.id
                            }
                        }
                    )
                }
            }
        }
    }

    // Confirmation dialog for deleting selected items
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Quitar de la cola") },
            text = { Text("¿Desea quitar los ${selectedItemIds.size} elementos seleccionados de la cola de impresión?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idsToRemove = selectedItemIds.toList()
                        showDeleteConfirmDialog = false
                        coroutineScope.launch {
                            queueManager.deleteItems(idsToRemove)
                            selectedItemIds = emptySet()
                            Toast.makeText(context, "Elementos eliminados de la cola", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Quitar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmation dialog for clearing queue
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Vaciar cola de impresión") },
            text = { Text("¿Está seguro de que desea eliminar todos los elementos de la cola?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        coroutineScope.launch {
                            queueManager.clearQueue()
                            selectedItemIds = emptySet()
                            Toast.makeText(context, "Cola vaciada", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Vaciar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmation dialog for clearing printed items
    if (showClearPrintedConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearPrintedConfirmDialog = false },
            title = { Text("Limpiar impresos") },
            text = { Text("¿Desea eliminar de la cola todos los elementos ya impresos?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearPrintedConfirmDialog = false
                        coroutineScope.launch {
                            queueManager.clearPrintedItems()
                            Toast.makeText(context, "Impresos eliminados", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Limpiar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearPrintedConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun TemplateInfoBanner(
    template: PrintTemplate,
    selectedCount: Int
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Plantilla Activa: ${template.description}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (selectedCount > 0) {
                val slotCount = template.slotCount.coerceAtLeast(1)
                val estimatedPages = ceil(selectedCount.toFloat() / slotCount).toInt()
                Text(
                    text = "Generará un PDF de $estimatedPages página(s) A4 ($slotCount foto(s) por página).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Seleccione fotos de la cola para imprimirlas o quitarlas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrintItemCard(
    item: PrintItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit
) {
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            CardDefaults.outlinedCardBorder()
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onToggleSelect() },
                onLongClick = { onToggleSelect() }
            )
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = item.photoUri,
                contentDescription = "Foto ${item.sequence}",
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Foto ${item.sequence}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Lote ${item.batchId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item.photoUri.substringAfterLast('/'),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            StatusBadge(status = item.status)
        }
    }
}

@Composable
private fun StatusBadge(status: PrintItemStatus) {
    val (backgroundColor, textColor, label) = when (status) {
        PrintItemStatus.PENDING -> Triple(Color(0xFFFFF3CD), Color(0xFF856404), "Pendiente")
        PrintItemStatus.QUEUED -> Triple(Color(0xFFE2E3E5), Color(0xFF383D41), "En cola")
        PrintItemStatus.PRINTING -> Triple(Color(0xFFCCE5FF), Color(0xFF004085), "Imprimiendo")
        PrintItemStatus.BLOCKED -> Triple(Color(0xFFF8D7DA), Color(0xFF721C24), "Bloqueado")
        PrintItemStatus.SENT -> Triple(Color(0xFFD1ECF1), Color(0xFF0C5460), "Enviado")
        PrintItemStatus.PRINTED -> Triple(Color(0xFFD4EDDA), Color(0xFF155724), "Impreso")
        PrintItemStatus.FAILED -> Triple(Color(0xFFF8D7DA), Color(0xFF721C24), "Error")
        PrintItemStatus.CANCELED -> Triple(Color(0xFFE2E3E5), Color(0xFF383D41), "Cancelado")
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
