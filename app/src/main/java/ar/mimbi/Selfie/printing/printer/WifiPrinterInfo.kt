package ar.mimbi.Selfie.printing.printer

data class WifiPrinterInfo(
    val name: String,
    val ipAddress: String,
    val port: Int = 9100,
    val serviceType: String = ""
)
