package ar.mimbi.Selfie.printing

import ar.mimbi.Selfie.printing.model.PageSize
import ar.mimbi.Selfie.printing.model.PrintablePage
import ar.mimbi.Selfie.printing.printer.FakePrinter
import ar.mimbi.Selfie.printing.printer.PrinterStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePrinterTest {

    @Test
    fun testFakePrinterConnectionAndPrint() = runBlocking {
        val fakePrinter = FakePrinter(printDelayMs = 0)
        assertEquals(PrinterStatus.Ready, fakePrinter.getStatus())

        fakePrinter.connect()
        assertTrue(fakePrinter.isConnected)

        val page = PrintablePage(bitmap = null, pageSize = PageSize(100, 100))

        fakePrinter.print(page)

        assertEquals(1, fakePrinter.printedPages.size)
        assertEquals(PrinterStatus.Ready, fakePrinter.getStatus())

        fakePrinter.disconnect()
        assertEquals(PrinterStatus.Disconnected, fakePrinter.getStatus())
    }

    @Test(expected = IllegalStateException::class)
    fun testFakePrinterErrorSimulation() = runBlocking {
        val fakePrinter = FakePrinter(printDelayMs = 0, shouldFail = true, failureMessage = "Out of paper")
        fakePrinter.connect()

        val page = PrintablePage(bitmap = null, pageSize = PageSize(100, 100))

        fakePrinter.print(page)
    }
}
