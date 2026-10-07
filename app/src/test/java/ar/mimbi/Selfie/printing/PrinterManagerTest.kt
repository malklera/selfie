package ar.mimbi.Selfie.printing

import ar.mimbi.Selfie.printing.model.PageSize
import ar.mimbi.Selfie.printing.model.PrintablePage
import ar.mimbi.Selfie.printing.printer.FakePrinter
import ar.mimbi.Selfie.printing.printer.PrinterManager
import ar.mimbi.Selfie.printing.printer.PrinterStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterManagerTest {

    @Test
    fun testDefaultPrinterManagerHasNoConfiguredPrinter() = runBlocking {
        val manager = PrinterManager()
        assertFalse(manager.hasConfiguredPrinter())
        assertNull(manager.getActivePrinter())
        assertEquals(PrinterStatus.Disconnected, manager.getStatus())
        assertEquals("Ninguna (Sin configurar)", manager.currentPrinterName.value)
    }

    @Test(expected = IllegalStateException::class)
    fun testPrintWithoutConfiguredPrinterThrows() = runBlocking {
        val manager = PrinterManager()
        manager.printPage(PrintablePage(bitmap = null, pageSize = PageSize(100, 100)))
    }

    @Test
    fun testSetPrinterAndClearPrinter() = runBlocking {
        val manager = PrinterManager()
        val fake = FakePrinter()

        manager.setPrinter(fake, "Mi Impresora", printerType = "FAKE", saveToDb = false)
        assertTrue(manager.hasConfiguredPrinter())
        assertEquals("Mi Impresora", manager.currentPrinterName.value)

        manager.connect()
        assertEquals(PrinterStatus.Ready, manager.getStatus())

        manager.clearPrinter(clearFromDb = false)
        assertFalse(manager.hasConfiguredPrinter())
        assertNull(manager.getActivePrinter())
        assertEquals(PrinterStatus.Disconnected, manager.getStatus())
        assertEquals("Ninguna (Sin configurar)", manager.currentPrinterName.value)
    }

    @Test
    fun testConnectWithoutConfiguredPrinterDoesNotBecomeReady() = runBlocking {
        val manager = PrinterManager()
        manager.connect()
        assertEquals(PrinterStatus.Disconnected, manager.getStatus())
    }
}
