package ar.mimbi.Selfie.printing.ui.owner

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
import ar.mimbi.Selfie.printing.model.BatchStatus
import ar.mimbi.Selfie.printing.model.PrintBatch
import ar.mimbi.Selfie.printing.model.PrintItem
import ar.mimbi.Selfie.printing.model.PrintItemStatus
import ar.mimbi.Selfie.printing.queue.PrintQueueManager
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
    val printingCount = items.count { it.status == PrintItemStatus.PRINTING }
    val printedCount = items.count { it.status == PrintItemStatus.PRINTED }
    val failedCount = items.count { it.status == PrintItemStatus.FAILED }

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
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCard("Pendientes", "$pendingCount", MaterialTheme.colorScheme.primaryContainer)
                StatCard("Imprimiendo", "$printingCount", MaterialTheme.colorScheme.tertiaryContainer)
                StatCard("Impresos", "$printedCount", MaterialTheme.colorScheme.surfaceVariant)
                StatCard("Fallidos", "$failedCount", MaterialTheme.colorScheme.errorContainer)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            queueManager.printRemaining(context)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Imprimir Restantes")
                }

                if (failedCount > 0) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                queueManager.retryFailed(context)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reintentar Fallidos")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Items en Cola (${items.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ItemRow(item)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, containerColor: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ItemRow(item: PrintItem) {
    val statusColor = when (item.status) {
        PrintItemStatus.PENDING -> MaterialTheme.colorScheme.primary
        PrintItemStatus.PRINTING -> MaterialTheme.colorScheme.tertiary
        PrintItemStatus.PRINTED -> MaterialTheme.colorScheme.outline
        PrintItemStatus.FAILED -> MaterialTheme.colorScheme.error
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
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Item #${item.id} (Lote #${item.batchId})", fontWeight = FontWeight.Bold)
                Text(text = item.photoUri, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }

            Surface(
                color = statusColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = item.status.name,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
