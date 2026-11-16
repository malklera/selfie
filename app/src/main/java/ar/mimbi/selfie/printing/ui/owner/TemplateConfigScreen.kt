package ar.mimbi.selfie.printing.ui.owner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ar.mimbi.selfie.data.AppOrientation
import ar.mimbi.selfie.data.ConfigSessionState
import ar.mimbi.selfie.printing.model.PrintTemplate
import ar.mimbi.selfie.printing.queue.PrintQueueManager
import ar.mimbi.selfie.printing.template.DefaultTemplates
import ar.mimbi.selfie.ui.components.EnforceAppOrientation
import kotlinx.coroutines.launch
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateConfigScreen(
    queueManager: PrintQueueManager,
    appOrientation: AppOrientation? = null,
    onBack: () -> Unit,
    session: ConfigSessionState? = null
) {
    val currentOrientation = session?.appOrientation ?: appOrientation
    EnforceAppOrientation(currentOrientation)

    val activeTemplateDb by queueManager.activeTemplateFlow.collectAsState(initial = DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE)
    val selectedTemplateId = session?.selectedTemplateId ?: activeTemplateDb.id

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val selectedIndex = remember(selectedTemplateId) {
        DefaultTemplates.ALL_TEMPLATES.indexOfFirst { it.id == selectedTemplateId }.coerceAtLeast(0)
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            listState.scrollToItem(selectedIndex)
        }
    }

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
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(DefaultTemplates.ALL_TEMPLATES, key = { it.id }) { template ->
                    TemplateItemCard(
                        template = template,
                        isSelected = template.id == selectedTemplateId,
                        onSelect = {
                            if (session != null) {
                                session.selectedTemplateId = template.id
                            } else {
                                scope.launch {
                                    queueManager.setActiveTemplate(template.id)
                                }
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
            // Template Visual Preview Canvas (A4 aspect ratio: 80dp x 113.14dp, straight paper edges)
            Box(
                modifier = Modifier
                    .size(80.dp, 113.14.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RectangleShape)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    val slotFillColor = Color(0xFF1976D2).copy(alpha = 0.25f)
                    val slotStrokeColor = Color(0xFF1565C0)

                    val strokePx = 1.5.dp.toPx()
                    val halfStroke = strokePx / 2f

                    template.slots.forEach { slot ->
                        val rawLeft = slot.left * w
                        val rawTop = slot.top * h
                        val rawWidth = slot.width * w
                        val rawHeight = slot.height * h

                        val slotLeft = rawLeft.coerceIn(0f, w)
                        val slotTop = rawTop.coerceIn(0f, h)
                        val slotWidth = rawWidth.coerceAtMost(w - slotLeft)
                        val slotHeight = rawHeight.coerceAtMost(h - slotTop)

                        if (slotWidth > 0f && slotHeight > 0f) {
                            // 1. Slot background fill
                            drawRect(
                                color = slotFillColor,
                                topLeft = Offset(slotLeft, slotTop),
                                size = Size(slotWidth, slotHeight)
                            )

                            // 2. Slot border stroke (inset by halfStroke to never bleed past bounds)
                            val strokeLeft = (slotLeft + halfStroke).coerceIn(halfStroke, w - halfStroke)
                            val strokeTop = (slotTop + halfStroke).coerceIn(halfStroke, h - halfStroke)
                            val strokeW = (slotWidth - strokePx).coerceAtLeast(0f)
                            val strokeH = (slotHeight - strokePx).coerceAtLeast(0f)

                            if (strokeW > 0f && strokeH > 0f) {
                                drawRect(
                                    color = slotStrokeColor,
                                    topLeft = Offset(strokeLeft, strokeTop),
                                    size = Size(strokeW, strokeH),
                                    style = Stroke(width = strokePx)
                                )
                            }

                            // 3. Photo symbol in center of slot (rotated 90° if slot is landscape)
                            val centerX = slotLeft + slotWidth / 2f
                            val centerY = slotTop + slotHeight / 2f
                            val isLandscape = slotWidth > slotHeight

                            val baseW = min(slotWidth, slotHeight) * 0.32f
                            val baseH = min(slotWidth, slotHeight) * 0.48f

                            if (baseW > 4f && baseH > 4f) {
                                withTransform({
                                    if (isLandscape) {
                                        rotate(90f, pivot = Offset(centerX, centerY))
                                    }
                                }) {
                                    // Vertical photo frame (rotated 90° via transform for landscape slots)
                                    drawRect(
                                        color = slotStrokeColor.copy(alpha = 0.7f),
                                        topLeft = Offset(centerX - baseW / 2f, centerY - baseH / 2f),
                                        size = Size(baseW, baseH),
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                    // Sun circle in top-left of photo frame
                                    val sunRadius = baseW * 0.15f
                                    drawCircle(
                                        color = slotStrokeColor.copy(alpha = 0.7f),
                                        radius = sunRadius,
                                        center = Offset(centerX - baseW * 0.22f, centerY - baseH * 0.22f)
                                    )
                                    // Mountain path in photo frame
                                    val mountainPath = Path().apply {
                                        moveTo(centerX - baseW * 0.35f, centerY + baseH * 0.35f)
                                        lineTo(centerX, centerY - baseH * 0.1f)
                                        lineTo(centerX + baseW * 0.35f, centerY + baseH * 0.35f)
                                        close()
                                    }
                                    drawPath(
                                        path = mountainPath,
                                        color = slotStrokeColor.copy(alpha = 0.5f),
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            val pageWidthMm = formatMm(template.widthCm * 10f)
            val pageHeightMm = formatMm(template.heightCm * 10f)

            val maxSlot = template.slots.maxByOrNull { it.width * it.height }
            val maxPictureWidthMm = formatMm((maxSlot?.width ?: 0f) * template.widthCm * 10f)
            val maxPictureHeightMm = formatMm((maxSlot?.height ?: 0f) * template.heightCm * 10f)

            val photoCountText = if (template.slotCount == 1) "1 foto" else "${template.slotCount} fotos"

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Plantilla ${template.id}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${template.pageType} - ${pageWidthMm}x${pageHeightMm} mm",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = template.colorMode,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = photoCountText,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tamaño: ${maxPictureWidthMm}x${maxPictureHeightMm} mm",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun formatMm(mm: Float): String {
    val rounded = kotlin.math.round(mm * 10f) / 10f
    return if (rounded % 1f == 0f) {
        rounded.toInt().toString()
    } else {
        "%.1f".format(java.util.Locale.US, rounded)
    }
}
