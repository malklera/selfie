package ar.mimbi.Selfie.printing.queue

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import ar.mimbi.Selfie.data.ErrorLogger
import ar.mimbi.Selfie.printing.model.*
import ar.mimbi.Selfie.printing.printer.PrinterManager
import ar.mimbi.Selfie.printing.template.DefaultTemplateRenderer
import ar.mimbi.Selfie.printing.template.DefaultTemplates
import ar.mimbi.Selfie.printing.template.TemplateRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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

    suspend fun enqueuePhoto(context: Context, photoUri: String, copies: Int): List<Long> {
        val activeTemplate = repository.getActiveTemplate()
        val activeBatch = repository.getOrCreateActiveBatch(activeTemplate)
        val insertedIds = repository.enqueuePhotos(activeBatch.id, photoUri, copies)

        Log.d(TAG, "Enqueued $copies copies for $photoUri into batch ${activeBatch.id}")

        // Trigger asynchronous processing
        scope.launch {
            processQueue(context, flush = false)
        }

        return insertedIds
    }

    suspend fun setActiveTemplate(templateId: String) {
        repository.setActiveTemplate(templateId)
    }

    fun printRemaining(context: Context) {
        scope.launch {
            processQueue(context, flush = true)
        }
    }

    fun retryFailed(context: Context) {
        scope.launch {
            repository.resetFailedItemsToPending()
            processQueue(context, flush = false)
        }
    }

    suspend fun processQueue(context: Context, flush: Boolean = false) {
        mutex.withLock {
            try {
                val pendingBatches = repository.getAllPendingBatches()
                for (batch in pendingBatches) {
                    val template = repository.getTemplateById(batch.templateId)
                        ?: DefaultTemplates.getById(batch.templateId)

                    val pendingItems = repository.getPendingItemsForBatch(batch.id)
                    if (pendingItems.isEmpty()) continue

                    val slotCount = template.slotCount
                    val chunks = pendingItems.chunked(slotCount)

                    for (chunk in chunks) {
                        val isCompletePage = chunk.size == slotCount
                        if (!isCompletePage && !flush) {
                            // Incomplete chunk without flush remains pending
                            continue
                        }

                        // Process this chunk
                        val chunkIds = chunk.map { it.id }
                        repository.updateItemStatuses(chunkIds, PrintItemStatus.PRINTING)

                        val loadedBitmaps = mutableListOf<Bitmap?>()
                        var loadFailed = false

                        for (item in chunk) {
                            val bitmap = decodeBitmapFromUri(context, item.photoUri, PageSize().widthPx, PageSize().heightPx)
                            if (bitmap == null) {
                                Log.e(TAG, "Failed to load image from URI: ${item.photoUri}")
                                ErrorLogger.log("Failed to load image from URI: ${item.photoUri}")
                                loadFailed = true
                                break
                            }
                            loadedBitmaps.add(bitmap)
                        }

                        if (loadFailed) {
                            // Release any bitmaps loaded so far
                            loadedBitmaps.forEach { it?.recycle() }
                            repository.updateItemStatuses(chunkIds, PrintItemStatus.FAILED)
                            repository.updateBatchStatus(batch.id, BatchStatus.FAILED)
                            continue
                        }

                        try {
                            val renderedPage = renderer.render(template, loadedBitmaps, PageSize())

                            // Source bitmaps no longer needed after rendering
                            loadedBitmaps.forEach { it?.recycle() }

                            printerManager.printPage(renderedPage)

                            // Print successful
                            repository.updateItemStatuses(chunkIds, PrintItemStatus.PRINTED)

                            // Recycle rendered bitmap
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

                    // Check if all items in batch are completed
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
            
            // First decode with inJustDecodeBounds=true to check dimensions
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
