package ar.mimbi.Selfie.printing.model

/**
 * Page resolution dimensions in pixels.
 */
data class PageSize(
    val widthPx: Int = 1181,  // e.g. ~10cm @ 300 DPI
    val heightPx: Int = 1772  // e.g. ~15cm @ 300 DPI
)
