package ar.mimbi.Selfie.printing.printer

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
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
        val bitmap = page.bitmap ?: throw IllegalArgumentException("Page bitmap is null")
        if (!isConnected || socket == null || socket?.isConnected != true || socket?.isClosed == true) {
            connect()
        }
        val currentSocket = socket ?: throw IllegalStateException("Socket is null")

        withContext(Dispatchers.IO) {
            _status.value = PrinterStatus.Printing
            try {
                val pdfBytes = convertBitmapToPdf(bitmap)

                // Wrap PDF in PJL (Printer Job Language) commands for modern network printers (PDF interpreter)
                val pjlStart = "@PJL JOB\n@PJL ENTER LANGUAGE = PDF\n".toByteArray(Charsets.US_ASCII)
                val pjlEnd = "\n@PJL EOJ\n".toByteArray(Charsets.US_ASCII)

                val payload = ByteArray(pjlStart.size + pdfBytes.size + pjlEnd.size)
                System.arraycopy(pjlStart, 0, payload, 0, pjlStart.size)
                System.arraycopy(pdfBytes, 0, payload, pjlStart.size, pdfBytes.size)
                System.arraycopy(pjlEnd, 0, payload, pjlStart.size + pdfBytes.size, pjlEnd.size)

                val outputStream = currentSocket.getOutputStream()
                outputStream.write(payload)
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

    private fun convertBitmapToPdf(bitmap: Bitmap): ByteArray {
        val pdfDocument = PdfDocument()
        // Convert 300 DPI pixel dimensions to standard PDF points (1/72 inch)
        val pdfWidth = (bitmap.width * 72) / 300
        val pdfHeight = (bitmap.height * 72) / 300

        val pageInfo = PdfDocument.PageInfo.Builder(pdfWidth, pdfHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        val canvas = page.canvas
        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        val destRect = Rect(0, 0, pdfWidth, pdfHeight)
        canvas.drawBitmap(bitmap, srcRect, destRect, null)

        pdfDocument.finishPage(page)

        val stream = ByteArrayOutputStream()
        pdfDocument.writeTo(stream)
        pdfDocument.close()
        return stream.toByteArray()
    }
}
