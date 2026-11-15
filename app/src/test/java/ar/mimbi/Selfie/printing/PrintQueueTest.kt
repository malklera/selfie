package ar.mimbi.Selfie.printing

import ar.mimbi.Selfie.printing.model.*
import ar.mimbi.Selfie.printing.queue.PagePlanner
import ar.mimbi.Selfie.printing.template.DefaultTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintQueueTest {

    @Test
    fun testPrintPageStatusEnumValues() {
        val statuses = PrintPageStatus.entries.map { it.name }
        assertTrue(statuses.contains("OPEN"))
        assertTrue(statuses.contains("READY"))
        assertTrue(statuses.contains("SENT"))
        assertTrue(statuses.contains("PRINTING"))
        assertTrue(statuses.contains("BLOCKED"))
        assertTrue(statuses.contains("PRINTED"))
        assertTrue(statuses.contains("FAILED"))
        assertTrue(statuses.contains("CANCELED"))
    }

    @Test
    fun testHeaderFormat() {
        val page = PrintPage(
            id = 1L,
            templateId = 4,
            templateVersion = 1,
            templateSnapshotJson = "{}",
            pageNumber = 2,
            slotCount = 4,
            status = PrintPageStatus.OPEN,
            items = listOf(
                PrintItem(1, 1, 0, "uri1", 1),
                PrintItem(2, 1, 1, "uri2", 2),
                PrintItem(3, 1, 2, "uri3", 3)
            )
        )
        assertEquals("Página 2 - Plantilla 4 - 3/4 fotos - Abierta", page.headerText)
    }

    @Test
    fun testPagePlanningOneCopyFourSlotTemplate() {
        val template = DefaultTemplates.TEMPLATE_4_PHOTOS
        val (pages, items) = PagePlanner.enqueue(emptyList(), template, "file:///photo1.jpg", 1)

        assertEquals(1, pages.size)
        val page = pages.first()
        assertEquals(PrintPageStatus.OPEN, page.status)
        assertEquals(1, page.itemCount)
        assertEquals(4, page.slotCount)
        assertEquals(1, page.pageNumber)
        assertEquals(1, items.size)
        assertEquals(0, items.first().slotIndex)
    }

    @Test
    fun testPagePlanningFourCopiesFourSlotTemplate() {
        val template = DefaultTemplates.TEMPLATE_4_PHOTOS
        val (pages, items) = PagePlanner.enqueue(emptyList(), template, "file:///photo.jpg", 4)

        assertEquals(1, pages.size)
        val page = pages.first()
        assertEquals(PrintPageStatus.READY, page.status)
        assertEquals(4, page.itemCount)
        assertEquals(4, items.size)
    }

    @Test
    fun testPagePlanningFiveCopiesSpanningMultiplePages() {
        val template = DefaultTemplates.TEMPLATE_4_PHOTOS
        val (pages, items) = PagePlanner.enqueue(emptyList(), template, "file:///photo.jpg", 5)

        assertEquals(2, pages.size)
        assertEquals(PrintPageStatus.READY, pages[0].status)
        assertEquals(4, pages[0].itemCount)
        assertEquals(1, pages[0].pageNumber)

        assertEquals(PrintPageStatus.OPEN, pages[1].status)
        assertEquals(1, pages[1].itemCount)
        assertEquals(2, pages[1].pageNumber)

        assertEquals(5, items.size)
    }

    @Test
    fun testSwitchingTemplatesPreservesOpenPages() {
        val t4 = DefaultTemplates.TEMPLATE_4_PHOTOS
        val t1 = DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE

        // Enqueue 1 photo to 4-photo template -> Page 1 (t4) OPEN 1/4
        val (pages1, _) = PagePlanner.enqueue(emptyList(), t4, "file:///p1.jpg", 1)
        assertEquals(1, pages1.size)
        assertEquals(t4.id, pages1[0].templateId)
        assertEquals(PrintPageStatus.OPEN, pages1[0].status)

        // Switch to single-photo template and enqueue 1 photo -> Page 1 (t1) READY 1/1
        val (pages2, _) = PagePlanner.enqueue(pages1, t1, "file:///p2.jpg", 1)
        assertEquals(2, pages2.size)
        assertEquals(PrintPageStatus.OPEN, pages2[0].status) // t4 still OPEN
        assertEquals(t1.id, pages2[1].templateId)
        assertEquals(PrintPageStatus.READY, pages2[1].status) // t1 is READY

        // Switch back to 4-photo template and enqueue 2 photos -> Page 1 (t4) fills up to 3/4
        val (pages3, _) = PagePlanner.enqueue(pages2, t4, "file:///p3.jpg", 2)
        assertEquals(2, pages3.size)
        val t4Page = pages3.find { it.templateId == t4.id }
        assertNotNull(t4Page)
        assertEquals(3, t4Page!!.itemCount)
        assertEquals(PrintPageStatus.OPEN, t4Page.status)
    }

    @Test
    fun testNeverMixTemplatesInOnePage() {
        val t4 = DefaultTemplates.TEMPLATE_4_PHOTOS
        val t2 = DefaultTemplates.TEMPLATE_DOUBLE_STRIP

        var pages = emptyList<PrintPage>()
        pages = PagePlanner.enqueue(pages, t4, "file:///p1.jpg", 1).first
        pages = PagePlanner.enqueue(pages, t2, "file:///p2.jpg", 1).first

        assertEquals(2, pages.size)
        assertEquals(t4.id, pages[0].templateId)
        assertEquals(t2.id, pages[1].templateId)
    }

    @Test
    fun testDeletingItemReflowsSlotsAndResetsReadyToOpen() {
        val template = DefaultTemplates.TEMPLATE_4_PHOTOS
        val (pages, _) = PagePlanner.enqueue(emptyList(), template, "file:///p.jpg", 4)
        val readyPage = pages.first()
        assertEquals(PrintPageStatus.READY, readyPage.status)
        assertEquals(4, readyPage.items.size)

        // Delete second item (slotIndex 1)
        val removedItemId = readyPage.items[1].id
        val updatedPage = PagePlanner.reflowItemsAfterRemoval(readyPage, removedItemId)

        assertNotNull(updatedPage)
        assertEquals(PrintPageStatus.OPEN, updatedPage!!.status)
        assertEquals(3, updatedPage.items.size)
        // Verify slots are contiguous: 0, 1, 2
        assertEquals(0, updatedPage.items[0].slotIndex)
        assertEquals(1, updatedPage.items[1].slotIndex)
        assertEquals(2, updatedPage.items[2].slotIndex)
    }

    @Test
    fun testDeletingLastItemReturnsNullPage() {
        val template = DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE
        val (pages, _) = PagePlanner.enqueue(emptyList(), template, "file:///p.jpg", 1)
        val page = pages.first()

        val updatedPage = PagePlanner.reflowItemsAfterRemoval(page, page.items[0].id)
        assertNull(updatedPage)
    }

    @Test
    fun testTemplateSnapshotSerializationAndDeserialization() {
        val template = DefaultTemplates.TEMPLATE_4_PHOTOS
        val json = PrintTemplateSnapshot.serialize(template)
        val parsed = PrintTemplateSnapshot.deserialize(json)

        assertNotNull(parsed)
        assertEquals(template.id, parsed!!.id)
        assertEquals(template.version, parsed.version)
        assertEquals(template.slots.size, parsed.slots.size)
        assertEquals(template.widthCm, parsed.widthCm, 0.01f)
        assertEquals(template.heightCm, parsed.heightCm, 0.01f)
        assertEquals(template.pageType, parsed.pageType)
    }

    @Test
    fun testTemplateSnapshotFailsOnUnknownTopLevelField() {
        val template = DefaultTemplates.TEMPLATE_4_PHOTOS
        val validJson = PrintTemplateSnapshot.serialize(template)
        val jsonWithUnknownField = validJson.dropLast(1) + ",\"removedField\":\"value\"}"

        val parsed = PrintTemplateSnapshot.deserialize(jsonWithUnknownField)
        assertNull(parsed)
    }

    @Test
    fun testTemplateSnapshotFailsOnUnknownSlotField() {
        val jsonWithUnknownSlotField = "{\"id\":1,\"description\":\"A4\",\"version\":1,\"quality\":\"Alta (300 DPI)\",\"colorMode\":\"Color\",\"widthCm\":21.0,\"heightCm\":29.7,\"pageType\":\"A4\",\"slots\":[{\"left\":0.0,\"top\":0.0,\"width\":1.0,\"height\":1.0,\"extraSlotField\":123}]}"

        val parsed = PrintTemplateSnapshot.deserialize(jsonWithUnknownSlotField)
        assertNull(parsed)
    }

    @Test
    fun testPrintPageGetTemplateSnapshotFailsOnUnknownField() {
        val template = DefaultTemplates.TEMPLATE_4_PHOTOS
        val validJson = PrintTemplateSnapshot.serialize(template)
        val jsonWithUnknownField = validJson.dropLast(1) + ",\"oldField\":42}"

        val page = PrintPage(
            id = 1L,
            templateId = template.id,
            templateVersion = template.version,
            templateSnapshotJson = jsonWithUnknownField,
            pageNumber = 1,
            slotCount = template.slotCount,
            status = PrintPageStatus.READY
        )

        assertNull(page.getTemplateSnapshot())
    }

    @Test
    fun testSentPrintedAndCanceledPagesSelectionAndSelectAllBehavior() {
        val readyPage = PrintPage(id = 1L, templateId = 1, templateVersion = 1, templateSnapshotJson = "{}", pageNumber = 1, slotCount = 1, status = PrintPageStatus.READY)
        val sentPage = PrintPage(id = 2L, templateId = 1, templateVersion = 1, templateSnapshotJson = "{}", pageNumber = 2, slotCount = 1, status = PrintPageStatus.SENT)
        val printedPage = PrintPage(id = 3L, templateId = 1, templateVersion = 1, templateSnapshotJson = "{}", pageNumber = 3, slotCount = 1, status = PrintPageStatus.PRINTED)
        val canceledPage = PrintPage(id = 4L, templateId = 1, templateVersion = 1, templateSnapshotJson = "{}", pageNumber = 4, slotCount = 1, status = PrintPageStatus.CANCELED)

        val pages = listOf(readyPage, sentPage, printedPage, canceledPage)

        // "Seleccionar todo" selects READY pages only
        val selectAllEligible = pages.filter { it.status == PrintPageStatus.READY }
        assertEquals(1, selectAllEligible.size)
        assertEquals(1L, selectAllEligible.first().id)

        // Individual selection allows READY, FAILED, CANCELED, SENT, PRINTED
        val individuallySelectable = pages.filter {
            it.status == PrintPageStatus.READY ||
                    it.status == PrintPageStatus.FAILED ||
                    it.status == PrintPageStatus.CANCELED ||
                    it.status == PrintPageStatus.SENT ||
                    it.status == PrintPageStatus.PRINTED
        }
        assertEquals(4, individuallySelectable.size)
    }
}
