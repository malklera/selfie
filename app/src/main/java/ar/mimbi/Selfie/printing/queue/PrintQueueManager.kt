package ar.mimbi.Selfie.printing.queue

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import ar.mimbi.Selfie.data.ErrorLogger
import ar.mimbi.Selfie.printing.model.*
import ar.mimbi.Selfie.printing.printer.PrinterManager
import ar.mimbi.Selfie.printing.printer.PrinterStatus
import ar.mimbi.Selfie.printing.template.DefaultTemplateRenderer
import ar.mimbi.Selfie.printing.template.DefaultTemplates
import ar.mimbi.Selfie.printing.template.TemplateRenderer
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

    suspend fun clearQueue() {
        repository.clearQueue()
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
