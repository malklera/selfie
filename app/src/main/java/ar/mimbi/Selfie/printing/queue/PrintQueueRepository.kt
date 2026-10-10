package ar.mimbi.Selfie.printing.queue

import ar.mimbi.Selfie.data.db.AppDatabase
import ar.mimbi.Selfie.printing.model.*
import ar.mimbi.Selfie.printing.persistence.*
import ar.mimbi.Selfie.printing.template.DefaultTemplates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

open class PrintQueueRepository(private val database: AppDatabase? = null) {

    private val templateDao get() = database?.printTemplateDao()
    private val batchDao get() = database?.printBatchDao()
    private val itemDao get() = database?.printItemDao()
    private val configDao get() = database?.printerConfigDao()

    open suspend fun initDefaultTemplates() {
        val dao = templateDao ?: return
        DefaultTemplates.ALL_TEMPLATES.forEach { template ->
            val existing = dao.getTemplateById(template.id)
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
                dao.insertOrUpdate(
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

    open suspend fun getActiveTemplate(): PrintTemplate {
        initDefaultTemplates()
        val dao = templateDao ?: return DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE
        val activeEntity = dao.getActiveTemplate()
            ?: return DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE

        return parseTemplateEntity(activeEntity)
    }

    open suspend fun setActiveTemplate(templateId: Int) {
        templateDao?.setActiveTemplate(templateId)
    }

    open fun getActiveTemplateFlow(): Flow<PrintTemplate> {
        val dao = templateDao ?: return kotlinx.coroutines.flow.flowOf(DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE)
        return dao.getActiveTemplateFlow().map { entity ->
            if (entity == null) DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE else parseTemplateEntity(entity)
        }
    }

    open suspend fun getTemplateById(id: Int): PrintTemplate? {
        val entity = templateDao?.getTemplateById(id) ?: return null
        return parseTemplateEntity(entity)
    }

    open suspend fun getOrCreateActiveBatch(template: PrintTemplate): PrintBatch {
        val dao = batchDao ?: return PrintBatch(0, template.id, template.version, System.currentTimeMillis(), BatchStatus.ACTIVE)
        val activeEntity = dao.getActiveBatch(BatchStatus.ACTIVE.name)
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
            dao.updateBatchStatus(activeEntity.id, BatchStatus.COMPLETED.name)
        }

        val newBatchEntity = PrintBatchEntity(
            templateId = template.id,
            templateVersion = template.version,
            createdAt = System.currentTimeMillis(),
            status = BatchStatus.ACTIVE.name
        )
        val newId = dao.insertBatch(newBatchEntity)
        return PrintBatch(
            id = newId,
            templateId = template.id,
            templateVersion = template.version,
            createdAt = newBatchEntity.createdAt,
            status = BatchStatus.ACTIVE
        )
    }

    open suspend fun enqueuePhotos(batchId: Long, photoUri: String, copies: Int): List<Long> {
        val dao = itemDao ?: return emptyList()
        val lastItem = dao.getLastItem()
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
        return dao.insertItems(entities)
    }

    open suspend fun getPendingItemsForBatch(batchId: Long): List<PrintItem> {
        return itemDao?.getItemsByBatchAndStatus(batchId, PrintItemStatus.PENDING.name)?.map {
            PrintItem(
                id = it.id,
                batchId = it.batchId,
                photoUri = it.photoUri,
                sequence = it.sequence,
                status = PrintItemStatus.valueOf(it.status)
            )
        } ?: emptyList()
    }

    open suspend fun getAllPendingBatches(): List<PrintBatch> {
        val dao = itemDao ?: return emptyList()
        val bDao = batchDao ?: return emptyList()
        val allPendingItems = dao.getAllPendingItems()
        val batchIds = allPendingItems.map { it.batchId }.distinct()
        return batchIds.mapNotNull { id ->
            val entity = bDao.getBatchById(id)
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

    open suspend fun updateItemStatuses(ids: List<Long>, status: PrintItemStatus) {
        itemDao?.updateItemStatuses(ids, status.name)
    }

    open suspend fun updateBatchStatus(batchId: Long, status: BatchStatus) {
        batchDao?.updateBatchStatus(batchId, status.name)
    }

    open suspend fun resetPrintingItemsToPending() {
        itemDao?.resetPrintingToPending()
    }

    open suspend fun resetFailedItemsToPending() {
        itemDao?.resetFailedToPending()
    }

    open suspend fun getFailedItems(): List<PrintItem> {
        return itemDao?.getAllFailedItems()?.map {
            PrintItem(
                id = it.id,
                batchId = it.batchId,
                photoUri = it.photoUri,
                sequence = it.sequence,
                status = runCatching { PrintItemStatus.valueOf(it.status) }.getOrDefault(PrintItemStatus.FAILED)
            )
        } ?: emptyList()
    }

    open suspend fun resetFailedItemsToPendingForIds(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            itemDao?.updateItemStatuses(ids, PrintItemStatus.PENDING.name)
        }
    }

    open suspend fun clearPrintedItems() {
        itemDao?.deletePrintedItems()
    }

    open fun getAllItemsFlow(): Flow<List<PrintItem>> {
        val dao = itemDao ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return dao.getAllItemsFlow().map { list ->
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

    open fun getAllBatchesFlow(): Flow<List<PrintBatch>> {
        val dao = batchDao ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return dao.getAllBatchesFlow().map { list ->
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

    open suspend fun savePrinterConfig(printerType: String, printerName: String, settingsJson: String) {
        configDao?.insertOrUpdate(
            PrinterConfigEntity(
                id = "default",
                selectedPrinterId = printerName,
                printerType = printerType,
                settingsJson = settingsJson
            )
        )
    }

    open suspend fun getSavedPrinterConfig(): PrinterConfigEntity? {
        return configDao?.getConfig("default")
    }

    open suspend fun clearPrinterConfig() {
        configDao?.deleteConfig("default")
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
