package ar.mimbi.selfie.printing.model

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
    val colorMode: String = "Color",
    val widthCm: Float = 21.0f,
    val heightCm: Float = 29.7f,
    val pageType: String = "A4"
) {
    val slotCount: Int get() = slots.size
}
