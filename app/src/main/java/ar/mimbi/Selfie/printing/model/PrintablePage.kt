package ar.mimbi.Selfie.printing.model

import android.graphics.Bitmap

/**
 * Rendered print output ready to be sent to a printer.
 */
data class PrintablePage(
    val bitmap: Bitmap?,
    val pageSize: PageSize
)
