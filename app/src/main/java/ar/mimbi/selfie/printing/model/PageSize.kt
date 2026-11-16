package ar.mimbi.selfie.printing.model

/**
 * Page resolution dimensions in pixels.
 * Default is ONE A4 page at 300 DPI (210mm x 297mm -> 2480px x 3508px).
 */
data class PageSize(
    val widthPx: Int = 2480,  // A4 width @ 300 DPI
    val heightPx: Int = 3508  // A4 height @ 300 DPI
)
