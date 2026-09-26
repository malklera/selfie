package ar.mimbi.Selfie.printing.model

/**
 * Generic printer configuration.
 */
data class PrinterConfig(
    val selectedPrinterId: String = "fake_printer",
    val printerType: String = "FAKE",
    val settingsJson: String = "{}"
)
