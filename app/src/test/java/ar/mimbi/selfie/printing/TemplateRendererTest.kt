package ar.mimbi.selfie.printing

import ar.mimbi.selfie.printing.model.PageSize
import ar.mimbi.selfie.printing.template.DefaultTemplates
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

        val pageSize = PageSize(2480, 3508)
        assertEquals(2480, pageSize.widthPx)
        assertEquals(3508, pageSize.heightPx)
    }
}
