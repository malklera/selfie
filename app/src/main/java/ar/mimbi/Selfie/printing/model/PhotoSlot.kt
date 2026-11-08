package ar.mimbi.Selfie.printing.model

/**
 * Represents a photo position on a print page using normalized coordinates (0.0 .. 1.0).
 *
 * @param left Top-left X position (0.0 to 1.0)
 * @param top Top-left Y position (0.0 to 1.0)
 * @param width Slot width ratio (0.0 to 1.0)
 * @param height Slot height ratio (0.0 to 1.0)
 */
data class PhotoSlot(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    init {
        require(left in 0.0f..1.0f) { "left must be in range 0.0..1.0, was $left" }
        require(top in 0.0f..1.0f) { "top must be in range 0.0..1.0, was $top" }
        require(width > 0.0f && left + width <= 1.001f) { "width out of bounds: left=$left, width=$width" }
        require(height > 0.0f && top + height <= 1.001f) { "height out of bounds: top=$top, height=$height" }
    }
}
