package ar.mimbi.Selfie.printing

import ar.mimbi.Selfie.printing.model.PageSize
import ar.mimbi.Selfie.printing.template.DefaultTemplates
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateRendererTest {

    @Test
    fun testPageSizeAndSlotCalculation() {
        val template1 = DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE
        assertEquals(1, template1.slotCount)

        val template2 = DefaultTemplates.TEMPLATE_DOUBLE_STRIP
        assertEquals(2, template2.slotCount)

        val template4 = DefaultTemplates.TEMPLATE_4_PHOTOS
        assertEquals(4, template4.slotCount)

        val pageSize = PageSize(1181, 1772)
        assertEquals(1181, pageSize.widthPx)
        assertEquals(1772, pageSize.heightPx)
    }
}
