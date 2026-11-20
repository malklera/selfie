package ar.mimbi.selfie.printing.ui.owner

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import ar.mimbi.selfie.data.AppOrientation
import ar.mimbi.selfie.data.UserActionTracker
import ar.mimbi.selfie.printing.model.PrintItem
import ar.mimbi.selfie.printing.model.PrintPage
import ar.mimbi.selfie.printing.model.PrintPageStatus
import ar.mimbi.selfie.printing.model.PrintTemplate
import ar.mimbi.selfie.printing.model.QueueCounts
import ar.mimbi.selfie.printing.queue.PrintQueueManager
import ar.mimbi.selfie.ui.components.EnforceAppOrientation
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private enum class PageStatusFilter(val label: String) {
    ALL("Todas"),
    READY("Listas"),
    INCOMPLETE("Incompletas"),
    PRINTED("Impresas"),
    FAILED("Fallidas")
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

    var currentFilter by remember { mutableStateOf(PageStatusFilter.ALL) }

    val filterStatusParam = remember(currentFilter) {
        when (currentFilter) {
            PageStatusFilter.ALL -> null
            PageStatusFilter.READY -> "READY"
            PageStatusFilter.INCOMPLETE -> "INCOMPLETE"
            PageStatusFilter.PRINTED -> "PRINTED"
            PageStatusFilter.FAILED -> "FAILED"
        }
    }

    val displayedPages by remember(filterStatusParam) {
        queueManager.getPagesByFilterFlow(filterStatusParam)
    }.collectAsState(initial = emptyList())

    val queueCounts by queueManager.queueCountsFlow.collectAsState(initial = QueueCounts())
    val activeTemplate by queueManager.activeTemplateFlow.collectAsState(initial = null)

    var selectedPageIds by remember { mutableStateOf(setOf<Long>()) }
    var showMenu by remember { mutableStateOf(false) }

    var partialPrintPageConfirm by remember { mutableStateOf<PrintPage?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showClearPrintedConfirmDialog by remember { mutableStateOf(false) }

    val filteredPages = displayedPages

    val (eligibleSelectablePages, eligibleSelectableIds) = remember(displayedPages) {
        val eligible = displayedPages.filter { it.status == PrintPageStatus.READY }
        val ids = eligible.mapTo(HashSet(eligible.size)) { it.id }
        eligible to ids
    }

    val hasSelectedPages = selectedPageIds.isNotEmpty()

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
                            text = { Text("Imprimir páginas listas") },
                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    val success = queueManager.printReadyPages(context)
                                    if (success) {
                                        Toast.makeText(context, "Enviando páginas listas a impresión...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No hay páginas listas para imprimir", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Reintentar fallidas") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    queueManager.retryFailedPages(context)
                                    Toast.makeText(context, "Reintentando páginas fallidas...", Toast.LENGTH_SHORT).show()
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Active Template Banner
            item(key = "queue_banner") {
                QueueStatusBanner(
                    activeTemplate = activeTemplate
                )
            }

            // Selection Controls & Action Buttons (FlowRow without gray background card, transparent fill & gray border)
            item(key = "selection_controls") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (hasSelectedPages) {
                                selectedPageIds = emptySet()
                            } else {
                                selectedPageIds = eligibleSelectableIds
                            }
                        },
                        enabled = hasSelectedPages || eligibleSelectablePages.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (hasSelectedPages || eligibleSelectablePages.isNotEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                        )
                    ) {
                        Text(
                            text = if (hasSelectedPages) "Desmarcar todo" else "Seleccionar todo",
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val pageIdsToPrint = selectedPageIds.toList()
                            UserActionTracker.trackAction("Imprimir ${pageIdsToPrint.size} páginas seleccionadas")
                            coroutineScope.launch {
                                val success = queueManager.printPages(context, pageIdsToPrint)
                                if (success) {
                                    Toast.makeText(
                                        context,
                                        "Enviado al servicio de impresión de Android",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    selectedPageIds = emptySet()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Error al iniciar servicio de impresión",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = selectedPageIds.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (selectedPageIds.isNotEmpty()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                        )
                    ) {
                        Text(
                            text = "Imprimir (${selectedPageIds.size})",
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            showDeleteConfirmDialog = true
                        },
                        enabled = selectedPageIds.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.error,
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (selectedPageIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                        )
                    ) {
                        Text(
                            text = "Quitar (${selectedPageIds.size})",
                            maxLines = 1
                        )
                    }
                }
            }

            // Filter Chips Horizontal Scrollable Row
            item(key = "filter_chips") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PageStatusFilter.entries, key = { it.name }) { filter ->
                        val count = when (filter) {
                            PageStatusFilter.ALL -> queueCounts.total
                            PageStatusFilter.READY -> queueCounts.ready
                            PageStatusFilter.INCOMPLETE -> queueCounts.incomplete
                            PageStatusFilter.PRINTED -> queueCounts.printed
                            PageStatusFilter.FAILED -> queueCounts.failed
                        }
                        FilterChip(
                            selected = currentFilter == filter,
                            onClick = { currentFilter = filter },
                            label = { Text("${filter.label} ($count)", maxLines = 1, overflow = TextOverflow.Clip) }
                        )
                    }
                }
            }

            // List of pages (no horizontal dividers between items)
            if (filteredPages.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay páginas en la cola para este filtro",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredPages, key = { it.id }) { page ->
                    PrintPageCard(
                        page = page,
                        isSelected = page.id in selectedPageIds,
                        onToggleSelect = {
                            if (page.status == PrintPageStatus.READY ||
                                page.status == PrintPageStatus.FAILED ||
                                page.status == PrintPageStatus.CANCELED ||
                                page.status == PrintPageStatus.SENT ||
                                page.status == PrintPageStatus.PRINTED
                            ) {
                                selectedPageIds = if (page.id in selectedPageIds) {
                                    selectedPageIds - page.id
                                } else {
                                    selectedPageIds + page.id
                                }
                            }
                        },
                        onPrintPartial = {
                            partialPrintPageConfirm = page
                        },
                        onRetryPage = {
                            coroutineScope.launch {
                                queueManager.printPages(context, listOf(page.id))
                            }
                        }
                    )
                }
            }
        }
    }

    // Confirmation dialog for partial printing of INCOMPLETE page
    partialPrintPageConfirm?.let { page ->
        AlertDialog(
            onDismissRequest = { partialPrintPageConfirm = null },
            title = { Text("Imprimir página parcial") },
            text = {
                Text("¿Desea imprimir la Página ${page.pageNumber} con solo ${page.itemCount}/${page.slotCount} fotos?\n\nLa página se cerrará y no recibirá más fotos.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        partialPrintPageConfirm = null
                        coroutineScope.launch {
                            val success = queueManager.printPages(context, listOf(page.id), allowPartial = true)
                            if (success) {
                                Toast.makeText(context, "Enviando página parcial...", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Error al imprimir página parcial", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Imprimir parcial")
                }
            },
            dismissButton = {
                TextButton(onClick = { partialPrintPageConfirm = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Confirmation dialog for deleting selected pages
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Quitar de la cola") },
            text = { Text("¿Desea quitar las ${selectedPageIds.size} página(s) seleccionadas de la cola de impresión?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idsToRemove = selectedPageIds.toList()
                        showDeleteConfirmDialog = false
                        coroutineScope.launch {
                            queueManager.deletePages(idsToRemove)
                            selectedPageIds = emptySet()
                            Toast.makeText(context, "Páginas eliminadas de la cola", Toast.LENGTH_SHORT).show()
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
            text = { Text("¿Está seguro de que desea eliminar todas las páginas y fotos de la cola?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        coroutineScope.launch {
                            queueManager.clearQueue()
                            selectedPageIds = emptySet()
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
            text = { Text("¿Desea eliminar de la cola todas las páginas ya impresas?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearPrintedConfirmDialog = false
                        coroutineScope.launch {
                            queueManager.clearPrintedItems()
                            Toast.makeText(context, "Páginas impresas eliminadas", Toast.LENGTH_SHORT).show()
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
private fun QueueStatusBanner(
    activeTemplate: PrintTemplate?
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
            activeTemplate?.let { template ->
                Text(
                    text = "Plantilla Activa: ${template.description} (Id ${template.id})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PrintPageCard(
    page: PrintPage,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onPrintPartial: () -> Unit,
    onRetryPage: () -> Unit
) {
    val context = LocalContext.current
    val isSelectable = page.status == PrintPageStatus.READY ||
            page.status == PrintPageStatus.FAILED ||
            page.status == PrintPageStatus.CANCELED ||
            page.status == PrintPageStatus.SENT ||
            page.status == PrintPageStatus.PRINTED

    val requiresLongPressToSelect = page.status == PrintPageStatus.SENT ||
            page.status == PrintPageStatus.PRINTED ||
            page.status == PrintPageStatus.CANCELED

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
                enabled = isSelectable,
                onClick = {
                    if (requiresLongPressToSelect) {
                        Toast.makeText(
                            context,
                            "Mantén presionado para seleccionar esta página",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        onToggleSelect()
                    }
                },
                onLongClick = { onToggleSelect() }
            )
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Página {n} - Plantilla {id} - {pictures}/{slots} fotos - {state}
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = page.headerText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(8.dp))
                PageStatusBadge(status = page.status)
            }

            page.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Photo Slots Grid / List
            val itemsBySlot = remember(page.items) {
                page.items.associateBy { it.slotIndex }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (slotIndex in 0 until page.slotCount) {
                    val item = itemsBySlot[slotIndex]
                    SlotThumbnailBox(
                        slotIndex = slotIndex,
                        item = item,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Page Actions Row (if partial print or retry applies)
            if ((page.status == PrintPageStatus.INCOMPLETE && page.itemCount < page.slotCount && page.itemCount > 0) ||
                page.status == PrintPageStatus.FAILED || page.status == PrintPageStatus.CANCELED
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (page.status == PrintPageStatus.INCOMPLETE && page.itemCount < page.slotCount && page.itemCount > 0) {
                        OutlinedButton(
                            onClick = onPrintPartial,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text("Imprimir página parcial", maxLines = 1)
                        }
                    }

                    if (page.status == PrintPageStatus.FAILED || page.status == PrintPageStatus.CANCELED) {
                        OutlinedButton(
                            onClick = onRetryPage,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text("Reintentar página", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotThumbnailBox(
    slotIndex: Int,
    item: PrintItem?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (item != null) {
            AsyncImage(
                model = item.photoUri,
                contentDescription = "Ranura ${slotIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Slot badge top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${slotIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            // Empty placeholder
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Ranura libre",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "${slotIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun PageStatusBadge(status: PrintPageStatus) {
    val (backgroundColor, textColor) = when (status) {
        PrintPageStatus.INCOMPLETE -> Pair(Color(0xFFFFF3CD), Color(0xFF856404))
        PrintPageStatus.READY -> Pair(Color(0xFFD4EDDA), Color(0xFF155724))
        PrintPageStatus.SENT -> Pair(Color(0xFFD1ECF1), Color(0xFF0C5460))
        PrintPageStatus.PRINTING -> Pair(Color(0xFFCCE5FF), Color(0xFF004085))
        PrintPageStatus.BLOCKED -> Pair(Color(0xFFF8D7DA), Color(0xFF721C24))
        PrintPageStatus.PRINTED -> Pair(Color(0xFFD4EDDA), Color(0xFF155724))
        PrintPageStatus.FAILED -> Pair(Color(0xFFF8D7DA), Color(0xFF721C24))
        PrintPageStatus.CANCELED -> Pair(Color(0xFFE2E3E5), Color(0xFF383D41))
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = status.labelSpanish,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
