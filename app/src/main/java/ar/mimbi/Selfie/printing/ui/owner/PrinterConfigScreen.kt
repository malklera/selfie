package ar.mimbi.Selfie.printing.ui.owner

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintJobInfo
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
import ar.mimbi.Selfie.data.UserActionTracker
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PrinterInfoResult(
    val isAvailable: Boolean,
    val enabledServices: List<String> = emptyList(),
    val activePrinters: List<String> = emptyList()
)

private fun formatPrinterName(rawId: String): String {
    var name = rawId
    if (name.contains("#d#")) {
        name = name.substringAfterLast("#d#")
    } else if (name.contains("#")) {
        name = name.substringAfterLast("#")
    }
    if (name.contains("/")) {
        name = name.substringAfterLast("/")
    }
    name = name.trim('?', ' ', ':', '\t', '\n')
    return if (name.isNotBlank()) name else rawId
}

private fun queryPrinterAndServiceInfo(context: Context): PrinterInfoResult {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        ?: return PrinterInfoResult(isAvailable = false)

    val enabledServices = mutableSetOf<String>()

    // Method 1: Try PrintManager.getPrintServices via reflection
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        try {
            val method = printManager.javaClass.methods.firstOrNull {
                it.name == "getPrintServices" && it.parameterTypes.size == 1
            }
            if (method != null) {
                val services = (method.invoke(printManager, 1 /* ENABLED_SERVICES */) as? List<*>)
                    ?: (method.invoke(printManager, 3 /* ALL_SERVICES */) as? List<*>)

                services?.forEach { service ->
                    if (service != null) {
                        var resolveInfo: ResolveInfo? = null
                        try {
                            val getResolveInfoMethod = service.javaClass.methods.firstOrNull { it.name == "getResolveInfo" }
                            resolveInfo = getResolveInfoMethod?.invoke(service) as? ResolveInfo
                        } catch (_: Exception) {}

                        if (resolveInfo == null) {
                            try {
                                val field = service.javaClass.fields.firstOrNull { it.name == "resolveInfo" }
                                resolveInfo = field?.get(service) as? ResolveInfo
                            } catch (_: Exception) {}
                        }

                        val label = resolveInfo?.loadLabel(context.packageManager)?.toString()
                        if (!label.isNullOrBlank()) {
                            enabledServices.add(label)
                        } else {
                            try {
                                val getIdMethod = service.javaClass.methods.firstOrNull { it.name == "getId" }
                                val id = getIdMethod?.invoke(service) as? String
                                if (!id.isNullOrBlank()) {
                                    val formattedId = id.substringBefore("/").substringAfterLast(".")
                                    enabledServices.add(formattedId)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PrinterConfigScreen", "Error querying getPrintServices", e)
        }
    }

    // Method 2: Query PackageManager for installed/enabled Print Services
    try {
        val printServiceIntent = Intent("android.printservice.PrintService")
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentServices(
                printServiceIntent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentServices(printServiceIntent, 0)
        }

        val enabledSetting = try {
            Settings.Secure.getString(context.contentResolver, "enabled_print_services") ?: ""
        } catch (_: Exception) { "" }

        resolveInfos.forEach { info ->
            val pkg = info.serviceInfo.packageName
            val componentName = "$pkg/${info.serviceInfo.name}"
            val isEnabled = enabledSetting.contains(pkg) || enabledSetting.contains(componentName) || enabledSetting.isBlank()

            if (isEnabled) {
                val label = info.loadLabel(context.packageManager).toString()
                if (label.isNotBlank()) {
                    enabledServices.add(label)
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("PrinterConfigScreen", "Error querying intent services", e)
    }

    val activePrintJobs = printManager.printJobs.filter { job ->
        val state = job.info.state
        state == PrintJobInfo.STATE_QUEUED ||
                state == PrintJobInfo.STATE_STARTED ||
                state == PrintJobInfo.STATE_BLOCKED
    }

    val activePrinters = activePrintJobs.mapNotNull { job ->
        job.info.printerId?.localId?.takeIf { it.isNotBlank() }?.let { formatPrinterName(it) }
    }.distinct()

    return PrinterInfoResult(
        isAvailable = true,
        enabledServices = enabledServices.toList(),
        activePrinters = activePrinters
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterConfigScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()

    var printerInfo by remember { mutableStateOf(queryPrinterAndServiceInfo(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                printerInfo = queryPrinterAndServiceInfo(context)
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
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (printerInfo.activePrinters.isNotEmpty()) {
                                Text(
                                    text = "Impresora(s) activa(s) conectada(s):",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
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
                                    color = MaterialTheme.colorScheme.primary
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

        pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 size in points
        val page = pdfDocument?.startPage(pageInfo)

        page?.let { p ->
            val canvas: Canvas = p.canvas
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

            pdfDocument?.finishPage(p)
        }

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
        try {
            pdfDocument?.writeTo(FileOutputStream(destination.fileDescriptor))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
            return
        } finally {
            pdfDocument?.close()
            pdfDocument = null
        }

        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    }
}
