package ar.mimbi.Selfie.printing.model

/**
 * Defines page layout and photo slot positions along with printing configuration.
 * Each template represents ONE future A4 PDF page (21.0cm x 29.7cm).
 */
data class PrintTemplate(
    val id: Int,
    val description: String,
    val slots: List<PhotoSlot>,
    val version: Int = 1,
    val quality: String = "Alta (300 DPI)",
    val widthCm: Float = 21.0f,
    val heightCm: Float = 29.7f,
    val marginMm: Float = 0.0f,
    val isFullWidth: Boolean = true
) {
    val slotCount: Int get() = slots.size
}
