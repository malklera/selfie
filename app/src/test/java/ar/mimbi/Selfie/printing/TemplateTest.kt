package ar.mimbi.Selfie.printing

import ar.mimbi.Selfie.printing.template.DefaultTemplates
import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateTest {

    @Test
    fun testOnePhotoTemplateSlots() {
        val template = DefaultTemplates.TEMPLATE_FULL_WIDTH_SINGLE
        assertEquals(1, template.slotCount)

        val slot = template.slots[0]
        assertEquals(0.0f, slot.left, 0.001f)
        assertEquals(0.0f, slot.top, 0.001f)
        assertEquals(1.0f, slot.width, 0.001f)
        assertEquals(1.0f, slot.height, 0.001f)
    }

    @Test
    fun testSingleMarginTemplateSlots() {
        val template = DefaultTemplates.TEMPLATE_SINGLE_MARGIN
        assertEquals(1, template.slotCount)

        val slot = template.slots[0]
        // 10mm margins on A4 (210mm x 297mm)
        assertEquals(10.0f / 210.0f, slot.left, 0.001f)
        assertEquals(10.0f / 297.0f, slot.top, 0.001f)
        assertEquals(190.0f / 210.0f, slot.width, 0.001f)
        assertEquals(277.0f / 297.0f, slot.height, 0.001f)
        assertEquals(10.0f, template.marginMm, 0.001f)
    }

    @Test
    fun testTwoPhotosOneAboveTheOtherTemplateSlots() {
        val template = DefaultTemplates.TEMPLATE_DOUBLE_STRIP
        assertEquals(2, template.slotCount)

        val slot1 = template.slots[0]
        assertEquals(0.0f, slot1.left, 0.001f)
        assertEquals(0.0f, slot1.top, 0.001f)
        assertEquals(1.0f, slot1.width, 0.001f)
        assertEquals(143.5f / 297.0f, slot1.height, 0.001f)

        val slot2 = template.slots[1]
        assertEquals(0.0f, slot2.left, 0.001f)
        assertEquals(153.5f / 297.0f, slot2.top, 0.001f)
        assertEquals(1.0f, slot2.width, 0.001f)
        assertEquals(143.5f / 297.0f, slot2.height, 0.001f)

        val gapMm = (slot2.top - (slot1.top + slot1.height)) * 297.0f
        assertEquals(10.0f, gapMm, 0.01f)
    }

    @Test
    fun testFourPhotosTemplateSlotsWithCrossGap() {
        val template = DefaultTemplates.TEMPLATE_4_PHOTOS
        assertEquals(4, template.slotCount)

        // Outer edges touch page boundaries (0mm margin)
        val topLeft = template.slots[0]
        assertEquals(0.0f, topLeft.left, 0.001f)
        assertEquals(0.0f, topLeft.top, 0.001f)
        assertEquals(100.0f / 210.0f, topLeft.width, 0.001f)
        assertEquals(143.5f / 297.0f, topLeft.height, 0.001f)

        val topRight = template.slots[1]
        assertEquals(110.0f / 210.0f, topRight.left, 0.001f)
        assertEquals(1.0f, topRight.left + topRight.width, 0.001f) // touches right edge

        val bottomLeft = template.slots[2]
        assertEquals(153.5f / 297.0f, bottomLeft.top, 0.001f)
        assertEquals(1.0f, bottomLeft.top + bottomLeft.height, 0.001f) // touches bottom edge

        // Verify 10mm gap between left and right slots (110mm - 100mm = 10mm)
        val horizontalGapMm = (topRight.left - (topLeft.left + topLeft.width)) * 210.0f
        assertEquals(10.0f, horizontalGapMm, 0.01f)

        // Verify 10mm gap between top and bottom slots (153.5mm - 143.5mm = 10mm)
        val verticalGapMm = (bottomLeft.top - (topLeft.top + topLeft.height)) * 297.0f
        assertEquals(10.0f, verticalGapMm, 0.01f)
    }
}
