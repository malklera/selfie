package ar.mimbi.Selfie.printing.printer

sealed class PrinterStatus {
    data object Ready : PrinterStatus()
    data object Printing : PrinterStatus()
    data class Error(val message: String) : PrinterStatus()
    data object Disconnected : PrinterStatus()
}
