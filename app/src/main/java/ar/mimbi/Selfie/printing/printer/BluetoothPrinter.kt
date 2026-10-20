package ar.mimbi.Selfie.printing.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
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
import java.util.UUID

/**
 * Standard Bluetooth SPP Printer implementation.
 */
class BluetoothPrinter(
    val macAddress: String,
    val printerName: String = "Impresora Bluetooth"
) : Printer {

    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Disconnected)
    val statusFlow: StateFlow<PrinterStatus> = _status.asStateFlow()

    private var socket: BluetoothSocket? = null
    var isConnected: Boolean = false
        private set

    // Standard SPP (Serial Port Profile) UUID for Bluetooth printers
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            try {
                _status.value = PrinterStatus.Printing
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    ?: throw IllegalStateException("El dispositivo no soporta Bluetooth")

                if (!bluetoothAdapter.isEnabled) {
                    throw IllegalStateException("El Bluetooth está desactivado")
                }

                val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(macAddress)
                    ?: throw IllegalStateException("Dispositivo Bluetooth no encontrado: $macAddress")

                bluetoothAdapter.cancelDiscovery()

                val s = device.createRfcommSocketToServiceRecord(sppUuid)
                s.connect()
                socket = s
                isConnected = true
                _status.value = PrinterStatus.Ready
            } catch (e: Exception) {
                isConnected = false
                socket?.close()
                socket = null
                _status.value = PrinterStatus.Error(e.message ?: "Falló la conexión Bluetooth")
                throw e
            }
        }
    }

    override suspend fun getStatus(): PrinterStatus {
        val connected = isConnected && socket?.isConnected == true
        return if (connected) {
            PrinterStatus.Ready
        } else {
            PrinterStatus.Disconnected
        }
    }

    override suspend fun print(page: PrintablePage) {
        val bitmap = page.bitmap ?: throw IllegalArgumentException("La página no contiene imagen para imprimir")
        if (!isConnected || socket == null || socket?.isConnected != true) {
            connect()
        }
        val currentSocket = socket ?: throw IllegalStateException("Socket Bluetooth nulo")

        withContext(Dispatchers.IO) {
            _status.value = PrinterStatus.Printing
            try {
                val pdfBytes = convertBitmapToPdf(bitmap)

                val pjlStart = "@PJL JOB\n@PJL ENTER LANGUAGE = PDF\n".toByteArray(Charsets.US_ASCII)
                val pjlEnd = "\n@PJL EOJ\n".toByteArray(Charsets.US_ASCII)

                val payload = ByteArray(pjlStart.size + pdfBytes.size + pjlEnd.size)
                System.arraycopy(pjlStart, 0, payload, 0, pjlStart.size)
                System.arraycopy(pdfBytes, 0, payload, pjlStart.size, pdfBytes.size)
                System.arraycopy(pjlEnd, 0, payload, pjlStart.size + pdfBytes.size, pjlEnd.size)

                val outputStream = currentSocket.outputStream
                outputStream.write(payload)
                outputStream.flush()

                _status.value = PrinterStatus.Ready
            } catch (e: Exception) {
                _status.value = PrinterStatus.Error(e.message ?: "Falló la impresión por Bluetooth")
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
