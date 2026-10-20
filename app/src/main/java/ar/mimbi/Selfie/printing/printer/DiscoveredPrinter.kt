package ar.mimbi.Selfie.printing.printer

import android.hardware.usb.UsbDevice

enum class PrinterConnectionType(
    val label: String,
    val description: String
) {
    WIFI_MDNS("Wi-Fi", "Descubierta por mDNS / DNS-SD"),
    WIFI_SUBNET("Wi-Fi", "Encontrada por escaneo IP de red"),
    USB_HOST("USB OTG", "Conectada por cable USB OTG"),
    BLUETOOTH("Bluetooth", "Conexión Bluetooth SPP / Serie"),
    WIFI_DIRECT("Wi-Fi Direct", "Conexión inalámbrica Wi-Fi Direct")
}

data class DiscoveredPrinter(
    val id: String,
    val name: String,
    val connectionType: PrinterConnectionType,
    val details: String,
    val ipAddress: String? = null,
    val port: Int? = null,
    val bluetoothAddress: String? = null,
    val usbDevice: UsbDevice? = null
)
