package ar.mimbi.selfie.printing.template

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.core.graphics.withClip

data class FittedBitmapResult(
    val scaledWidth: Int,
    val scaledHeight: Int,
    val offsetX: Int,
    val offsetY: Int,
    val isRotated: Boolean = false
)

/**
 * Image aspect-preserving FIT utility functions with orientation matching.
 */
object ImageFit {

    /**
     * Rotates [source] by [degrees] clockwise.
     */
    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Calculates scaled dimensions and offsets to fit the whole image inside a slot,
     * preserving aspect ratio and never cropping.
     * If [autoRotate] is true and the image orientation (portrait vs landscape)
     * conflicts with the slot orientation, auto-rotates 90 degrees to maximize slot usage.
     */
    fun calculateFit(
        imageWidth: Int,
        imageHeight: Int,
        slotWidth: Int,
        slotHeight: Int,
        autoRotate: Boolean = false
    ): FittedBitmapResult {
        if (imageWidth <= 0 || imageHeight <= 0 || slotWidth <= 0 || slotHeight <= 0) {
            return FittedBitmapResult(0, 0, 0, 0)
        }

        val isImagePortrait = imageHeight > imageWidth
        val isSlotLandscape = slotWidth > slotHeight
        val isImageLandscape = imageWidth > imageHeight
        val isSlotPortrait = slotHeight > slotWidth

        val shouldRotate = autoRotate && ((isImagePortrait && isSlotLandscape) || (isImageLandscape && isSlotPortrait))

        val effectiveWidth = if (shouldRotate) imageHeight else imageWidth
        val effectiveHeight = if (shouldRotate) imageWidth else imageHeight

        val scale = min(slotWidth.toFloat() / effectiveWidth, slotHeight.toFloat() / effectiveHeight)

        val scaledWidth = (effectiveWidth * scale).roundToInt()
        val scaledHeight = (effectiveHeight * scale).roundToInt()
        val offsetX = (slotWidth - scaledWidth) / 2
        val offsetY = (slotHeight - scaledHeight) / 2

        return FittedBitmapResult(scaledWidth, scaledHeight, offsetX, offsetY, isRotated = shouldRotate)
    }

    /**
     * Draws [bitmap] centered inside the specified [slotRect] on [canvas], preserving
     * aspect ratio and never cropping.
     * Auto-rotates 90 degrees if photo orientation differs from slot orientation.
     */
    fun drawFittedBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        slotRect: Rect,
        paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    ) {
        val fit = calculateFit(
            bitmap.width,
            bitmap.height,
            slotRect.width(),
            slotRect.height(),
            autoRotate = true
        )

        var bmpToDraw = bitmap
        var tempRotatedBmp: Bitmap? = null

        if (fit.isRotated) {
            tempRotatedBmp = rotateBitmap(bitmap, 90f)
            bmpToDraw = tempRotatedBmp
        }

        val destLeft = slotRect.left + fit.offsetX
        val destTop = slotRect.top + fit.offsetY
        val destRect = Rect(
            destLeft,
            destTop,
            destLeft + fit.scaledWidth,
            destTop + fit.scaledHeight
        )

        canvas.withClip(slotRect) {
            val srcRect = Rect(0, 0, bmpToDraw.width, bmpToDraw.height)
            canvas.drawBitmap(bmpToDraw, srcRect, destRect, paint)

        }

        tempRotatedBmp?.recycle()
    }
}
