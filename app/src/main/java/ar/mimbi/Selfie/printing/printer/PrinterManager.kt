package ar.mimbi.Selfie.printing.printer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PrinterManager(
    private var activePrinter: Printer? = null
) {
    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Disconnected)
    val statusFlow: StateFlow<PrinterStatus> = _status.asStateFlow()

    private val _currentPrinterName = MutableStateFlow<String?>(null)
    val currentPrinterName: StateFlow<String?> = _currentPrinterName.asStateFlow()

    fun getActivePrinter(): Printer? = activePrinter

}
