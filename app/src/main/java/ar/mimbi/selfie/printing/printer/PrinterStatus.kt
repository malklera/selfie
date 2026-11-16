package ar.mimbi.selfie.printing.printer

sealed class PrinterStatus {
    data object Disconnected : PrinterStatus()
}
