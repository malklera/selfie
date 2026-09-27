package ar.mimbi.Selfie.printing.model

/**
 * Defines page layout and photo slot positions along with printing configuration (quality, dimensions, margins).
 */
data class PrintTemplate(
    val id: Int,
    val description: String,
    val slots: List<PhotoSlot>,
    val version: Int = 1,
    val quality: String = "Alta (300 DPI)",
    val widthCm: Float = 10.0f,
    val heightCm: Float = 15.0f,
    val marginMm: Float = 0.0f,
    val isFullWidth: Boolean = true
) {
    val slotCount: Int get() = slots.size
}
