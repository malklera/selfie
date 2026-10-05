package ar.mimbi.Selfie.printing

import android.print.PrintJobInfo
import ar.mimbi.Selfie.printing.model.PrintItemStatus
import ar.mimbi.Selfie.printing.printer.StandardAndroidPrinter
import org.junit.Assert.assertEquals
import org.junit.Test

class StandardAndroidPrinterTest {

    @Test
    fun testMapStateToStatusAndSpanish() {
        val (createdStatus, createdSpanish) = StandardAndroidPrinter.mapStateToStatusAndSpanish(PrintJobInfo.STATE_CREATED)
        assertEquals(PrintItemStatus.QUEUED, createdStatus)
        assertEquals("Creado - Inicializando", createdSpanish)

        val (queuedStatus, queuedSpanish) = StandardAndroidPrinter.mapStateToStatusAndSpanish(PrintJobInfo.STATE_QUEUED)
        assertEquals(PrintItemStatus.QUEUED, queuedStatus)
        assertEquals("En cola de impresión", queuedSpanish)

        val (startedStatus, startedSpanish) = StandardAndroidPrinter.mapStateToStatusAndSpanish(PrintJobInfo.STATE_STARTED)
        assertEquals(PrintItemStatus.PRINTING, startedStatus)
        assertEquals("Imprimiendo - Enviando a la impresora", startedSpanish)

        val (blockedStatus, blockedSpanish) = StandardAndroidPrinter.mapStateToStatusAndSpanish(PrintJobInfo.STATE_BLOCKED)
        assertEquals(PrintItemStatus.BLOCKED, blockedStatus)
        assertEquals("Bloqueado - Verificar impresora (sin papel o pausada)", blockedSpanish)

        val (completedStatus, completedSpanish) = StandardAndroidPrinter.mapStateToStatusAndSpanish(PrintJobInfo.STATE_COMPLETED)
        assertEquals(PrintItemStatus.PRINTED, completedStatus)
        assertEquals("Completado exitosamente", completedSpanish)

        val (failedStatus, failedSpanish) = StandardAndroidPrinter.mapStateToStatusAndSpanish(PrintJobInfo.STATE_FAILED)
        assertEquals(PrintItemStatus.FAILED, failedStatus)
        assertEquals("Fallido - Error en el servicio de impresión", failedSpanish)

        val (canceledStatus, canceledSpanish) = StandardAndroidPrinter.mapStateToStatusAndSpanish(PrintJobInfo.STATE_CANCELED)
        assertEquals(PrintItemStatus.CANCELED, canceledStatus)
        assertEquals("Cancelado por el usuario", canceledSpanish)
    }
}
