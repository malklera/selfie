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
import ar.mimbi.Selfie.printing.printer.StandardAndroidPrinter
import ar.mimbi.Selfie.printing.printer.WifiPrinter
import ar.mimbi.Selfie.printing.printer.WifiPrinterDiscovery
import ar.mimbi.Selfie.printing.printer.WifiPrinterInfo
import ar.mimbi.Selfie.printing.template.DefaultTemplateRenderer
import ar.mimbi.Selfie.printing.template.DefaultTemplates
import ar.mimbi.Selfie.printing.template.TemplateRenderer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

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

    suspend fun ensurePrinterReady(context: Context?) {
        val currentStatus = printerManager.getStatus()
        if (currentStatus is PrinterStatus.Ready) return
        if (context == null) return

        // If no printer is configured, do not auto-configure any printer
        val savedConfig = repository.getSavedPrinterConfig()
        if (savedConfig == null && !printerManager.hasConfiguredPrinter()) {
            return
        }

        // 1. Try reconnecting current active printer
        try {
            printerManager.connect()
            if (printerManager.getStatus() is PrinterStatus.Ready) return
        } catch (_: Exception) {}

        // 2. If configured as WIFI, try reconnecting or discovering saved printer
        if (savedConfig != null && savedConfig.printerType == "WIFI") {
            try {
                val json = JSONObject(savedConfig.settingsJson)
                val ip = json.optString("ipAddress")
                val port = json.optInt("port", 9100)
                val name = json.optString("printerName", savedConfig.selectedPrinterId.ifBlank { "Impresora Wi-Fi" })
                if (ip.isNotBlank()) {
                    val wifiPrinter = WifiPrinter(ip, port, timeoutMs = 2000)
                    try {
                        wifiPrinter.connect()
                        printerManager.setPrinter(wifiPrinter, name, printerType = "WIFI", ipAddress = ip, port = port, saveToDb = false)
                        if (printerManager.getStatus() is PrinterStatus.Ready) return
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            // Search for target printer using discovery
            val discovery = WifiPrinterDiscovery(context)
            val discovered = suspendCancellableCoroutine<List<WifiPrinterInfo>> { continuation ->
                var resumed = false
                discovery.startDiscovery()
                CoroutineScope(Dispatchers.IO).launch {
                    delay(2000)
                    discovery.stopDiscovery()
                    if (!resumed) {
                        resumed = true
                        continuation.resume(discovery.discoveredPrinters.value) {}
                    }
                }
            }

            val target = discovered.find { it.ipAddress == JSONObject(savedConfig.settingsJson).optString("ipAddress") }
                ?: discovered.firstOrNull()

            if (target != null) {
                val wifiPrinter = WifiPrinter(target.ipAddress, target.port)
                try {
                    wifiPrinter.connect()
                    printerManager.setPrinter(wifiPrinter, "${target.name} (${target.ipAddress})", printerType = "WIFI", ipAddress = target.ipAddress, port = target.port)
                } catch (_: Exception) {}
            }
        } else if (savedConfig != null && savedConfig.printerType == "SYSTEM_ANDROID") {
            val json = runCatching { JSONObject(savedConfig.settingsJson) }.getOrNull()
            if (json?.optBoolean("userConfigured", false) == true) {
                val standardPrinter = StandardAndroidPrinter(context)
                if (standardPrinter.isPrinterAvailable()) {
                    printerManager.setPrinter(
                        printer = standardPrinter,
                        printerName = savedConfig.selectedPrinterId.ifBlank { "Servicio de Impresión Android (Standard)" },
                        printerType = "SYSTEM_ANDROID",
                        saveToDb = false
                    )
                    printerManager.connect()
                }
            }
        }
    }

    suspend fun enqueuePhoto(context: Context, photoUri: String, copies: Int): List<Long> {
        ensurePrinterReady(context)

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

    suspend fun setActiveTemplate(templateId: Int) {
        repository.setActiveTemplate(templateId)
    }

    suspend fun printRemaining(context: Context? = null): Boolean {
        ensurePrinterReady(context)
        val status = printerManager.getStatus()
        if (status !is PrinterStatus.Ready && status !is PrinterStatus.Printing) {
            Log.w(TAG, "No hay impresora disponible. El trabajo permanecerá PENDIENTE.")
            return false
        }
        processQueue(context, flush = true)
        return true
    }

    suspend fun retryFailed(context: Context? = null): Boolean {
        ensurePrinterReady(context)
        val status = printerManager.getStatus()
        if (status !is PrinterStatus.Ready && status !is PrinterStatus.Printing) {
            Log.w(TAG, "No hay impresora disponible. El trabajo permanecerá en estado fallido.")
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
        ensurePrinterReady(context)

        // Rule: Only send to print if a printer is available, otherwise keep pending
        if (printerManager.getStatus() !is PrinterStatus.Ready && printerManager.getStatus() !is PrinterStatus.Printing) {
            Log.w(TAG, "No hay impresora disponible. El trabajo permanecerá PENDIENTE.")
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
                            // Incomplete chunk without flush remains pending
                            continue
                        }

                        // Process this chunk
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

                            // If using StandardAndroidPrinter, attach status listener to update DB state in real-time
                            val currentPrinter = printerManager.getActivePrinter()
                            if (currentPrinter is StandardAndroidPrinter) {
                                currentPrinter.onJobStatusChanged = { itemStatus, _ ->
                                    scope.launch {
                                        repository.updateItemStatuses(chunkIds, itemStatus)
                                    }
                                }
                            }

                            repository.updateItemStatuses(chunkIds, PrintItemStatus.PRINTING)
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
