package ar.mimbi.Selfie.printing.printer

import android.content.Context
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
    private var activePrinter: Printer? = null
) {
    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Disconnected)
    val statusFlow: StateFlow<PrinterStatus> = _status.asStateFlow()

    private val _currentPrinterName = MutableStateFlow("Ninguna (Sin configurar)")
    val currentPrinterName: StateFlow<String> = _currentPrinterName.asStateFlow()

    private var currentPrinterType: String? = null

    fun getActivePrinter(): Printer? = activePrinter

    fun hasConfiguredPrinter(): Boolean = activePrinter != null && currentPrinterType != null

    fun getPrinterType(): String? = currentPrinterType

    fun initFromRepository(context: Context) {
        repository?.let { repo ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val saved = repo.getSavedPrinterConfig()
                    if (saved != null) {
                        when (saved.printerType) {
                            "SYSTEM_ANDROID" -> {
                                val json = runCatching { JSONObject(saved.settingsJson) }.getOrNull()
                                val isUserConfigured = json?.optBoolean("userConfigured", false) ?: false
                                if (isUserConfigured) {
                                    val androidPrinter = StandardAndroidPrinter(context)
                                    setPrinter(
                                        printer = androidPrinter,
                                        printerName = saved.selectedPrinterId.ifBlank { "Servicio de Impresión Android (Standard)" },
                                        printerType = "SYSTEM_ANDROID",
                                        saveToDb = false
                                    )
                                    connect()
                                } else {
                                    // Remove legacy auto-saved SYSTEM_ANDROID config
                                    repo.clearPrinterConfig()
                                    clearPrinter(clearFromDb = false)
                                }
                            }
                            "WIFI" -> {
                                val json = JSONObject(saved.settingsJson)
                                val ip = json.optString("ipAddress")
                                val port = json.optInt("port", 9100)
                                val name = json.optString("printerName", saved.selectedPrinterId.ifBlank { "Impresora Wi-Fi" })
                                if (ip.isNotBlank()) {
                                    val wifiPrinter = WifiPrinter(ip, port, timeoutMs = 2000)
                                    setPrinter(wifiPrinter, name, printerType = "WIFI", ipAddress = ip, port = port, saveToDb = false)
                                    connect()
                                } else {
                                    clearPrinter(clearFromDb = false)
                                }
                            }
                            else -> {
                                clearPrinter(clearFromDb = false)
                            }
                        }
                    } else {
                        // No printer configured
                        clearPrinter(clearFromDb = false)
                    }
                } catch (_: Exception) {
                    _status.value = PrinterStatus.Disconnected
                }
            }
        }
    }

    fun setPrinter(
        printer: Printer?,
        printerName: String = "Ninguna (Sin configurar)",
        printerType: String? = when (printer) {
            is StandardAndroidPrinter -> "SYSTEM_ANDROID"
            is WifiPrinter -> "WIFI"
            else -> null
        },
        ipAddress: String? = null,
        port: Int? = null,
        saveToDb: Boolean = true
    ) {
        activePrinter = printer
        currentPrinterType = printerType
        _currentPrinterName.value = printerName
        if (printer == null) {
            _status.value = PrinterStatus.Disconnected
        }
        if (saveToDb && repository != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (printer == null || printerType == null) {
                        repository.clearPrinterConfig()
                    } else {
                        val json = JSONObject().apply {
                            put("printerName", printerName)
                            put("ipAddress", ipAddress ?: "")
                            put("port", port ?: 9100)
                            if (printerType == "SYSTEM_ANDROID") {
                                put("userConfigured", true)
                            }
                        }.toString()
                        repository.savePrinterConfig(printerType, printerName, json)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun clearPrinter(clearFromDb: Boolean = true) {
        try {
            activePrinter?.disconnect()
        } catch (_: Exception) {}
        activePrinter = null
        currentPrinterType = null
        _currentPrinterName.value = "Ninguna (Sin configurar)"
        _status.value = PrinterStatus.Disconnected
        if (clearFromDb && repository != null) {
            try {
                repository.clearPrinterConfig()
            } catch (_: Exception) {}
        }
    }

    suspend fun connect() {
        val printer = activePrinter
        if (printer == null) {
            _status.value = PrinterStatus.Disconnected
            return
        }
        try {
            printer.connect()
            _status.value = printer.getStatus()
        } catch (e: Exception) {
            _status.value = PrinterStatus.Error(e.message ?: "Conexión fallida")
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
