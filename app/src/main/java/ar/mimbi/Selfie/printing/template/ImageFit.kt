package ar.mimbi.Selfie.printing.template

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.roundToInt

data class FittedBitmapResult(
    val scaledWidth: Int,
    val scaledHeight: Int,
    val offsetX: Int,
    val offsetY: Int
)

/**
 * Image aspect-preserving FIT utility functions.
 */
object ImageFit {

    /**
     * Calculates scaled dimensions and offsets to fit an image inside a slot
     * without cropping or distortion, preserving aspect ratio.
     */
    fun calculateFit(imageWidth: Int, imageHeight: Int, slotWidth: Int, slotHeight: Int): FittedBitmapResult {
        if (imageWidth <= 0 || imageHeight <= 0 || slotWidth <= 0 || slotHeight <= 0) {
            return FittedBitmapResult(0, 0, 0, 0)
        }
        val scale = min(slotWidth.toFloat() / imageWidth, slotHeight.toFloat() / imageHeight)
        val scaledWidth = (imageWidth * scale).roundToInt()
        val scaledHeight = (imageHeight * scale).roundToInt()
        val offsetX = (slotWidth - scaledWidth) / 2
        val offsetY = (slotHeight - scaledHeight) / 2
        return FittedBitmapResult(scaledWidth, scaledHeight, offsetX, offsetY)
    }

    /**
     * Draws [bitmap] centered inside the specified [slotRect] on [canvas] using an aspect-preserving FIT.
     * Leaves unused slot area blank.
     */
    fun drawFittedBitmap(canvas: Canvas, bitmap: Bitmap, slotRect: Rect, paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) {
        val fit = calculateFit(bitmap.width, bitmap.height, slotRect.width(), slotRect.height())
        val destLeft = slotRect.left + fit.offsetX
        val destTop = slotRect.top + fit.offsetY
        val destRect = Rect(
            destLeft,
            destTop,
            destLeft + fit.scaledWidth,
            destTop + fit.scaledHeight
        )
        val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
        canvas.drawBitmap(bitmap, srcRect, destRect, paint)
    }
}
