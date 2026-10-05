package ar.mimbi.Selfie.printing.printer

import ar.mimbi.Selfie.printing.model.PrintablePage
import ar.mimbi.Selfie.printing.queue.PrintQueueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class PrinterManager(
    private val repository: PrintQueueRepository? = null,
    private var activePrinter: Printer = FakePrinter()
) {
    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Ready)
    val statusFlow: StateFlow<PrinterStatus> = _status.asStateFlow()

    private val _currentPrinterName = MutableStateFlow("Impresora de Prueba (Fake Printer)")
    val currentPrinterName: StateFlow<String> = _currentPrinterName.asStateFlow()

    init {
        repository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val saved = repo.getSavedPrinterConfig()
                    if (saved != null) {
                        if (saved.printerType == "WIFI") {
                            val json = JSONObject(saved.settingsJson)
                            val ip = json.optString("ipAddress")
                            val port = json.optInt("port", 9100)
                            val name = json.optString("printerName", "Impresora Wi-Fi")
                            if (ip.isNotBlank()) {
                                val wifiPrinter = WifiPrinter(ip, port, timeoutMs = 2000)
                                setPrinter(wifiPrinter, name, printerType = "WIFI", ipAddress = ip, port = port, saveToDb = false)
                                connect()
                            }
                        } else {
                            setPrinter(FakePrinter(), "Impresora de Prueba (Fake Printer)", printerType = "FAKE", saveToDb = false)
                            connect()
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun setPrinter(
        printer: Printer,
        printerName: String = "Impresora de Prueba (Fake Printer)",
        printerType: String = if (printer is FakePrinter) "FAKE" else "WIFI",
        ipAddress: String? = null,
        port: Int? = null,
        saveToDb: Boolean = true
    ) {
        activePrinter = printer
        _currentPrinterName.value = printerName
        if (saveToDb && repository != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val json = JSONObject().apply {
                        put("printerName", printerName)
                        put("ipAddress", ipAddress ?: "")
                        put("port", port ?: 9100)
                    }.toString()
                    repository.savePrinterConfig(printerType, printerName, json)
                } catch (_: Exception) {}
            }
        }
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
