package ar.mimbi.Selfie.data

data class AppConfig(
    val portadaPath: String? = null,
    val countdownSeconds: Int = 3,
    val destinationPath: String = "/storage/emulated/0/Pictures/selfie"
)
