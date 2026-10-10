package ar.mimbi.Selfie.printing

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueItemStatusTextTest {

    private fun formatPendingStatus(
        slotCount: Int,
        pendingInChunk: Int
    ): String {
        return if (slotCount > 1 && pendingInChunk < slotCount) {
            "Pendiente (Esperando más fotos)"
        } else {
            "Pendiente (Esperando impresora disponible)"
        }
    }

    @Test
    fun testPendingTextWhenLessPicturesThanRequired() {
        // Multi-picture template (4 photos required), 1 photo pending in chunk
        val result4With1 = formatPendingStatus(slotCount = 4, pendingInChunk = 1)
        assertEquals("Pendiente (Esperando más fotos)", result4With1)

        // Multi-picture template (4 photos required), 3 photos pending in chunk
        val result4With3 = formatPendingStatus(slotCount = 4, pendingInChunk = 3)
        assertEquals("Pendiente (Esperando más fotos)", result4With3)

        // Multi-picture template (2 photos required), 1 photo pending in chunk
        val result2With1 = formatPendingStatus(slotCount = 2, pendingInChunk = 1)
        assertEquals("Pendiente (Esperando más fotos)", result2With1)
    }

    @Test
    fun testPendingTextWhenPageIsFullOrSinglePictureTemplate() {
        // Multi-picture template (4 photos required), 4 photos pending in chunk (full page)
        val result4Full = formatPendingStatus(slotCount = 4, pendingInChunk = 4)
        assertEquals("Pendiente (Esperando impresora disponible)", result4Full)

        // Single-picture template (1 photo required), 1 photo pending
        val resultSingle = formatPendingStatus(slotCount = 1, pendingInChunk = 1)
        assertEquals("Pendiente (Esperando impresora disponible)", resultSingle)
    }
}
