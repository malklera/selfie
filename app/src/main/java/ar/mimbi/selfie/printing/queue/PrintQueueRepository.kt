package ar.mimbi.selfie.printing.queue

import ar.mimbi.selfie.data.ConfigDataStore
import ar.mimbi.selfie.data.db.AppDatabase
import ar.mimbi.selfie.printing.model.*
import ar.mimbi.selfie.printing.persistence.*
import ar.mimbi.selfie.printing.template.DefaultTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

open class PrintQueueRepository(
    private val database: AppDatabase? = null,
    private val configDataStore: ConfigDataStore? = null
) {

    private val pageDao get() = database?.printPageDao()
    private val itemDao get() = database?.printItemDao()

    open suspend fun getActiveTemplate(): PrintTemplate = withContext(Dispatchers.IO) {
        val activeId = configDataStore?.getActiveTemplateId()
        DefaultTemplates.getById(activeId)
    }

    open suspend fun setActiveTemplate(templateId: Int) {
        if (DefaultTemplates.ALL_TEMPLATES.none { it.id == templateId }) return
        configDataStore?.setActiveTemplateId(templateId)
    }

    open fun getActiveTemplateFlow(): Flow<PrintTemplate> {
        val ids = configDataStore?.activeTemplateIdFlow ?: flowOf(null)
        return ids.map { DefaultTemplates.getById(it) }
    }

    open suspend fun enqueuePhotos(template: PrintTemplate, photoUri: String, copies: Int): List<Long> = withContext(Dispatchers.IO) {
        val pDao = pageDao ?: return@withContext emptyList()
        val iDao = itemDao ?: return@withContext emptyList()

        val insertedItemIds = mutableListOf<Long>()
        val lastGlobalItem = iDao.getLastItem()
        var currentSeq = (lastGlobalItem?.sequence ?: 0) + 1

        val snapshotJson = PrintTemplateSnapshot.serialize(template)

        repeat(copies) {
            val openPage = pDao.getLatestOpenPage(template.id, template.version)
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

        insertedItemIds
    }

    open fun getAllPagesFlow(): Flow<List<PrintPage>> {
        val pDao = pageDao ?: return flowOf(emptyList())
        return pDao.getAllPagesWithItemsFlow().map { list ->
            list.map { mapToDomainPage(it) }
        }
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

    open suspend fun clearPrintedPages() {
        itemDao?.deletePrintedItems()
        pageDao?.deletePrintedPages()
    }

    open suspend fun clearQueue() {
        itemDao?.deleteAllItems()
        pageDao?.deleteAllPages()
    }

    open suspend fun resetInFlightPagesToReadyOrOpen() {
        val pDao = pageDao ?: return
        val printingPages = pDao.getPagesWithItemsByStatus(PrintPageStatus.PRINTING.name)
        for ((page, items) in printingPages) {
            val itemsCount = items.size
            val newStatus = if (itemsCount >= page.slotCount || page.wasPartial) {
                PrintPageStatus.READY.name
            } else {
                PrintPageStatus.OPEN.name
            }
            pDao.updatePageStatus(page.id, newStatus)
            itemDao?.updateItemStatusesForPage(page.id, PrintItemStatus.PENDING.name)
        }
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
}
