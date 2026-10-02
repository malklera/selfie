package ar.mimbi.Selfie.printing.printer

import ar.mimbi.Selfie.printing.model.PrintablePage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PrinterManager(
    private var activePrinter: Printer = FakePrinter()
) {
    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Ready)
    val statusFlow: StateFlow<PrinterStatus> = _status.asStateFlow()

    private val _currentPrinterName = MutableStateFlow("Impresora de Prueba (Fake Printer)")
    val currentPrinterName: StateFlow<String> = _currentPrinterName.asStateFlow()

    fun setPrinter(printer: Printer, printerName: String = "Impresora de Prueba (Fake Printer)") {
        activePrinter = printer
        _currentPrinterName.value = printerName
    }

    suspend fun connect() {
        try {
            activePrinter.connect()
            _status.value = activePrinter.getStatus()
        } catch (e: Exception) {
            _status.value = PrinterStatus.Error(e.message ?: "Connection failed")
        }
    }

    suspend fun printPage(page: PrintablePage) {
        try {
            _status.value = PrinterStatus.Printing
            activePrinter.print(page)
            _status.value = activePrinter.getStatus()
        } catch (e: Exception) {
            _status.value = PrinterStatus.Error(e.message ?: "Print failed")
            throw e
        }
    }

    suspend fun getStatus(): PrinterStatus {
        val current = activePrinter.getStatus()
        _status.value = current
        return current
    }

    suspend fun disconnect() {
        try {
            activePrinter.disconnect()
            _status.value = PrinterStatus.Disconnected
        } catch (e: Exception) {
            _status.value = PrinterStatus.Error(e.message ?: "Disconnect failed")
        }
    }
}
