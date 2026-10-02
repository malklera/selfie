package ar.mimbi.Selfie.printing

import ar.mimbi.Selfie.printing.printer.PrinterStatus
import ar.mimbi.Selfie.printing.printer.WifiPrinter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiPrinterTest {

    @Test
    fun testWifiPrinterInvalidConnectionFails() = runBlocking {
        val wifiPrinter = WifiPrinter(ipAddress = "127.0.0.1", port = 65535, timeoutMs = 500)
        assertEquals(PrinterStatus.Disconnected, wifiPrinter.getStatus())

        var caught = false
        try {
            wifiPrinter.connect()
        } catch (_: Exception) {
            caught = true
        }
        assertTrue(caught)
        assertEquals(PrinterStatus.Disconnected, wifiPrinter.getStatus())
    }
}
