package ar.mimbi.Selfie.data

enum class PreviewFit { FIT, FILL, FIXED_ASPECT }
enum class FixedAspect { R4_3, R16_9, R3_4, R9_16 }
enum class DecorationFit { STRETCH, COVER }
enum class AppOrientation { PORTRAIT, LANDSCAPE, AUTO }

data class AppConfig(
    val frontPath: String? = null,
    val captureBoxPath: String? = null,
    val countdownSeconds: Int = 3,
    val destinationPath: String = "/storage/emulated/0/Pictures/selfie",
    val inputResolution: String? = null,
    val previewFit: PreviewFit = PreviewFit.FIT,
    val fixedAspect: FixedAspect = FixedAspect.R9_16,
    val decorationFit: DecorationFit = DecorationFit.STRETCH,
    val appOrientation: AppOrientation = AppOrientation.AUTO,
    val showPrintButton: Boolean = false,
    val maxPrintCount: Int = 0,
    val printCount: Int = 0
) {
    @Deprecated("Use inputResolution instead", ReplaceWith("inputResolution"))
    val pictureResolution: String?
        get() = inputResolution
}
