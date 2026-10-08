package ar.mimbi.Selfie.printing.ui.owner

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.mimbi.Selfie.printing.model.PrintItem
import ar.mimbi.Selfie.printing.model.PrintItemStatus
import ar.mimbi.Selfie.printing.queue.PrintQueueManager
import ar.mimbi.Selfie.printing.template.DefaultTemplates
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueManagementScreen(
    queueManager: PrintQueueManager,
    onBack: () -> Unit
) {
    val items by queueManager.allItemsFlow.collectAsState(initial = emptyList())
    val batches by queueManager.allBatchesFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pendingCount = items.count { it.status == PrintItemStatus.PENDING }
    val queuedCount = items.count { it.status == PrintItemStatus.QUEUED }
    val printingCount = items.count { it.status == PrintItemStatus.PRINTING || it.status == PrintItemStatus.BLOCKED }
    val printedCount = items.count { it.status == PrintItemStatus.PRINTED }
    val failedCount = items.count { it.status == PrintItemStatus.FAILED || it.status == PrintItemStatus.CANCELED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Cola de Impresión") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                // Stats
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatRow("Pendientes (Esperando impresora)", "$pendingCount")
                    StatRow("En cola de impresión", "$queuedCount")
                    StatRow("Imprimiendo / Bloqueados", "$printingCount")
                    StatRow("Completados", "$printedCount")
                    StatRow("Fallidos / Cancelados", "$failedCount")
                }
            }

            item {
                // Action Buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                queueManager.printRemaining(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Imprimir Restantes", maxLines = 1)
                    }

                    if (failedCount > 0) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    queueManager.retryFailed(context)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reintentar Fallidos", maxLines = 1)
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Cola de impresión (${items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            items(items, key = { it.id }) { item ->
                val batch = batches.find { it.id == item.batchId }
                val templateId = batch?.templateId ?: 1
                val template = DefaultTemplates.getById(templateId)
                val slotCount = template.slotCount.coerceAtLeast(1)

                val batchItems = items.filter { it.batchId == item.batchId }.sortedBy { it.sequence }
                val itemIndex = batchItems.indexOfFirst { it.id == item.id } + 1
                val pageNumber = if (itemIndex > 0) (itemIndex - 1) / slotCount + 1 else 1

                ItemRow(item, templateId, pageNumber)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ItemRow(item: PrintItem, templateId: Int, pageNumber: Int) {
    val statusColor = when (item.status) {
        PrintItemStatus.PENDING -> MaterialTheme.colorScheme.primary
        PrintItemStatus.QUEUED -> MaterialTheme.colorScheme.secondary
        PrintItemStatus.PRINTING -> MaterialTheme.colorScheme.tertiary
        PrintItemStatus.BLOCKED -> Color(0xFFE65100)
        PrintItemStatus.PRINTED -> MaterialTheme.colorScheme.outline
        PrintItemStatus.FAILED -> MaterialTheme.colorScheme.error
        PrintItemStatus.CANCELED -> Color(0xFF757575)
    }

    val statusText = when (item.status) {
        PrintItemStatus.PENDING -> "Pendiente (Esperando impresora disponible)"
        PrintItemStatus.QUEUED -> "En cola de impresión"
        PrintItemStatus.PRINTING -> "Imprimiendo / Enviando a la impresora"
        PrintItemStatus.BLOCKED -> "Bloqueado (Verificar impresora / sin papel)"
        PrintItemStatus.PRINTED -> "Completado exitosamente"
        PrintItemStatus.FAILED -> "Fallido (Error de impresión)"
        PrintItemStatus.CANCELED -> "Cancelado por el usuario"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Half 1: Job Description (50% width)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "#${item.id} - Plantilla $templateId - Página $pageNumber",
                    fontWeight = FontWeight.Bold,
                    softWrap = true
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.photoUri,
                    style = MaterialTheme.typography.bodySmall,
                    softWrap = true
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Half 2: State Report Badge (50% width, wraps long status text)
            Surface(
                modifier = Modifier.weight(1f),
                color = statusColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = statusText,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    softWrap = true
                )
            }
        }
    }
}
