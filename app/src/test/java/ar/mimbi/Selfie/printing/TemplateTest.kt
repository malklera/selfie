package ar.mimbi.Selfie.printing

import ar.mimbi.Selfie.printing.template.DefaultTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun testTwoPhotosTemplateSlots() {
        val template = DefaultTemplates.TEMPLATE_DOUBLE_STRIP
        assertEquals(2, template.slotCount)

        val slot1 = template.slots[0]
        assertEquals(0.0f, slot1.left, 0.001f)
        assertEquals(0.0f, slot1.top, 0.001f)
        assertEquals(1.0f, slot1.width, 0.001f)
        assertEquals(0.5f, slot1.height, 0.001f)

        val slot2 = template.slots[1]
        assertEquals(0.0f, slot2.left, 0.001f)
        assertEquals(0.5f, slot2.top, 0.001f)
        assertEquals(1.0f, slot2.width, 0.001f)
        assertEquals(0.5f, slot2.height, 0.001f)
    }

    @Test
    fun testFourPhotosTemplateSlots() {
        val template = DefaultTemplates.TEMPLATE_4_PHOTOS
        assertEquals(4, template.slotCount)

        template.slots.forEach { slot ->
            assertTrue(slot.left in 0.0f..1.0f)
            assertTrue(slot.top in 0.0f..1.0f)
            assertTrue(slot.width > 0f)
            assertTrue(slot.height > 0f)
            assertTrue(slot.left + slot.width <= 1.001f)
            assertTrue(slot.top + slot.height <= 1.001f)
        }
    }
}
