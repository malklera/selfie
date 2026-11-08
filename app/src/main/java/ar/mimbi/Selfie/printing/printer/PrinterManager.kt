package ar.mimbi.Selfie.printing.printer

import ar.mimbi.Selfie.printing.model.PrintablePage
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

    fun hasConfiguredPrinter(): Boolean = activePrinter != null

    fun setPrinter(printer: Printer?, printerName: String? = null) {
        activePrinter = printer
        _currentPrinterName.value = printerName
        if (printer == null) {
            _status.value = PrinterStatus.Disconnected
        }
    }

    suspend fun connect(): Boolean {
        val printer = activePrinter
        if (printer == null) {
            _status.value = PrinterStatus.Disconnected
            return false
        }
        return try {
            printer.connect()
            val st = printer.getStatus()
            _status.value = st
            st is PrinterStatus.Ready
        } catch (e: Exception) {
            _status.value = PrinterStatus.Error(e.message ?: "Conexión fallida")
            false
        }
    }

    suspend fun printPage(page: PrintablePage) {
        val printer = activePrinter ?: throw IllegalStateException("No hay impresora configurada")
        try {
            _status.value = PrinterStatus.Printing
            printer.print(page)
            _status.value = printer.getStatus()
        } catch (e: Exception) {
            _status.value = PrinterStatus.Error(e.message ?: "Error de impresión")
            throw e
        }
    }

    suspend fun getStatus(): PrinterStatus {
        val printer = activePrinter ?: return PrinterStatus.Disconnected
        val current = printer.getStatus()
        _status.value = current
        return current
    }

    suspend fun disconnect() {
        try {
            activePrinter?.disconnect()
            _status.value = PrinterStatus.Disconnected
        } catch (e: Exception) {
            _status.value = PrinterStatus.Error(e.message ?: "Error al desconectar")
        }
    }
}
