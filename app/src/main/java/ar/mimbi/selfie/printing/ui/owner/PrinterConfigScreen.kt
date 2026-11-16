package ar.mimbi.selfie.printing.ui.owner

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.mimbi.selfie.data.AppOrientation
import ar.mimbi.selfie.data.UserActionTracker
import ar.mimbi.selfie.printing.printer.PrinterInfoHelper
import ar.mimbi.selfie.printing.printer.PrinterManager
import ar.mimbi.selfie.ui.components.EnforceAppOrientation
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterConfigScreen(
    appOrientation: AppOrientation? = null,
    printerManager: PrinterManager? = null,
    onBack: () -> Unit
) {
    EnforceAppOrientation(appOrientation)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()

    var printerInfo by remember { mutableStateOf(PrinterInfoHelper.queryPrinterDetails(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                printerInfo = PrinterInfoHelper.queryPrinterDetails(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
    val isPrintServiceAvailable = printManager != null

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Configuración de Impresora") },
                navigationIcon = {
                    IconButton(onClick = {
                        UserActionTracker.trackAction("Volver desde configuración de impresora")
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search and Connect Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Buscar y Conectar Impresoras",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Abra los ajustes de impresión del sistema para buscar impresoras disponibles en su red o emparejar nuevas impresoras por Wi-Fi, Bluetooth o USB.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Display detected printer / service status from Android Print Framework
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (printerInfo.activePrinters.isNotEmpty()) {
                            Text(
                                text = "Impresora(s) activa(s) conectada(s):",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            printerInfo.activePrinters.forEach { printerName ->
                                Text(
                                    text = "• $printerName",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else if (printerInfo.enabledServices.isNotEmpty()) {
                            Text(
                                text = "Servicio(s) de impresión activo(s):",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            printerInfo.enabledServices.forEach { serviceName ->
                                Text(
                                    text = "• $serviceName",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                text = "Los servicios están activados en el sistema. Al pulsar 'Imprimir Página de Prueba' o 'Abrir Ajustes', el sistema buscará las impresoras encendidas en su red Wi-Fi/Bluetooth/USB.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Estado: No hay servicios de impresión activos ni impresoras conectadas en este momento.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            UserActionTracker.trackAction("Abrir ajustes de impresión de Android")
                            try {
                                val intent = Intent(Settings.ACTION_PRINT_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    "No se pudieron abrir los ajustes de impresión del sistema",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Abrir Ajustes de Impresión de Android")
                    }
                }
            }

            // Test Print Card
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Prueba de Impresión",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Imprima una página de prueba para verificar que la impresora está correctamente conectada y configurada en el sistema.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            UserActionTracker.trackAction("Imprimir página de prueba")
                            if (printManager != null) {
                                try {
                                    printManager.print(
                                        "Prueba de Impresora Selfie",
                                        TestPagePrintAdapter(),
                                        null
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Error al iniciar trabajo de impresión: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Servicio de impresión no disponible",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = isPrintServiceAvailable,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Imprimir Página de Prueba")
                    }
                }
            }

            HorizontalDivider()

            // Real time printer query
            RealtimePrinterQuerySection(printerManager = printerManager)
        }
    }
}

@Composable
fun RealtimePrinterQuerySection(printerManager: PrinterManager? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var queryResult by remember {
        mutableStateOf(PrinterInfoHelper.queryFullPrinterDetails(context, printerManager))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                queryResult = PrinterInfoHelper.queryFullPrinterDetails(context, printerManager)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Resultado de la Consulta a la Impresora",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = queryResult.rawFormattedOutput,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private class TestPagePrintAdapter : PrintDocumentAdapter() {
    private var pdfDocument: PdfDocument? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }

        try {
            pdfDocument?.close()
        } catch (_: Exception) {}

        val newDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 size in points
        val page = newDoc.startPage(pageInfo)

        val canvas: Canvas = page.canvas
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isAntiAlias = true
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 14f
            isAntiAlias = true
        }

        canvas.drawText("Página de Prueba - Selfie App", 50f, 80f, titlePaint)
        canvas.drawText("Configuración de Impresora en Android", 50f, 120f, bodyPaint)
        canvas.drawText("Estado: Impresora conectada y funcionando.", 50f, 160f, bodyPaint)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateStr = dateFormat.format(Date())
        canvas.drawText("Fecha y hora: $dateStr", 50f, 200f, bodyPaint)

        newDoc.finishPage(page)
        pdfDocument = newDoc

        val info = PrintDocumentInfo.Builder("Pagina_Prueba_Selfie.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()

        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }

        val doc = pdfDocument
        if (doc == null) {
            callback.onWriteFailed("Document is null")
            return
        }

        try {
            doc.writeTo(FileOutputStream(destination.fileDescriptor))
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }

    override fun onFinish() {
        super.onFinish()
        try {
            pdfDocument?.close()
        } catch (_: Exception) {}
        pdfDocument = null
    }
}
