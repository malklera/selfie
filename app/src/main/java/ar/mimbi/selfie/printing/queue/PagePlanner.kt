package ar.mimbi.selfie.printing.queue

import ar.mimbi.selfie.printing.model.PrintItem
import ar.mimbi.selfie.printing.model.PrintItemStatus
import ar.mimbi.selfie.printing.model.PrintPage
import ar.mimbi.selfie.printing.model.PrintPageStatus
import ar.mimbi.selfie.printing.model.PrintTemplate
import ar.mimbi.selfie.printing.model.PrintTemplateSnapshot

object PagePlanner {

    /**
     * Given an existing list of pages, enqueues [copies] of [photoUri] under [template].
     * Returns the updated list of pages and newly generated item IDs.
     */
    fun enqueue(
        pages: List<PrintPage>,
        template: PrintTemplate,
        photoUri: String,
        copies: Int,
        currentTimeMillis: Long = System.currentTimeMillis(),
        idGenerator: () -> Long = { System.nanoTime() }
    ): Pair<List<PrintPage>, List<PrintItem>> {
        val mutablePages = pages.toMutableList()
        val createdItems = mutableListOf<PrintItem>()

        repeat(copies) {
            val openPageIndex = mutablePages.indexOfLast {
                it.templateId == template.id &&
                        it.templateVersion == template.version &&
                        it.status == PrintPageStatus.OPEN
            }

            val targetPage: PrintPage
            val targetIndex: Int

            if (openPageIndex >= 0) {
                targetPage = mutablePages[openPageIndex]
                targetIndex = openPageIndex
            } else {
                val maxPageNum = mutablePages
                    .filter { it.templateId == template.id && it.templateVersion == template.version }
                    .maxOfOrNull { it.pageNumber } ?: 0

                val newPage = PrintPage(
                    id = idGenerator(),
                    templateId = template.id,
                    templateVersion = template.version,
                    templateSnapshotJson = PrintTemplateSnapshot.serialize(template),
                    pageNumber = maxPageNum + 1,
                    slotCount = template.slotCount,
                    createdAt = currentTimeMillis,
                    status = PrintPageStatus.OPEN,
                    items = emptyList()
                )
                mutablePages.add(newPage)
                targetPage = newPage
                targetIndex = mutablePages.lastIndex
            }

            val occupiedSlots = targetPage.items.map { it.slotIndex }.toSet()
            val nextSlotIndex = (0 until targetPage.slotCount).firstOrNull { it !in occupiedSlots } ?: 0

            val newItem = PrintItem(
                id = idGenerator(),
                pageId = targetPage.id,
                slotIndex = nextSlotIndex,
                photoUri = photoUri,
                sequence = targetPage.items.size.toLong() + 1,
                status = PrintItemStatus.PENDING
            )
            createdItems.add(newItem)

            val updatedItems = targetPage.items + newItem
            val isNowFull = updatedItems.size >= targetPage.slotCount

            val updatedPage = targetPage.copy(
                items = updatedItems,
                status = if (isNowFull) PrintPageStatus.READY else PrintPageStatus.OPEN,
                readyAt = if (isNowFull) currentTimeMillis else targetPage.readyAt
            )

            mutablePages[targetIndex] = updatedPage
        }

        return Pair(mutablePages, createdItems)
    }

    /**
     * Reflows slot indexes for a page after an item is removed.
     */
    fun reflowItemsAfterRemoval(
        page: PrintPage,
        removedItemId: Long
    ): PrintPage? {
        val remainingItems = page.items.filter { it.id != removedItemId }
        if (remainingItems.isEmpty()) {
            return null // Page should be deleted
        }

        val reflowedItems = remainingItems.mapIndexed { index, item ->
            item.copy(slotIndex = index, status = PrintItemStatus.PENDING)
        }

        val isFull = reflowedItems.size >= page.slotCount
        val newStatus = if (page.status == PrintPageStatus.READY && !isFull) {
            PrintPageStatus.OPEN
        } else {
            page.status
        }

        return page.copy(
            items = reflowedItems,
            status = newStatus,
            readyAt = if (newStatus == PrintPageStatus.OPEN) null else page.readyAt
        )
    }
}
