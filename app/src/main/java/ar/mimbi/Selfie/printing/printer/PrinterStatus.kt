package ar.mimbi.Selfie.printing.printer

sealed class PrinterStatus {
    data object Disconnected : PrinterStatus()
}
