package ar.mimbi.Selfie.data

data class AppConfig(
    val portadaPath: String? = null,
    val captureBoxPath: String? = null,
    val countdownSeconds: Int = 3,
    val destinationPath: String = "/storage/emulated/0/Pictures/selfie",
    val pictureResolution: String? = null,
    val showPrintButton: Boolean = false,
    val maxPrintCount: Int = 0,
    val printCount: Int = 0,
    val printMode: String = "mode_full_width_single"
)
