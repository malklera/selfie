package ar.mimbi.Selfie.printing.queue

import ar.mimbi.Selfie.data.db.AppDatabase
import ar.mimbi.Selfie.printing.model.*
import ar.mimbi.Selfie.printing.persistence.*
import ar.mimbi.Selfie.printing.template.DefaultTemplates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

open class PrintQueueRepository(private val database: AppDatabase? = null) {

    private val templateDao get() = database?.printTemplateDao()
    private val batchDao get() = database?.printBatchDao()
    private val pageDao get() = database?.printPageDao()
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
                        colorMode = template.colorMode,
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
        val dao = templateDao ?: return flowOf(DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE)
        return dao.getActiveTemplateFlow().map { entity ->
            if (entity == null) DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE else parseTemplateEntity(entity)
        }
    }

    open suspend fun getTemplateById(id: Int): PrintTemplate? {
        val entity = templateDao?.getTemplateById(id) ?: return null
        return parseTemplateEntity(entity)
    }

    open suspend fun enqueuePhotos(template: PrintTemplate, photoUri: String, copies: Int): List<Long> {
        val pDao = pageDao ?: return emptyList()
        val iDao = itemDao ?: return emptyList()

        val insertedItemIds = mutableListOf<Long>()
        val lastGlobalItem = iDao.getLastItem()
        var currentSeq = (lastGlobalItem?.sequence ?: 0) + 1

        val snapshotJson = PrintTemplateSnapshot.serialize(template)

        repeat(copies) {
            var openPage = pDao.getLatestOpenPage(template.id, template.version)
            var currentItemsInOpenPage = if (openPage != null) iDao.getItemsByPageId(openPage.id) else emptyList()

            val pageId: Long
            val slotIndex: Int

            if (openPage == null || currentItemsInOpenPage.size >= template.slotCount) {
                val maxPageNum = pDao.getMaxPageNumber(template.id, template.version) ?: 0
                val newPageEntity = PrintPageEntity(
                    templateId = template.id,
                    templateVersion = template.version,
                    templateSnapshotJson = snapshotJson,
                    pageNumber = maxPageNum + 1,
                    slotCount = template.slotCount,
                    createdAt = System.currentTimeMillis(),
                    status = PrintPageStatus.OPEN.name
                )
                pageId = pDao.insertPage(newPageEntity)
                slotIndex = 0
                currentItemsInOpenPage = emptyList()
            } else {
                pageId = openPage.id
                val occupiedSlots = currentItemsInOpenPage.map { it.slotIndex }.toSet()
                slotIndex = (0 until template.slotCount).firstOrNull { it !in occupiedSlots } ?: currentItemsInOpenPage.size
            }

            val itemEntity = PrintItemEntity(
                pageId = pageId,
                slotIndex = slotIndex,
                photoUri = photoUri,
                sequence = currentSeq++,
                status = PrintItemStatus.PENDING.name
            )
            val insertedId = iDao.insertItem(itemEntity)
            insertedItemIds.add(insertedId)

            val totalItemsNow = currentItemsInOpenPage.size + 1
            if (totalItemsNow >= template.slotCount) {
                pDao.updatePageStatus(pageId, PrintPageStatus.READY.name)
                val readyTime = System.currentTimeMillis()
                val existingPage = pDao.getPageById(pageId)
                if (existingPage != null) {
                    pDao.updatePage(existingPage.copy(readyAt = readyTime, status = PrintPageStatus.READY.name))
                }
            }
        }

        return insertedItemIds
    }

    open fun getAllPagesFlow(): Flow<List<PrintPage>> {
        val pDao = pageDao ?: return flowOf(emptyList())
        return pDao.getAllPagesWithItemsFlow().map { list ->
            list.map { mapToDomainPage(it) }
        }
    }

    open suspend fun getAllPages(): List<PrintPage> {
        val pDao = pageDao ?: return emptyList()
        return pDao.getAllPagesWithItems().map { mapToDomainPage(it) }
    }

    open suspend fun getPagesByIds(ids: List<Long>): List<PrintPage> {
        if (ids.isEmpty()) return emptyList()
        val pDao = pageDao ?: return emptyList()
        return pDao.getPagesWithItemsByIds(ids).map { mapToDomainPage(it) }
    }

    open suspend fun getAllReadyPages(): List<PrintPage> {
        val pDao = pageDao ?: return emptyList()
        return pDao.getPagesWithItemsByStatus(PrintPageStatus.READY.name).map { mapToDomainPage(it) }
    }

    open suspend fun getFailedOrCanceledPages(): List<PrintPage> {
        val pDao = pageDao ?: return emptyList()
        return pDao.getFailedOrCanceledPagesWithItems().map { mapToDomainPage(it) }
    }

    open suspend fun updatePageStatuses(pageIds: List<Long>, status: PrintPageStatus, statusMessage: String? = null) {
        if (pageIds.isEmpty()) return
        pageDao?.updatePageStatuses(pageIds, status.name, statusMessage)

        val itemStatus = when (status) {
            PrintPageStatus.OPEN, PrintPageStatus.READY -> PrintItemStatus.PENDING
            PrintPageStatus.SENT -> PrintItemStatus.SENT
            PrintPageStatus.PRINTING -> PrintItemStatus.PRINTING
            PrintPageStatus.BLOCKED -> PrintItemStatus.BLOCKED
            PrintPageStatus.PRINTED -> PrintItemStatus.PRINTED
            PrintPageStatus.FAILED -> PrintItemStatus.FAILED
            PrintPageStatus.CANCELED -> PrintItemStatus.CANCELED
        }
        itemDao?.updateItemStatusesForPages(pageIds, itemStatus.name)
    }

    open suspend fun markPageWasPartial(pageId: Long) {
        val pDao = pageDao ?: return
        val existing = pDao.getPageById(pageId) ?: return
        pDao.updatePage(existing.copy(wasPartial = true, status = PrintPageStatus.READY.name))
    }

    open suspend fun updatePageSentJob(pageIds: List<Long>, jobId: String?) {
        if (pageIds.isEmpty()) return
        pageDao?.updatePageSentJob(pageIds, PrintPageStatus.SENT.name, jobId)
        itemDao?.updateItemStatusesForPages(pageIds, PrintItemStatus.SENT.name)
    }

    open suspend fun updatePagesPrinted(pageIds: List<Long>, printedAt: Long = System.currentTimeMillis()) {
        if (pageIds.isEmpty()) return
        pageDao?.updatePagesPrintedInfo(pageIds, printedAt, PrintPageStatus.PRINTED.name)
        itemDao?.updateItemStatusesForPages(pageIds, PrintItemStatus.PRINTED.name)
    }

    open suspend fun deletePages(pageIds: List<Long>) {
        if (pageIds.isEmpty()) return
        itemDao?.deleteItemsByPageIds(pageIds)
        pageDao?.deletePages(pageIds)
    }

    open suspend fun deletePage(pageId: Long) {
        itemDao?.deleteItemsByPageId(pageId)
        pageDao?.deletePage(pageId)
    }

    open suspend fun deleteItem(itemId: Long) {
        val iDao = itemDao ?: return
        val pDao = pageDao ?: return

        val itemEntity = iDao.getItemById(itemId) ?: return
        val pageId = itemEntity.pageId
        val pageEntity = pDao.getPageById(pageId) ?: run {
            iDao.deleteItemsByIds(listOf(itemId))
            return
        }

        val pageStatus = runCatching { PrintPageStatus.valueOf(pageEntity.status) }.getOrDefault(PrintPageStatus.OPEN)

        if (pageStatus == PrintPageStatus.OPEN || pageStatus == PrintPageStatus.READY) {
            iDao.deleteItemsByIds(listOf(itemId))
            val remaining = iDao.getItemsByPageId(pageId)
            if (remaining.isEmpty()) {
                pDao.deletePage(pageId)
            } else {
                val reflowed = remaining.mapIndexed { index, it ->
                    it.copy(slotIndex = index, status = PrintItemStatus.PENDING.name)
                }
                iDao.insertItems(reflowed)

                if (pageStatus == PrintPageStatus.READY && reflowed.size < pageEntity.slotCount) {
                    pDao.updatePage(pageEntity.copy(status = PrintPageStatus.OPEN.name, readyAt = null))
                }
            }
        } else {
            iDao.deleteItemsByIds(listOf(itemId))
            val remaining = iDao.getItemsByPageId(pageId)
            if (remaining.isEmpty()) {
                pDao.deletePage(pageId)
            }
        }
    }

    open suspend fun clearPrintedPages() {
        itemDao?.deletePrintedItems()
        pageDao?.deletePrintedPages()
    }

    open suspend fun clearQueue() {
        itemDao?.deleteAllItems()
        pageDao?.deleteAllPages()
        batchDao?.deleteAllBatches()
    }

    open suspend fun resetInFlightPagesToReadyOrOpen() {
        val pDao = pageDao ?: return
        val printingPages = pDao.getPagesWithItemsByStatus(PrintPageStatus.PRINTING.name)
        for (withItems in printingPages) {
            val page = withItems.page
            val itemsCount = withItems.items.size
            val newStatus = if (itemsCount >= page.slotCount || page.wasPartial) {
                PrintPageStatus.READY.name
            } else {
                PrintPageStatus.OPEN.name
            }
            pDao.updatePageStatus(page.id, newStatus)
            itemDao?.updateItemStatusesForPage(page.id, PrintItemStatus.PENDING.name)
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

    private fun mapToDomainPage(withItems: PrintPageWithItems): PrintPage {
        val pageEntity = withItems.page
        val itemEntities = withItems.items.sortedBy { it.slotIndex }

        val pageStatus = runCatching { PrintPageStatus.valueOf(pageEntity.status) }
            .getOrDefault(PrintPageStatus.OPEN)

        val items = itemEntities.map { item ->
            val itemStatus = runCatching { PrintItemStatus.valueOf(item.status) }
                .getOrDefault(PrintItemStatus.PENDING)

            PrintItem(
                id = item.id,
                pageId = item.pageId,
                slotIndex = item.slotIndex,
                photoUri = item.photoUri,
                sequence = item.sequence,
                status = itemStatus
            )
        }

        return PrintPage(
            id = pageEntity.id,
            templateId = pageEntity.templateId,
            templateVersion = pageEntity.templateVersion,
            templateSnapshotJson = pageEntity.templateSnapshotJson,
            pageNumber = pageEntity.pageNumber,
            slotCount = pageEntity.slotCount,
            createdAt = pageEntity.createdAt,
            readyAt = pageEntity.readyAt,
            status = pageStatus,
            wasPartial = pageEntity.wasPartial,
            statusMessage = pageEntity.statusMessage,
            lastPrintJobId = pageEntity.lastPrintJobId,
            printedAt = pageEntity.printedAt,
            items = items
        )
    }

    private fun parseTemplateEntity(entity: PrintTemplateEntity): PrintTemplate {
        val slots = mutableListOf<PhotoSlot>()
        try {
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
        } catch (e: Exception) {
            return DefaultTemplates.getById(entity.id)
        }
        return PrintTemplate(
            id = entity.id,
            description = entity.description,
            slots = slots,
            version = entity.version,
            quality = entity.quality,
            colorMode = entity.colorMode,
            widthCm = entity.widthCm,
            heightCm = entity.heightCm,
            marginMm = entity.marginMm,
            isFullWidth = entity.isFullWidth
        )
    }
}
