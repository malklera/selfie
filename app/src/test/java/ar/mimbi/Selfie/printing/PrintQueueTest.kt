package ar.mimbi.Selfie.printing

import ar.mimbi.Selfie.printing.model.PrintItem
import ar.mimbi.Selfie.printing.model.PrintItemStatus
import ar.mimbi.Selfie.printing.template.DefaultTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

class PrintQueueTest {

    @Test
    fun testPrintItemStatusEnumContainsSent() {
        val statuses = PrintItemStatus.entries.map { it.name }
        assertTrue(statuses.contains("PENDING"))
        assertTrue(statuses.contains("SENT"))
        assertTrue(statuses.contains("PRINTED"))
        assertTrue(statuses.contains("FAILED"))
    }

    @Test
    fun testMultiPagePdfPageCountCalculation() {
        // Single photo template -> 1 slot per page
        val singleTemplate = DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE
        val selectedCount1 = 5
        val pagesSingle = ceil(selectedCount1.toFloat() / singleTemplate.slotCount).toInt()
        assertEquals(5, pagesSingle)

        // Double strip template -> 2 slots per page
        val doubleTemplate = DefaultTemplates.TEMPLATE_DOUBLE_STRIP
        val selectedCount2 = 5
        val pagesDouble = ceil(selectedCount2.toFloat() / doubleTemplate.slotCount).toInt()
        assertEquals(3, pagesDouble)

        // 4 photos template -> 4 slots per page
        val fourTemplate = DefaultTemplates.TEMPLATE_4_PHOTOS
        val selectedCount4 = 9
        val pagesFour = ceil(selectedCount4.toFloat() / fourTemplate.slotCount).toInt()
        assertEquals(3, pagesFour)
    }

    @Test
    fun testChunkingSelectedItemsForMultiPagePdf() {
        val template = DefaultTemplates.TEMPLATE_4_PHOTOS
        val items = (1..9).map { index ->
            PrintItem(
                id = index.toLong(),
                batchId = 1L,
                photoUri = "file:///dummy/photo_$index.jpg",
                sequence = index.toLong(),
                status = PrintItemStatus.PENDING
            )
        }

        val chunks = items.chunked(template.slotCount)
        assertEquals(3, chunks.size)
        assertEquals(4, chunks[0].size)
        assertEquals(4, chunks[1].size)
        assertEquals(1, chunks[2].size)
    }
}
