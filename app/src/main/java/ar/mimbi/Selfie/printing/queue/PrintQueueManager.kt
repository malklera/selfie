package ar.mimbi.Selfie.printing.queue

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
import ar.mimbi.Selfie.data.ErrorLogger
import ar.mimbi.Selfie.printing.model.*
import ar.mimbi.Selfie.printing.printer.PrinterManager
import ar.mimbi.Selfie.printing.printer.PrinterStatus
import ar.mimbi.Selfie.printing.template.DefaultTemplateRenderer
import ar.mimbi.Selfie.printing.template.DefaultTemplates
import ar.mimbi.Selfie.printing.template.TemplateRenderer
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    val allItemsFlow: Flow<List<PrintItem>> = repository.getAllItemsFlow()
    val allBatchesFlow: Flow<List<PrintBatch>> = repository.getAllBatchesFlow()
    val activeTemplateFlow: Flow<PrintTemplate> = repository.getActiveTemplateFlow()

    init {
        scope.launch {
            repository.initDefaultTemplates()
            repository.resetPrintingItemsToPending()
        }
    }

    /**
     * Enqueues photo copies assigned to the currently selected active template.
     */
    suspend fun enqueuePhoto(context: Context, photoUri: String, copies: Int): List<Long> {
        val activeTemplate = repository.getActiveTemplate()
        val activeBatch = repository.getOrCreateActiveBatch(activeTemplate)
        val insertedIds = repository.enqueuePhotos(activeBatch.id, photoUri, copies)

        Log.d(TAG, "Enqueued $copies copies for $photoUri into batch ${activeBatch.id} with template ${activeTemplate.id}")

        scope.launch {
            processQueue(context, flush = false)
        }

        return insertedIds
    }

    suspend fun setActiveTemplate(templateId: Int) {
        repository.setActiveTemplate(templateId)
    }

    suspend fun printRemaining(context: Context? = null): Boolean {
        val status = printerManager.getStatus()
        if (status !is PrinterStatus.Ready && status !is PrinterStatus.Printing) {
            Log.w(TAG, "No printer available. Enqueued jobs remain PENDING.")
            return false
        }
        processQueue(context, flush = true)
        return true
    }

    suspend fun retryFailed(context: Context? = null): Boolean {
        val status = printerManager.getStatus()
        if (status !is PrinterStatus.Ready && status !is PrinterStatus.Printing) {
            Log.w(TAG, "No printer available. Failed items remain failed.")
            return false
        }

        val failedItems = repository.getFailedItems()
        if (failedItems.isEmpty()) {
            return true
        }

        val failedIds = failedItems.map { it.id }
        repository.resetFailedItemsToPendingForIds(failedIds)
        processQueue(context, flush = true, targetItemIds = failedIds)
        return true
    }

    suspend fun clearPrintedItems() {
        repository.clearPrintedItems()
    }

    suspend fun deleteItems(itemIds: List<Long>) {
        repository.deleteItems(itemIds)
    }

    suspend fun clearQueue() {
        repository.clearQueue()
    }

    suspend fun printSelectedItems(context: Context, itemIds: List<Long>): Boolean {
        if (itemIds.isEmpty()) return false

        val selectedItems = repository.getItemsByIds(itemIds)
        if (selectedItems.isEmpty()) return false

        val template = repository.getActiveTemplate()
        val slotCount = template.slotCount.coerceAtLeast(1)
        val chunks = selectedItems.chunked(slotCount)

        val printablePages = mutableListOf<PrintablePage>()
        for (chunk in chunks) {
            val loadedBitmaps = mutableListOf<Bitmap?>()
            for (item in chunk) {
                val bitmap = decodeBitmapFromUri(context, item.photoUri, PageSize().widthPx, PageSize().heightPx)
                loadedBitmaps.add(bitmap)
            }
            val renderedPage = renderer.render(template, loadedBitmaps, PageSize())
            loadedBitmaps.forEach { it?.recycle() }
            printablePages.add(renderedPage)
        }

        val pdfDocument = PdfDocument()
        val pdfWidth = (template.widthCm / 2.54f * 72f).roundToInt().coerceAtLeast(100)
        val pdfHeight = (template.heightCm / 2.54f * 72f).roundToInt().coerceAtLeast(100)

        printablePages.forEachIndexed { index, printablePage ->
            val pageInfo = PdfDocument.PageInfo.Builder(pdfWidth, pdfHeight, index + 1).create()
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
                if (!it.isRecycled) {
                    it.recycle()
                }
            }
        }

        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            try {
                pdfDocument.close()
            } catch (_: Exception) {}
            return false
        }

        val mediaSize = if (template.widthCm > template.heightCm) {
            PrintAttributes.MediaSize.ISO_A4.asLandscape()
        } else {
            PrintAttributes.MediaSize.ISO_A4.asPortrait()
        }

        val resolutionDpi = if (template.quality.contains("300")) 300 else 200
        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(mediaSize)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setResolution(PrintAttributes.Resolution("res_id", "Default Resolution", resolutionDpi, resolutionDpi))
            .build()

        val jobName = "Impresión Selfie (${selectedItems.size} foto(s))"
        val adapter = PdfDocumentPrintAdapter(pdfDocument, printablePages.size, jobName)

        repository.updateItemStatuses(itemIds, PrintItemStatus.SENT)

        val printJob = try {
            printManager.print(jobName, adapter, printAttributes)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting print job: ${e.message}", e)
            ErrorLogger.log("Error starting print job: ${e.message}")
            repository.updateItemStatuses(itemIds, PrintItemStatus.FAILED)
            return false
        }

        if (printJob != null) {
            scope.launch {
                var waitedMs = 0
                while (waitedMs < 120000) {
                    delay(1000)
                    waitedMs += 1000
                    val info = printJob.info
                    val state = info.state
                    if (state == PrintJobInfo.STATE_COMPLETED) {
                        repository.updateItemStatuses(itemIds, PrintItemStatus.PRINTED)
                        break
                    } else if (state == PrintJobInfo.STATE_FAILED) {
                        repository.updateItemStatuses(itemIds, PrintItemStatus.FAILED)
                        break
                    } else if (state == PrintJobInfo.STATE_CANCELED) {
                        repository.updateItemStatuses(itemIds, PrintItemStatus.CANCELED)
                        break
                    }
                }
            }
        }

        return true
    }

    suspend fun processQueue(context: Context? = null, flush: Boolean = false, targetItemIds: List<Long>? = null) {
        if (printerManager.getStatus() !is PrinterStatus.Ready && printerManager.getStatus() !is PrinterStatus.Printing) {
            Log.w(TAG, "No printer available. Jobs will remain PENDING in queue.")
            return
        }

        mutex.withLock {
            try {
                val pendingBatches = repository.getAllPendingBatches()
                for (batch in pendingBatches) {
                    val template = repository.getTemplateById(batch.templateId)
                        ?: DefaultTemplates.getById(batch.templateId)

                    var pendingItems = repository.getPendingItemsForBatch(batch.id)
                    if (targetItemIds != null) {
                        pendingItems = pendingItems.filter { it.id in targetItemIds }
                    }
                    if (pendingItems.isEmpty()) continue

                    val slotCount = template.slotCount
                    val chunks = pendingItems.chunked(slotCount)

                    for (chunk in chunks) {
                        val isCompletePage = chunk.size == slotCount
                        if (!isCompletePage && !flush) {
                            // Incomplete chunk without explicit flush stays pending
                            continue
                        }

                        val chunkIds = chunk.map { it.id }
                        repository.updateItemStatuses(chunkIds, PrintItemStatus.QUEUED)

                        val loadedBitmaps = mutableListOf<Bitmap?>()
                        var loadFailed = false

                        for (item in chunk) {
                            val bitmap = if (context != null) decodeBitmapFromUri(context, item.photoUri, PageSize().widthPx, PageSize().heightPx) else null
                            if (bitmap == null) {
                                Log.e(TAG, "Failed to load image from URI: ${item.photoUri}")
                                ErrorLogger.log("Failed to load image from URI: ${item.photoUri}")
                                loadFailed = true
                                break
                            }
                            loadedBitmaps.add(bitmap)
                        }

                        if (loadFailed) {
                            loadedBitmaps.forEach { it?.recycle() }
                            repository.updateItemStatuses(chunkIds, PrintItemStatus.FAILED)
                            repository.updateBatchStatus(batch.id, BatchStatus.FAILED)
                            continue
                        }

                        try {
                            val renderedPage = renderer.render(template, loadedBitmaps, PageSize())
                            loadedBitmaps.forEach { it?.recycle() }

                            repository.updateItemStatuses(chunkIds, PrintItemStatus.PRINTING)
                            printerManager.printPage(renderedPage)
                            repository.updateItemStatuses(chunkIds, PrintItemStatus.PRINTED)

                            renderedPage.bitmap?.let {
                                if (!it.isRecycled) {
                                    it.recycle()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Print operation failed for batch ${batch.id}: ${e.message}", e)
                            ErrorLogger.log("Print operation failed: ${e.message}")
                            repository.updateItemStatuses(chunkIds, PrintItemStatus.FAILED)
                            repository.updateBatchStatus(batch.id, BatchStatus.FAILED)
                        }
                    }

                    val remainingInBatch = repository.getPendingItemsForBatch(batch.id)
                    if (remainingInBatch.isEmpty() && batch.status == BatchStatus.ACTIVE) {
                        repository.updateBatchStatus(batch.id, BatchStatus.COMPLETED)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing queue: ${e.message}", e)
                ErrorLogger.log("Error processing queue: ${e.message}")
            }
        }
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
        try {
            pdfDocument.writeTo(FileOutputStream(destination.fileDescriptor))
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            Log.e("PdfPrintAdapter", "Error writing PDF: ${e.message}", e)
            callback.onWriteFailed(e.message)
        } finally {
            try {
                pdfDocument.close()
            } catch (_: Exception) {}
        }
    }

    override fun onFinish() {
        super.onFinish()
        try {
            pdfDocument.close()
        } catch (_: Exception) {}
    }
}
