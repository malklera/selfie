package ar.mimbi.Selfie.printing.printer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import android.print.pdf.PrintedPdfDocument
import ar.mimbi.Selfie.printing.model.PrintItemStatus
import ar.mimbi.Selfie.printing.model.PrintablePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

/**
 * Standard Android Print Framework Printer implementation using PrintManager & PrintDocumentAdapter.
 */
class StandardAndroidPrinter(
    private val context: Context,
    private val onJobStatusChanged: ((itemStatus: PrintItemStatus, statusInSpanish: String) -> Unit)? = null
) : Printer {

    private val printManager: PrintManager =
        context.getSystemService(Context.PRINT_SERVICE) as PrintManager

    /**
     * Checks if there are installed print services (e.g., Default Print Service, Mopria, HP, etc.)
     * available on the Android system using standard PackageManager intent resolution.
     */
    fun isPrinterAvailable(): Boolean {
        return try {
            val intent = Intent("android.printservice.PrintService")
            val printServices = context.packageManager.queryIntentServices(intent, 0)
            printServices.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun connect() {
        if (!isPrinterAvailable()) {
            throw IllegalStateException("No hay servicios de impresión disponibles en Android")
        }
    }

    override suspend fun getStatus(): PrinterStatus {
        return if (isPrinterAvailable()) {
            PrinterStatus.Ready
        } else {
            PrinterStatus.Error("No hay servicios de impresión disponibles en el sistema")
        }
    }

    override suspend fun print(page: PrintablePage) {
        val bitmap = page.bitmap ?: throw IllegalArgumentException("La página no contiene imagen para imprimir")

        if (!isPrinterAvailable()) {
            onJobStatusChanged?.invoke(
                PrintItemStatus.PENDING,
                "Pendiente - Esperando impresora disponible"
            )
            throw IllegalStateException("No hay impresoras disponibles en el sistema Android")
        }

        withContext(Dispatchers.Main) {
            val jobName = "Selfie_Impresion_${System.currentTimeMillis()}"
            val adapter = PhotoPrintDocumentAdapter(context, bitmap, jobName)
            val printAttributes = PrintAttributes.Builder()
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setMediaSize(PrintAttributes.MediaSize.NA_INDEX_4X6)
                .build()

            val printJob: PrintJob = printManager.print(jobName, adapter, printAttributes)

            // Monitor state in background
            withContext(Dispatchers.IO) {
                var finished = false
                while (!finished) {
                    val state = printJob.info.state
                    val (itemStatus, statusSpanish) = mapStateToStatusAndSpanish(state)
                    onJobStatusChanged?.invoke(itemStatus, statusSpanish)

                    when (state) {
                        PrintJobInfo.STATE_COMPLETED -> {
                            finished = true
                        }
                        PrintJobInfo.STATE_FAILED -> {
                            finished = true
                            throw IllegalStateException("Error en impresión de Android: $statusSpanish")
                        }
                        PrintJobInfo.STATE_CANCELED -> {
                            finished = true
                            throw IllegalStateException("Trabajo de impresión cancelado por el usuario")
                        }
                    }

                    if (!finished) {
                        delay(1000)
                    }
                }
            }
        }
    }

    override suspend fun disconnect() {
        // No disconnect needed for standard PrintManager
    }

    companion object {
        fun mapStateToStatusAndSpanish(state: Int): Pair<PrintItemStatus, String> {
            return when (state) {
                PrintJobInfo.STATE_CREATED -> Pair(PrintItemStatus.QUEUED, "Creado - Inicializando")
                PrintJobInfo.STATE_QUEUED -> Pair(PrintItemStatus.QUEUED, "En cola de impresión")
                PrintJobInfo.STATE_STARTED -> Pair(PrintItemStatus.PRINTING, "Imprimiendo - Enviando a la impresora")
                PrintJobInfo.STATE_BLOCKED -> Pair(PrintItemStatus.BLOCKED, "Bloqueado - Verificar impresora (sin papel o pausada)")
                PrintJobInfo.STATE_COMPLETED -> Pair(PrintItemStatus.PRINTED, "Completado exitosamente")
                PrintJobInfo.STATE_FAILED -> Pair(PrintItemStatus.FAILED, "Fallido - Error en el servicio de impresión")
                PrintJobInfo.STATE_CANCELED -> Pair(PrintItemStatus.CANCELED, "Cancelado por el usuario")
                else -> Pair(PrintItemStatus.PENDING, "Pendiente - Esperando impresora disponible")
            }
        }
    }
}

class PhotoPrintDocumentAdapter(
    private val context: Context,
    private val bitmap: Bitmap,
    private val jobName: String
) : PrintDocumentAdapter() {

    private var pdfDocument: PrintedPdfDocument? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        pdfDocument = PrintedPdfDocument(context, newAttributes)
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(1)
            .build()

        callback?.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        val doc = pdfDocument ?: run {
            callback?.onWriteFailed("Documento PDF no disponible")
            return
        }

        try {
            val page = doc.startPage(0)
            if (cancellationSignal?.isCanceled == true) {
                doc.finishPage(page)
                callback?.onWriteCancelled()
                return
            }

            val canvas = page.canvas
            val destRect = RectF(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
            canvas.drawBitmap(bitmap, null, destRect, null)
            doc.finishPage(page)

            FileOutputStream(destination?.fileDescriptor).use { output ->
                doc.writeTo(output)
            }

            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed("Error al escribir el documento: ${e.message}")
        } finally {
            doc.close()
        }
    }
}
