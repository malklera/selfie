package ar.mimbi.Selfie.printing.ui.owner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.mimbi.Selfie.printing.model.PrintTemplate
import ar.mimbi.Selfie.printing.queue.PrintQueueManager
import ar.mimbi.Selfie.printing.template.DefaultTemplates
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateConfigScreen(
    queueManager: PrintQueueManager,
    onBack: () -> Unit
) {
    val activeTemplate by queueManager.activeTemplateFlow.collectAsState(initial = DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE)
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración de Plantillas") },
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
            Text(
                text = "Seleccione la plantilla activa para las fotos:",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(DefaultTemplates.ALL_TEMPLATES) { template ->
                    TemplateItemCard(
                        template = template,
                        isSelected = template.id == activeTemplate.id,
                        onSelect = {
                            scope.launch {
                                queueManager.setActiveTemplate(template.id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateItemCard(
    template: PrintTemplate,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Template Visual Preview Canvas
            Box(
                modifier = Modifier
                    .size(80.dp, 120.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val primaryColor = Color(0xFF1976D2)
                    template.slots.forEach { slot ->
                        drawRect(
                            color = primaryColor.copy(alpha = 0.3f),
                            topLeft = Offset(slot.left * size.width, slot.top * size.height),
                            size = Size(slot.width * size.width, slot.height * size.height)
                        )
                        drawRect(
                            color = primaryColor,
                            topLeft = Offset(slot.left * size.width, slot.top * size.height),
                            size = Size(slot.width * size.width, slot.height * size.height),
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${template.slotCount} foto(s) por página",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Seleccionado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
