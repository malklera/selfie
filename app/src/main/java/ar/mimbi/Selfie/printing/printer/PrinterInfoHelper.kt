package ar.mimbi.Selfie.printing.printer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintJobInfo
import android.print.PrintManager
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ConnectedPrinterDetails(
    val isAvailable: Boolean,
    val enabledServices: List<String> = emptyList(),
    val activePrinters: List<String> = emptyList(),
    val activeJobsInfo: List<PrintJobSummary> = emptyList()
)

data class PrintJobSummary(
    val jobName: String,
    val printerName: String,
    val stateText: String,
    val copies: Int,
    val mediaSizeText: String?,
    val resolutionText: String?
)

data class DetailedJobInfo(
    val jobId: String,
    val label: String,
    val stateName: String,
    val stateCode: Int,
    val printerIdRaw: String,
    val printerNameFormatted: String,
    val copies: Int,
    val mediaSizeLabel: String?,
    val mediaWidthMm: Float?,
    val mediaHeightMm: Float?,
    val resolutionDpi: String?,
    val colorMode: String,
    val duplexMode: String,
    val minMarginsMils: String?
)

data class FullPrinterQueryResult(
    val timestamp: String,
    val isFrameworkAvailable: Boolean,
    val enabledServicesCount: Int,
    val enabledServices: List<String>,
    val totalJobsInQueue: Int,
    val activePrinters: List<String>,
    val jobsDetails: List<DetailedJobInfo>,
    val appPrinterStatus: String,
    val appPrinterName: String?,
    val rawFormattedOutput: String,
    val rawJsonOutput: String
)

object PrinterInfoHelper {
    fun formatPrinterName(rawId: String): String {
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

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
    }

    fun queryPrinterDetails(context: Context): ConnectedPrinterDetails {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return ConnectedPrinterDetails(isAvailable = false)

        val enabledServices = getEnabledPrintServices(context, printManager)
        val printJobs = try { printManager.printJobs } catch (_: Exception) { emptyList() }

        val activeJobs = printJobs.filter { job ->
            val state = job.info.state
            state == PrintJobInfo.STATE_QUEUED ||
                    state == PrintJobInfo.STATE_STARTED ||
                    state == PrintJobInfo.STATE_BLOCKED
        }

        val activePrinters = activeJobs.mapNotNull { job ->
            job.info.printerId?.localId?.takeIf { it.isNotBlank() }?.let { formatPrinterName(it) }
        }.distinct()

        val jobSummaries = activeJobs.map { job ->
            val info = job.info
            val stateStr = mapJobStateToString(info.state)
            val pName = info.printerId?.localId?.let { formatPrinterName(it) } ?: "Impresora del sistema"
            val mediaSize = info.attributes.mediaSize?.getLabel(context.packageManager)
            val res = info.attributes.resolution?.let { "${it.horizontalDpi}x${it.verticalDpi} dpi" }

            PrintJobSummary(
                jobName = info.label ?: "Trabajo de impresión",
                printerName = pName,
                stateText = stateStr,
                copies = info.copies,
                mediaSizeText = mediaSize,
                resolutionText = res
            )
        }

        return ConnectedPrinterDetails(
            isAvailable = true,
            enabledServices = enabledServices,
            activePrinters = activePrinters,
            activeJobsInfo = jobSummaries
        )
    }

    fun queryFullPrinterDetails(
        context: Context,
        printerManager: PrinterManager? = null
    ): FullPrinterQueryResult {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())

        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            val emptyFormatted = """
Fecha y Hora: $timestamp
Estado del Servicio: NO DISPONIBLE (PrintManager es nulo)
""".trimIndent()
            val emptyJson = """{"timestamp":"$timestamp","error":"PrintManager not available"}"""
            return FullPrinterQueryResult(
                timestamp = timestamp,
                isFrameworkAvailable = false,
                enabledServicesCount = 0,
                enabledServices = emptyList(),
                totalJobsInQueue = 0,
                activePrinters = emptyList(),
                jobsDetails = emptyList(),
                appPrinterStatus = "Disconnected",
                appPrinterName = null,
                rawFormattedOutput = emptyFormatted,
                rawJsonOutput = emptyJson
            )
        }

        val enabledServices = getEnabledPrintServices(context, printManager)
        val printJobs = try { printManager.printJobs } catch (_: Exception) { emptyList() }

        val detailedJobs = printJobs.mapIndexed { index, job ->
            val info = job.info
            val pRaw = info.printerId?.localId ?: "N/A"
            val pFormatted = formatPrinterName(pRaw)
            val mediaSize = info.attributes.mediaSize
            val mediaLabel = mediaSize?.getLabel(context.packageManager)
            val widthMm = mediaSize?.let { it.widthMils * 0.0254f }
            val heightMm = mediaSize?.let { it.heightMils * 0.0254f }
            val res = info.attributes.resolution?.let { "${it.horizontalDpi}x${it.verticalDpi} dpi" }

            val colorModeStr = when (info.attributes.colorMode) {
                PrintAttributes.COLOR_MODE_COLOR -> "COLOR (2)"
                PrintAttributes.COLOR_MODE_MONOCHROME -> "MONOCHROME (1)"
                else -> "DESCONOCIDO (${info.attributes.colorMode})"
            }

            val duplexModeStr = when (info.attributes.duplexMode) {
                PrintAttributes.DUPLEX_MODE_NONE -> "NINGUNO (1)"
                PrintAttributes.DUPLEX_MODE_LONG_EDGE -> "BORDE LARGO (2)"
                PrintAttributes.DUPLEX_MODE_SHORT_EDGE -> "BORDE CORTO (4)"
                else -> "DESCONOCIDO (${info.attributes.duplexMode})"
            }

            val margins = info.attributes.minMargins
            val marginsStr = margins?.let { "L:${it.leftMils} T:${it.topMils} R:${it.rightMils} B:${it.bottomMils} mils" } ?: "N/A"

            DetailedJobInfo(
                jobId = "Job#${index + 1}",
                label = info.label ?: "Trabajo $index",
                stateName = mapJobStateToString(info.state),
                stateCode = info.state,
                printerIdRaw = pRaw,
                printerNameFormatted = pFormatted,
                copies = info.copies,
                mediaSizeLabel = mediaLabel,
                mediaWidthMm = widthMm,
                mediaHeightMm = heightMm,
                resolutionDpi = res,
                colorMode = colorModeStr,
                duplexMode = duplexModeStr,
                minMarginsMils = marginsStr
            )
        }

        val activePrinters = detailedJobs.map { it.printerNameFormatted }.distinct()
        val appPrinterStatus = printerManager?.statusFlow?.value?.let { st ->
            st.javaClass.simpleName ?: st.toString()
        } ?: "Desconectada / No configurada"

        val appPrinterName = printerManager?.currentPrinterName?.value ?: printerManager?.getActivePrinter()?.let { "Impresora Activa" }

        val sb = StringBuilder()
        sb.appendLine("Fecha y Hora: $timestamp")
        sb.appendLine()

        sb.appendLine("Servicios de impresión activos (${enabledServices.size}):")
        if (enabledServices.isEmpty()) {
            sb.appendLine("  - Ningún servicio de impresión activo reportado por el sistema.")
        } else {
            enabledServices.forEach { service ->
                sb.appendLine("  - $service")
            }
        }
        sb.appendLine()

        if (activePrinters.isEmpty()) {
            sb.appendLine("  - Impresoras activas detectadas: Ninguna en cola actualmente")
        } else {
            sb.appendLine("  - Impresoras detectadas: ${activePrinters.joinToString(", ")}")
        }
        sb.appendLine("  - Total de trabajos en el sistema: ${printJobs.size}")
        sb.appendLine()

        if (detailedJobs.isEmpty()) {
            sb.appendLine("  (No hay trabajos de impresión activos ni recientes en la cola del sistema)")
            sb.appendLine("  Nota: Para consultar atributos dinámicos de hardware en tiempo real, inicie un trabajo de impresión o pulse 'Imprimir Página de Prueba'.")
        } else {
            detailedJobs.forEach { job ->
                sb.appendLine("  * TRABAJO: ${job.label}")
                sb.appendLine("    - Estado: ${job.stateName} (Código: ${job.stateCode})")
                sb.appendLine("    - ID Impresora: ${job.printerIdRaw}")
                sb.appendLine("    - Nombre Impresora: ${job.printerNameFormatted}")
                sb.appendLine("    - Copias: ${job.copies}")
                sb.appendLine("    - Tamaño de Papel: ${job.mediaSizeLabel ?: "Predeterminado"} (${job.mediaWidthMm?.let { "%.1f".format(it) } ?: "?"} x ${job.mediaHeightMm?.let { "%.1f".format(it) } ?: "?"} mm)")
                sb.appendLine("    - Resolución DPI: ${job.resolutionDpi ?: "Predeterminada"}")
                sb.appendLine("    - Modo de Color: ${job.colorMode}")
                sb.appendLine("    - Modo Dúplex: ${job.duplexMode}")
                sb.appendLine("    - Márgenes Mínimos: ${job.minMarginsMils}")
                sb.appendLine()
            }
        }

        sb.appendLine("  - Impresora Seleccionada: ${appPrinterName ?: "Ninguna asignada explícitamente"}")
        sb.appendLine("  - Estado Interno: $appPrinterStatus")

        val formattedText = sb.toString()

        val servicesJson = enabledServices.joinToString(",", "[", "]") { "\"$it\"" }
        val printersJson = activePrinters.joinToString(",", "[", "]") { "\"$it\"" }
        val jobsJson = detailedJobs.joinToString(",", "[", "]") { job ->
            """{"jobId":"${job.jobId}","label":"${job.label}","state":"${job.stateName}","printerId":"${job.printerIdRaw}","copies":${job.copies},"mediaSize":"${job.mediaSizeLabel ?: "Unknown"}","resolution":"${job.resolutionDpi ?: "Unknown"}","colorMode":"${job.colorMode}","duplexMode":"${job.duplexMode}"}"""
        }

        val jsonText = """
{
  "timestamp": "$timestamp",
  "isFrameworkAvailable": true,
  "enabledServicesCount": ${enabledServices.size},
  "enabledServices": $servicesJson,
  "activePrinters": $printersJson,
  "totalJobsInQueue": ${printJobs.size},
  "jobs": $jobsJson,
  "appPrinter": {
    "name": ${appPrinterName?.let { "\"$it\"" } ?: "null"},
    "status": "$appPrinterStatus"
  }
}
""".trimIndent()

        return FullPrinterQueryResult(
            timestamp = timestamp,
            isFrameworkAvailable = true,
            enabledServicesCount = enabledServices.size,
            enabledServices = enabledServices,
            totalJobsInQueue = printJobs.size,
            activePrinters = activePrinters,
            jobsDetails = detailedJobs,
            appPrinterStatus = appPrinterStatus,
            appPrinterName = appPrinterName,
            rawFormattedOutput = formattedText,
            rawJsonOutput = jsonText
        )
    }

    private fun getEnabledPrintServices(context: Context, printManager: PrintManager): List<String> {
        val enabledServices = mutableSetOf<String>()

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
                android.util.Log.e("PrinterInfoHelper", "Error querying getPrintServices", e)
            }
        }

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
            android.util.Log.e("PrinterInfoHelper", "Error querying intent services", e)
        }

        return enabledServices.toList()
    }

    private fun mapJobStateToString(state: Int): String {
        return when (state) {
            PrintJobInfo.STATE_QUEUED -> "EN COLA (STATE_QUEUED)"
            PrintJobInfo.STATE_STARTED -> "IMPRIMIENDO (STATE_STARTED)"
            PrintJobInfo.STATE_BLOCKED -> "BLOQUEADO (STATE_BLOCKED)"
            PrintJobInfo.STATE_COMPLETED -> "COMPLETADO (STATE_COMPLETED)"
            PrintJobInfo.STATE_FAILED -> "ERROR (STATE_FAILED)"
            PrintJobInfo.STATE_CANCELED -> "CANCELADO (STATE_CANCELED)"
            else -> "DESCONOCIDO ($state)"
        }
    }
}
