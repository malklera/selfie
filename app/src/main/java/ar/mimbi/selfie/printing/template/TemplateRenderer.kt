package ar.mimbi.selfie.printing.template

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import ar.mimbi.selfie.printing.model.PageSize
import ar.mimbi.selfie.printing.model.PrintTemplate
import ar.mimbi.selfie.printing.model.PrintablePage
import kotlin.math.roundToInt
import androidx.core.graphics.createBitmap

interface TemplateRenderer {
    fun render(
        template: PrintTemplate,
        photos: List<Bitmap?>,
        pageSize: PageSize = PageSize()
    ): PrintablePage

    fun renderToCanvas(
        canvas: Canvas,
        template: PrintTemplate,
        photos: List<Bitmap?>,
        widthPx: Int,
        heightPx: Int
    ) {
        val printable = render(template, photos, PageSize(widthPx, heightPx))
        printable.bitmap?.let { bmp ->
            if (!bmp.isRecycled) {
                val srcRect = Rect(0, 0, bmp.width, bmp.height)
                val dstRect = Rect(0, 0, widthPx, heightPx)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(bmp, srcRect, dstRect, paint)
                bmp.recycle()
            }
        }
    }
}

class DefaultTemplateRenderer : TemplateRenderer {

    override fun renderToCanvas(
        canvas: Canvas,
        template: PrintTemplate,
        photos: List<Bitmap?>,
        widthPx: Int,
        heightPx: Int
    ) {
        // Blank page background (white)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        template.slots.forEachIndexed { index, slot ->
            val slotLeft = (slot.left * widthPx).roundToInt()
            val slotTop = (slot.top * heightPx).roundToInt()
            val slotWidth = (slot.width * widthPx).roundToInt()
            val slotHeight = (slot.height * heightPx).roundToInt()

            val slotRect = Rect(slotLeft, slotTop, slotLeft + slotWidth, slotTop + slotHeight)

            val photo = photos.getOrNull(index)
            if (photo != null && !photo.isRecycled) {
                ImageFit.drawFittedBitmap(
                    canvas = canvas,
                    bitmap = photo,
                    slotRect = slotRect,
                    paint = paint
                )
            }
            // Unused or null slots remain white (blank)
        }
    }

    override fun render(
        template: PrintTemplate,
        photos: List<Bitmap?>,
        pageSize: PageSize
    ): PrintablePage {
        val pageBitmap = createBitmap(pageSize.widthPx, pageSize.heightPx)
        val canvas = Canvas(pageBitmap)
        renderToCanvas(canvas, template, photos, pageSize.widthPx, pageSize.heightPx)
        return PrintablePage(pageBitmap, pageSize)
    }
}
