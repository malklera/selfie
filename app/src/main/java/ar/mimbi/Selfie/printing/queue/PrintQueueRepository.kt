package ar.mimbi.Selfie.printing.queue

import ar.mimbi.Selfie.data.db.AppDatabase
import ar.mimbi.Selfie.printing.model.*
import ar.mimbi.Selfie.printing.persistence.*
import ar.mimbi.Selfie.printing.template.DefaultTemplates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class PrintQueueRepository(private val database: AppDatabase) {

    private val templateDao = database.printTemplateDao()
    private val batchDao = database.printBatchDao()
    private val itemDao = database.printItemDao()
    private val configDao = database.printerConfigDao()

    suspend fun initDefaultTemplates() {
        DefaultTemplates.ALL_TEMPLATES.forEach { template ->
            val existing = templateDao.getTemplateById(template.id)
            if (existing == null) {
                val slotsJson = JSONArray().apply {
                    template.slots.forEach { slot ->
                        put(JSONObject().apply {
                            put("left", slot.left.toDouble())
                            put("top", slot.top.toDouble())
                            put("width", slot.width.toDouble())
                            put("height", slot.height.toDouble())
                        })
                    }
                }.toString()

                val isActive = template.id == DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE.id
                templateDao.insertOrUpdate(
                    PrintTemplateEntity(
                        id = template.id,
                        description = template.description,
                        slotsJson = slotsJson,
                        version = template.version,
                        isActive = isActive,
                        quality = template.quality,
                        widthCm = template.widthCm,
                        heightCm = template.heightCm,
                        marginMm = template.marginMm,
                        isFullWidth = template.isFullWidth
                    )
                )
            }
        }
    }

    suspend fun getActiveTemplate(): PrintTemplate {
        initDefaultTemplates()
        val activeEntity = templateDao.getActiveTemplate()
            ?: return DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE

        return parseTemplateEntity(activeEntity)
    }

    suspend fun setActiveTemplate(templateId: Int) {
        templateDao.setActiveTemplate(templateId)
    }

    fun getActiveTemplateFlow(): Flow<PrintTemplate> {
        return templateDao.getActiveTemplateFlow().map { entity ->
            if (entity == null) DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE else parseTemplateEntity(entity)
        }
    }

    suspend fun getTemplateById(id: Int): PrintTemplate? {
        val entity = templateDao.getTemplateById(id) ?: return null
        return parseTemplateEntity(entity)
    }

    suspend fun getOrCreateActiveBatch(template: PrintTemplate): PrintBatch {
        val activeEntity = batchDao.getActiveBatch(BatchStatus.ACTIVE.name)
        if (activeEntity != null && activeEntity.templateId == template.id && activeEntity.templateVersion == template.version) {
            return PrintBatch(
                id = activeEntity.id,
                templateId = activeEntity.templateId,
                templateVersion = activeEntity.templateVersion,
                createdAt = activeEntity.createdAt,
                status = BatchStatus.valueOf(activeEntity.status)
            )
        }

        // If an existing active batch uses a different template/version, finalize it
        if (activeEntity != null) {
            batchDao.updateBatchStatus(activeEntity.id, BatchStatus.COMPLETED.name)
        }

        val newBatchEntity = PrintBatchEntity(
            templateId = template.id,
            templateVersion = template.version,
            createdAt = System.currentTimeMillis(),
            status = BatchStatus.ACTIVE.name
        )
        val newId = batchDao.insertBatch(newBatchEntity)
        return PrintBatch(
            id = newId,
            templateId = template.id,
            templateVersion = template.version,
            createdAt = newBatchEntity.createdAt,
            status = BatchStatus.ACTIVE
        )
    }

    suspend fun enqueuePhotos(batchId: Long, photoUri: String, copies: Int): List<Long> {
        val lastItem = itemDao.getLastItem()
        var currentSeq = (lastItem?.sequence ?: 0) + 1

        val entities = mutableListOf<PrintItemEntity>()
        repeat(copies) {
            entities.add(
                PrintItemEntity(
                    batchId = batchId,
                    photoUri = photoUri,
                    sequence = currentSeq++,
                    status = PrintItemStatus.PENDING.name
                )
            )
        }
        return itemDao.insertItems(entities)
    }

    suspend fun getPendingItemsForBatch(batchId: Long): List<PrintItem> {
        return itemDao.getItemsByBatchAndStatus(batchId, PrintItemStatus.PENDING.name).map {
            PrintItem(
                id = it.id,
                batchId = it.batchId,
                photoUri = it.photoUri,
                sequence = it.sequence,
                status = PrintItemStatus.valueOf(it.status)
            )
        }
    }

    suspend fun getAllPendingBatches(): List<PrintBatch> {
        val allPendingItems = itemDao.getAllPendingItems()
        val batchIds = allPendingItems.map { it.batchId }.distinct()
        return batchIds.mapNotNull { id ->
            val entity = batchDao.getBatchById(id)
            if (entity != null) {
                PrintBatch(
                    id = entity.id,
                    templateId = entity.templateId,
                    templateVersion = entity.templateVersion,
                    createdAt = entity.createdAt,
                    status = BatchStatus.valueOf(entity.status)
                )
            } else null
        }
    }

    suspend fun updateItemStatuses(ids: List<Long>, status: PrintItemStatus) {
        itemDao.updateItemStatuses(ids, status.name)
    }

    suspend fun updateBatchStatus(batchId: Long, status: BatchStatus) {
        batchDao.updateBatchStatus(batchId, status.name)
    }

    suspend fun resetPrintingItemsToPending() {
        itemDao.resetPrintingToPending()
    }

    suspend fun resetFailedItemsToPending() {
        itemDao.resetFailedToPending()
    }

    suspend fun clearPrintedItems() {
        itemDao.deletePrintedItems()
    }

    fun getAllItemsFlow(): Flow<List<PrintItem>> {
        return itemDao.getAllItemsFlow().map { list ->
            list.map {
                PrintItem(
                    id = it.id,
                    batchId = it.batchId,
                    photoUri = it.photoUri,
                    sequence = it.sequence,
                    status = runCatching { PrintItemStatus.valueOf(it.status) }.getOrDefault(PrintItemStatus.PENDING)
                )
            }
        }
    }

    fun getAllBatchesFlow(): Flow<List<PrintBatch>> {
        return batchDao.getAllBatchesFlow().map { list ->
            list.map {
                PrintBatch(
                    id = it.id,
                    templateId = it.templateId,
                    templateVersion = it.templateVersion,
                    createdAt = it.createdAt,
                    status = BatchStatus.valueOf(it.status)
                )
            }
        }
    }

    suspend fun savePrinterConfig(printerType: String, printerName: String, settingsJson: String) {
        configDao.insertOrUpdate(
            PrinterConfigEntity(
                id = "default",
                selectedPrinterId = printerName,
                printerType = printerType,
                settingsJson = settingsJson
            )
        )
    }

    suspend fun getSavedPrinterConfig(): PrinterConfigEntity? {
        return configDao.getConfig("default")
    }

    private fun parseTemplateEntity(entity: PrintTemplateEntity): PrintTemplate {
        val slots = mutableListOf<PhotoSlot>()
        val jsonArray = JSONArray(entity.slotsJson)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            slots.add(
                PhotoSlot(
                    left = obj.getDouble("left").toFloat(),
                    top = obj.getDouble("top").toFloat(),
                    width = obj.getDouble("width").toFloat(),
                    height = obj.getDouble("height").toFloat()
                )
            )
        }
        return PrintTemplate(
            id = entity.id,
            description = entity.description,
            slots = slots,
            version = entity.version,
            quality = entity.quality,
            widthCm = entity.widthCm,
            heightCm = entity.heightCm,
            marginMm = entity.marginMm,
            isFullWidth = entity.isFullWidth
        )
    }
}
