package ar.mimbi.Selfie.printing.template

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import ar.mimbi.Selfie.printing.model.PageSize
import ar.mimbi.Selfie.printing.model.PrintTemplate
import ar.mimbi.Selfie.printing.model.PrintablePage
import kotlin.math.roundToInt

interface TemplateRenderer {
    fun render(
        template: PrintTemplate,
        photos: List<Bitmap?>,
        pageSize: PageSize = PageSize()
    ): PrintablePage
}

class DefaultTemplateRenderer : TemplateRenderer {

    override fun render(
        template: PrintTemplate,
        photos: List<Bitmap?>,
        pageSize: PageSize
    ): PrintablePage {
        val pageBitmap = Bitmap.createBitmap(
            pageSize.widthPx,
            pageSize.heightPx,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(pageBitmap)
        // Blank page background (white)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        template.slots.forEachIndexed { index, slot ->
            val slotLeft = (slot.left * pageSize.widthPx).roundToInt()
            val slotTop = (slot.top * pageSize.heightPx).roundToInt()
            val slotWidth = (slot.width * pageSize.widthPx).roundToInt()
            val slotHeight = (slot.height * pageSize.heightPx).roundToInt()

            val slotRect = Rect(slotLeft, slotTop, slotLeft + slotWidth, slotTop + slotHeight)

            val photo = photos.getOrNull(index)
            if (photo != null && !photo.isRecycled) {
                ImageFit.drawFittedBitmap(canvas, photo, slotRect, paint)
            }
            // Unused or null slots remain white (blank)
        }

        return PrintablePage(pageBitmap, pageSize)
    }
}
