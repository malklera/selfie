package ar.mimbi.selfie.printing.queue

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintJobInfo
import android.print.PrintManager
import android.util.Log
import ar.mimbi.selfie.data.ErrorLogger
import ar.mimbi.selfie.printing.model.*
import ar.mimbi.selfie.printing.printer.PrinterManager
import ar.mimbi.selfie.printing.template.DefaultTemplateRenderer
import ar.mimbi.selfie.printing.template.DefaultTemplates
import ar.mimbi.selfie.printing.template.TemplateRenderer
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

class PrintQueueManager(
    val repository: PrintQueueRepository,
    val printerManager: PrinterManager,
    val renderer: TemplateRenderer = DefaultTemplateRenderer(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val mutex = Mutex()

    companion object {
        private const val TAG = "PrintQueueManager"
    }

    val allPagesFlow: Flow<List<PrintPage>> = repository.getAllPagesFlow()
    val activeTemplateFlow: Flow<PrintTemplate> = repository.getActiveTemplateFlow()

    init {
        scope.launch {
            repository.resetInFlightPagesToReadyOrOpen()
        }
    }

    /**
     * Enqueues photo copies assigned to the currently selected active template.
     * Does NOT trigger automatic printing or processQueue.
     */
    suspend fun enqueuePhoto(photoUri: String, copies: Int): List<Long> = withContext(Dispatchers.IO) {
        val activeTemplate = repository.getActiveTemplate()
        val insertedIds = repository.enqueuePhotos(activeTemplate, photoUri, copies)
        Log.d(TAG, "Enqueued $copies copies for $photoUri with active template ${activeTemplate.id}")
        insertedIds
    }

    suspend fun setActiveTemplate(templateId: Int) {
        repository.setActiveTemplate(templateId)
    }

    suspend fun printReadyPages(context: Context): Boolean {
        val readyPages = repository.getAllReadyPages()
        return readyPages.isNotEmpty() && printPages(context, readyPages.map { it.id })
    }

    suspend fun retryFailedPages(context: Context): Boolean {
        val failedPages = repository.getFailedOrCanceledPages()
        if (failedPages.isEmpty()) return true

        for (page in failedPages) {
            val targetStatus = if (page.itemCount >= page.slotCount || page.wasPartial) {
                PrintPageStatus.READY
            } else {
                PrintPageStatus.OPEN
            }
            repository.updatePageStatuses(listOf(page.id), targetStatus)
        }

        val pagesToPrint = repository.getAllReadyPages()
        return pagesToPrint.isEmpty() || printPages(context, pagesToPrint.map { it.id })
    }

    suspend fun printPages(
        context: Context,
        pageIds: List<Long>,
        allowPartial: Boolean = false
    ): Boolean {
        return pageIds.isNotEmpty() && mutex.withLock {
            val pagesToProcess = repository.getPagesByIds(pageIds)
            if (pagesToProcess.isEmpty()) return@withLock false

            val eligiblePages = mutableListOf<PrintPage>()

            for (page in pagesToProcess) {
                val isEligible = when (page.status) {
                    PrintPageStatus.READY, PrintPageStatus.FAILED, PrintPageStatus.CANCELED, PrintPageStatus.SENT, PrintPageStatus.PRINTED -> true
                    PrintPageStatus.OPEN -> allowPartial && page.items.isNotEmpty()
                    else -> false
                }

                if (!isEligible) continue

                if (page.status == PrintPageStatus.OPEN) {
                    repository.markPageWasPartial(page.id)
                }

                val template = page.getTemplateSnapshot()
                if (template == null) {
                    repository.updatePageStatuses(listOf(page.id), PrintPageStatus.FAILED, "Snapshot de plantilla inválido")
                    continue
                }

                if (page.items.isEmpty()) continue

                eligiblePages.add(page)
            }

            if (eligiblePages.isEmpty()) return@withLock false

            // Group pages by compatible print attributes
            val groupedPages = eligiblePages.groupBy { page ->
                val template = page.getTemplateSnapshot()
                if (template != null) {
                    "${template.widthCm}_${template.heightCm}_${template.quality}_${template.colorMode}"
                } else ""
            }

            var anyJobStarted = false

            for ((_, groupPages) in groupedPages) {
                if (groupPages.isEmpty()) continue

                // Order deterministically: createdAt ASC, pageNumber ASC, id ASC
                val sortedPages = groupPages.sortedWith(
                    compareBy<PrintPage> { it.createdAt }
                        .thenBy { it.pageNumber }
                        .thenBy { it.id }
                )

                val pdfDocument = PdfDocument()
                val renderedPages = mutableListOf<PrintPage>()

                for (page in sortedPages) {
                    val template = page.getTemplateSnapshot() ?: continue
                    val pdfWidth = (template.widthCm / 2.54f * 72f).roundToInt().coerceAtLeast(100)
                    val pdfHeight = (template.heightCm / 2.54f * 72f).roundToInt().coerceAtLeast(100)

                    val loadedBitmaps = mutableListOf<Bitmap?>()
                    var loadFailed = false

                    val sortedItems = page.items.sortedBy { it.slotIndex }

                    for ((_, _, slotIndex, photoUri) in sortedItems) {
                        val slot = template.slots.getOrNull(slotIndex)
                        val slotWidthPx = ((slot?.width ?: 1f) * PageSize().widthPx).roundToInt().coerceAtLeast(100)
                        val slotHeightPx = ((slot?.height ?: 1f) * PageSize().heightPx).roundToInt().coerceAtLeast(100)

                        val bitmap = decodeBitmapFromUri(context,
                            photoUri, slotWidthPx, slotHeightPx)
                        if (bitmap == null) {
                            Log.e(TAG, "Failed to decode photo $photoUri for page ${page.id}")
                            ErrorLogger.log("Failed to decode photo $photoUri for page ${page.id}")
                            loadFailed = true
                            break
                        }
                        loadedBitmaps.add(bitmap)
                    }

                    if (loadFailed) {
                        loadedBitmaps.forEach { it?.recycle() }
                        repository.updatePageStatuses(listOf(page.id), PrintPageStatus.FAILED, "Error al decodificar imagen")
                        continue
                    }

                    try {
                        val printablePage = renderer.render(template, loadedBitmaps, PageSize())
                        loadedBitmaps.forEach { it?.recycle() }

                        val pageInfo = PdfDocument.PageInfo.Builder(pdfWidth, pdfHeight, renderedPages.size + 1).create()
                        val pdfPage = pdfDocument.startPage(pageInfo)
                        val canvas = pdfPage.canvas

                        printablePage.bitmap?.let { bmp ->
                            if (!bmp.isRecycled) {
                                val srcRect = Rect(0, 0, bmp.width, bmp.height)
                                val dstRect = Rect(0, 0, pdfWidth, pdfHeight)
                                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                                canvas.drawBitmap(bmp, srcRect, dstRect, paint)
                            }
                        }

                        pdfDocument.finishPage(pdfPage)
                        printablePage.bitmap?.let {
                            if (!it.isRecycled) it.recycle()
                        }

                        renderedPages.add(page)
                    } catch (e: Exception) {
                        Log.e(TAG, "Render failed for page ${page.id}: ${e.message}", e)
                        ErrorLogger.log("Render failed for page ${page.id}: ${e.message}")
                        loadedBitmaps.forEach { it?.recycle() }
                        repository.updatePageStatuses(listOf(page.id), PrintPageStatus.FAILED, "Error de renderizado: ${e.message}")
                    }
                }

                if (renderedPages.isEmpty()) {
                    try {
                        pdfDocument.close()
                    } catch (_: Exception) {}
                    continue
                }

                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager == null) {
                    try {
                        pdfDocument.close()
                    } catch (_: Exception) {}
                    repository.updatePageStatuses(renderedPages.map { it.id }, PrintPageStatus.FAILED, "Servicio de impresión de Android no disponible")
                    continue
                }

                val firstTemplate = renderedPages.first().getTemplateSnapshot() ?: DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE
                val mediaSize = if (firstTemplate.widthCm > firstTemplate.heightCm) {
                    PrintAttributes.MediaSize.ISO_A4.asLandscape()
                } else {
                    PrintAttributes.MediaSize.ISO_A4.asPortrait()
                }

                val resolutionDpi = if (firstTemplate.quality.contains("300")) 300 else 200
                val printAttributes = PrintAttributes.Builder()
                    .setMediaSize(mediaSize)
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .setResolution(PrintAttributes.Resolution("res_id", "Default Resolution", resolutionDpi, resolutionDpi))
                    .build()

                val jobName = "Impresión Selfie (${renderedPages.size} página(s))"
                val adapter = PdfDocumentPrintAdapter(pdfDocument, renderedPages.size, jobName)

                val renderedPageIds = renderedPages.map { it.id }
                repository.updatePageSentJob(renderedPageIds, null)

                val printJob = try {
                    printManager.print(jobName, adapter, printAttributes)
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting print job: ${e.message}", e)
                    ErrorLogger.log("Error starting print job: ${e.message}")
                    repository.updatePageStatuses(renderedPageIds, PrintPageStatus.FAILED, "Error al iniciar impresión: ${e.message}")
                    try {
                        pdfDocument.close()
                    } catch (_: Exception) {}
                    null
                }

                if (printJob != null) {
                    anyJobStarted = true
                    val jobIdString = printJob.id.toString()
                    repository.updatePageSentJob(renderedPageIds, jobIdString)

                    scope.launch {
                        var waitedMs = 0
                        while (waitedMs < 300000) {
                            delay(1000.milliseconds)
                            waitedMs += 1000
                            val info = printJob.info
                            val state = info.state
                            if (state == PrintJobInfo.STATE_COMPLETED) {
                                repository.updatePagesPrinted(renderedPageIds)
                                break
                            } else if (state == PrintJobInfo.STATE_FAILED) {
                                repository.updatePageStatuses(renderedPageIds, PrintPageStatus.FAILED, "Impresión fallida en Android")
                                break
                            } else if (state == PrintJobInfo.STATE_CANCELED) {
                                repository.updatePageStatuses(renderedPageIds, PrintPageStatus.CANCELED, "Trabajo cancelado por el usuario")
                                break
                            }
                        }
                    }
                }
            }

            return@withLock anyJobStarted
        }
    }

    suspend fun clearPrintedItems() {
        repository.clearPrintedPages()
    }

    suspend fun deletePages(pageIds: List<Long>) {
        repository.deletePages(pageIds)
    }

    suspend fun clearQueue() {
        repository.clearQueue()
    }

    private fun decodeBitmapFromUri(context: Context, uriString: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false

            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding bitmap from $uriString: ${e.message}")
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}

private class PdfDocumentPrintAdapter(
    private val pdfDocument: PdfDocument,
    private val pageCount: Int,
    private val documentName: String
) : PrintDocumentAdapter() {

    private var isClosed = false

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

        val info = PrintDocumentInfo.Builder(documentName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount)
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

        try {
            pdfDocument.writeTo(FileOutputStream(destination.fileDescriptor))
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            Log.e("PdfPrintAdapter", "Error writing PDF: ${e.message}", e)
            callback.onWriteFailed(e.message)
        }
    }

    override fun onFinish() {
        super.onFinish()
        closeDocument()
    }

    private fun closeDocument() {
        if (!isClosed) {
            isClosed = true
            try {
                pdfDocument.close()
            } catch (e: Exception) {
                Log.e("PdfPrintAdapter", "Error closing PdfDocument: ${e.message}")
            }
        }
    }
}
