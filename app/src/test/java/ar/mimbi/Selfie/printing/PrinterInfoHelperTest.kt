package ar.mimbi.Selfie.printing

import ar.mimbi.Selfie.printing.printer.PrinterInfoHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class PrinterInfoHelperTest {

    @Test
    fun testFormatPrinterNameWithHashtags() {
        val raw = "service_id#d#HP_DeskJet_3700"
        val formatted = PrinterInfoHelper.formatPrinterName(raw)
        assertEquals("HP_DeskJet_3700", formatted)
    }

    @Test
    fun testFormatPrinterNameWithSlashes() {
        val raw = "com.android.bms/Epson_XP_4100"
        val formatted = PrinterInfoHelper.formatPrinterName(raw)
        assertEquals("Epson_XP_4100", formatted)
    }

    @Test
    fun testFormatPrinterNamePlain() {
        val raw = "Canon_TS5300"
        val formatted = PrinterInfoHelper.formatPrinterName(raw)
        assertEquals("Canon_TS5300", formatted)
    }
}
