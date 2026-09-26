package ar.mimbi.Selfie.printing.printer

import ar.mimbi.Selfie.printing.model.PrintablePage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePrinter(
    var printDelayMs: Long = 100L,
    var shouldFail: Boolean = false,
    var failureMessage: String = "Fake printer error"
) : Printer {

    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Ready)
    val statusFlow: StateFlow<PrinterStatus> = _status.asStateFlow()

    val printedPages = mutableListOf<PrintablePage>()
    var isConnected = false

    override suspend fun connect() {
        isConnected = true
        _status.value = PrinterStatus.Ready
    }

    override suspend fun getStatus(): PrinterStatus {
        return _status.value
    }

    override suspend fun print(page: PrintablePage) {
        if (!isConnected) {
            connect()
        }
        _status.value = PrinterStatus.Printing
        if (printDelayMs > 0) {
            delay(printDelayMs)
        }
        if (shouldFail) {
            _status.value = PrinterStatus.Error(failureMessage)
            throw IllegalStateException(failureMessage)
        }
        printedPages.add(page)
        _status.value = PrinterStatus.Ready
    }

    override suspend fun disconnect() {
        isConnected = false
        _status.value = PrinterStatus.Disconnected
    }

    fun clearHistory() {
        printedPages.clear()
    }
}
