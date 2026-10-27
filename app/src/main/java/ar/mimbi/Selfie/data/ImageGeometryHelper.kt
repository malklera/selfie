package ar.mimbi.Selfie.data

import android.graphics.Rect
import kotlin.math.roundToInt

data class CropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    fun toAndroidRect(): Rect = Rect(left, top, right, bottom)
}

data class ImageSize(
    val width: Float,
    val height: Float
)

data class OutputRegion(
    val cropRect: CropRect
) {
    val width: Int get() = cropRect.width
    val height: Int get() = cropRect.height
    val aspect: Float get() = if (height > 0) width.toFloat() / height.toFloat() else 1.0f
    val totalPixels: Long get() = width.toLong() * height.toLong()
}

object ImageGeometryHelper {

    /**
     * Center crops a frame of [frameW] x [frameH] to match [targetAspect] without scaling.
     */
    fun coverCrop(frameW: Int, frameH: Int, targetAspect: Float): CropRect {
        if (frameW <= 0 || frameH <= 0 || targetAspect <= 0f) {
            return CropRect(0, 0, frameW.coerceAtLeast(0), frameH.coerceAtLeast(0))
        }

        val frameAspect = frameW.toFloat() / frameH.toFloat()

        return if (frameAspect > targetAspect) {
            // Frame is wider than target aspect ratio -> crop width
            val cropW = (frameH * targetAspect).roundToInt().coerceIn(1, frameW)
            val x = (frameW - cropW) / 2
            CropRect(x, 0, x + cropW, frameH)
        } else if (frameAspect < targetAspect) {
            // Frame is taller than target aspect ratio -> crop height
            val cropH = (frameW / targetAspect).roundToInt().coerceIn(1, frameH)
            val y = (frameH - cropH) / 2
            CropRect(0, y, frameW, y + cropH)
        } else {
            CropRect(0, 0, frameW, frameH)
        }
    }

    /**
     * Computes fitting dimensions inside a container preserving aspect ratio.
     */
    fun fitSize(containerW: Float, containerH: Float, aspect: Float): ImageSize {
        if (containerW <= 0f || containerH <= 0f || aspect <= 0f) {
            return ImageSize(0f, 0f)
        }

        val containerAspect = containerW / containerH

        return if (containerAspect > aspect) {
            ImageSize(containerH * aspect, containerH)
        } else {
            ImageSize(containerW, containerW / aspect)
        }
    }

    /**
     * Calculates the output region R for the captured frame based on the preview fit mode.
     */
    fun calculateOutputRegion(
        frameW: Int,
        frameH: Int,
        previewFit: PreviewFit,
        fixedAspect: FixedAspect,
        screenAspect: Float
    ): OutputRegion {
        if (frameW <= 0 || frameH <= 0) {
            return OutputRegion(CropRect(0, 0, 0, 0))
        }

        val cropRect = when (previewFit) {
            PreviewFit.FIT -> {
                CropRect(0, 0, frameW, frameH)
            }
            PreviewFit.FILL -> {
                coverCrop(frameW, frameH, screenAspect)
            }
            PreviewFit.FIXED_ASPECT -> {
                val isLandscape = frameW >= frameH
                val targetAspect = when (fixedAspect) {
                    FixedAspect.R4_3 -> if (isLandscape) 4.0f / 3.0f else 3.0f / 4.0f
                    FixedAspect.R16_9 -> if (isLandscape) 16.0f / 9.0f else 9.0f / 16.0f
                }
                coverCrop(frameW, frameH, targetAspect)
            }
        }

        return OutputRegion(cropRect)
    }
}
