package ar.mimbi.Selfie.printing.printer

import android.graphics.Bitmap
import ar.mimbi.Selfie.printing.model.PrintablePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket

class WifiPrinter(
    val ipAddress: String,
    val port: Int = 9100,
    val timeoutMs: Int = 5000
) : Printer {

    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Disconnected)
    val statusFlow: StateFlow<PrinterStatus> = _status.asStateFlow()

    private var socket: Socket? = null
    var isConnected: Boolean = false
        private set

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            try {
                _status.value = PrinterStatus.Printing
                val s = Socket()
                s.connect(InetSocketAddress(ipAddress, port), timeoutMs)
                socket = s
                isConnected = true
                _status.value = PrinterStatus.Ready
            } catch (e: Exception) {
                isConnected = false
                socket = null
                _status.value = PrinterStatus.Error(e.message ?: "Connection failed")
                throw e
            }
        }
    }

    override suspend fun getStatus(): PrinterStatus {
        val connected = isConnected && socket?.isConnected == true && socket?.isClosed == false
        return if (connected) {
            PrinterStatus.Ready
        } else {
            PrinterStatus.Disconnected
        }
    }

    override suspend fun print(page: PrintablePage) {
        if (!isConnected || socket == null || socket?.isConnected != true || socket?.isClosed == true) {
            connect()
        }
        val bitmap = page.bitmap ?: throw IllegalArgumentException("Page bitmap is null")
        val currentSocket = socket ?: throw IllegalStateException("Socket is null")

        withContext(Dispatchers.IO) {
            _status.value = PrinterStatus.Printing
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val bytes = stream.toByteArray()

                val outputStream = currentSocket.getOutputStream()
                outputStream.write(bytes)
                outputStream.flush()

                _status.value = PrinterStatus.Ready
            } catch (e: Exception) {
                _status.value = PrinterStatus.Error(e.message ?: "Print failed")
                throw e
            }
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                socket?.close()
            } catch (_: Exception) {}
            socket = null
            isConnected = false
            _status.value = PrinterStatus.Disconnected
        }
    }
}
