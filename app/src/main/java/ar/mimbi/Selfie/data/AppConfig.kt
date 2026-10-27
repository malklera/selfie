package ar.mimbi.Selfie.data

enum class PreviewFit { FIT, FILL, FIXED_ASPECT }
enum class FixedAspect { R4_3, R16_9 }
enum class DecorationFit { STRETCH, COVER }

data class AppConfig(
    val portadaPath: String? = null,
    val captureBoxPath: String? = null,
    val countdownSeconds: Int = 3,
    val destinationPath: String = "/storage/emulated/0/Pictures/selfie",
    val inputResolution: String? = null,
    val previewFit: PreviewFit = PreviewFit.FIT,
    val fixedAspect: FixedAspect = FixedAspect.R16_9,
    val decorationFit: DecorationFit = DecorationFit.STRETCH,
    val lastSavedBytesPerPixel: Double = 0.35,
    val savedSampleCount: Int = 0
) {
    @Deprecated("Use inputResolution instead", ReplaceWith("inputResolution"))
    val pictureResolution: String?
        get() = inputResolution
}
