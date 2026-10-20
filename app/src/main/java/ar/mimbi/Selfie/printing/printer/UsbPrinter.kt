package ar.mimbi.Selfie.printing.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.hardware.usb.*
import ar.mimbi.Selfie.printing.model.PrintablePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Standard USB Host / OTG Printer implementation.
 */
class UsbPrinter(
    private val context: Context,
    val deviceName: String
) : Printer {

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Disconnected)
    val statusFlow: StateFlow<PrinterStatus> = _status.asStateFlow()

    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var usbEndpointOut: UsbEndpoint? = null

    var isConnected: Boolean = false
        private set

    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            try {
                _status.value = PrinterStatus.Printing
                val deviceList = usbManager.deviceList
                val device = deviceList.values.firstOrNull { it.deviceName == deviceName || it.deviceId.toString() == deviceName }
                    ?: throw IllegalStateException("Dispositivo USB no encontrado: $deviceName")

                if (!usbManager.hasPermission(device)) {
                    throw IllegalStateException("Sin permiso de acceso al dispositivo USB")
                }

                var targetInterface: UsbInterface? = null
                var targetEndpoint: UsbEndpoint? = null

                // Search for USB Printer interface (Class 7)
                for (i in 0 until device.interfaceCount) {
                    val iface = device.getInterface(i)
                    if (iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER || device.deviceClass == UsbConstants.USB_CLASS_PRINTER) {
                        for (j in 0 until iface.endpointCount) {
                            val ep = iface.getEndpoint(j)
                            if (ep.direction == UsbConstants.USB_DIR_OUT) {
                                targetInterface = iface
                                targetEndpoint = ep
                                break
                            }
                        }
                    }
                    if (targetEndpoint != null) break
                }

                // Fallback to first available OUT endpoint if specific class is omitted by vendor
                if (targetEndpoint == null && device.interfaceCount > 0) {
                    val iface = device.getInterface(0)
                    for (j in 0 until iface.endpointCount) {
                        val ep = iface.getEndpoint(j)
                        if (ep.direction == UsbConstants.USB_DIR_OUT) {
                            targetInterface = iface
                            targetEndpoint = ep
                            break
                        }
                    }
                }

                val iface = targetInterface ?: throw IllegalStateException("Interfaz USB de impresión no encontrada")
                val epOut = targetEndpoint ?: throw IllegalStateException("Endpoint USB de salida no encontrado")

                val conn = usbManager.openDevice(device)
                    ?: throw IllegalStateException("No se pudo abrir conexión USB")

                conn.claimInterface(iface, true)

                usbConnection = conn
                usbInterface = iface
                usbEndpointOut = epOut
                isConnected = true
                _status.value = PrinterStatus.Ready
            } catch (e: Exception) {
                isConnected = false
                disconnectInternal()
                _status.value = PrinterStatus.Error(e.message ?: "Falló la conexión USB")
                throw e
            }
        }
    }

    override suspend fun getStatus(): PrinterStatus {
        return if (isConnected && usbConnection != null) {
            PrinterStatus.Ready
        } else {
            PrinterStatus.Disconnected
        }
    }

    override suspend fun print(page: PrintablePage) {
        val bitmap = page.bitmap ?: throw IllegalArgumentException("La página no contiene imagen para imprimir")
        if (!isConnected || usbConnection == null || usbEndpointOut == null) {
            connect()
        }
        val conn = usbConnection ?: throw IllegalStateException("Conexión USB nula")
        val epOut = usbEndpointOut ?: throw IllegalStateException("Endpoint USB nulo")

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

                // Transfer data in chunks
                val chunkSize = 16384
                var offset = 0
                while (offset < payload.size) {
                    val length = minOf(chunkSize, payload.size - offset)
                    val chunk = payload.copyOfRange(offset, offset + length)
                    val bytesTransferred = conn.bulkTransfer(epOut, chunk, length, 5000)
                    if (bytesTransferred < 0) {
                        throw IllegalStateException("Error de transferencia de datos por USB")
                    }
                    offset += bytesTransferred
                }

                _status.value = PrinterStatus.Ready
            } catch (e: Exception) {
                _status.value = PrinterStatus.Error(e.message ?: "Falló la impresión por USB")
                throw e
            }
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            disconnectInternal()
            _status.value = PrinterStatus.Disconnected
        }
    }

    private fun disconnectInternal() {
        try {
            usbInterface?.let { usbConnection?.releaseInterface(it) }
            usbConnection?.close()
        } catch (_: Exception) {}
        usbConnection = null
        usbInterface = null
        usbEndpointOut = null
        isConnected = false
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
