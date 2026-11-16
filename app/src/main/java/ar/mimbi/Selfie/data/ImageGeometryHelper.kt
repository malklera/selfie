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
                val targetAspect = when (fixedAspect) {
                    FixedAspect.R4_3 -> 4.0f / 3.0f
                    FixedAspect.R16_9 -> 16.0f / 9.0f
                    FixedAspect.R3_4 -> 3.0f / 4.0f
                    FixedAspect.R9_16 -> 9.0f / 16.0f
                }
                coverCrop(frameW, frameH, targetAspect)
            }
        }

        return OutputRegion(cropRect)
    }

    /**
     * Formats width and height into an "n:n" aspect ratio string (e.g., "16:9", "9:16", "4:3", "3:4").
     */
    fun formatAspectRatio(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "0:0"

        val aspect = width.toFloat() / height.toFloat()

        val knownRatios = listOf(
            9f / 16f to "9:16",
            16f / 9f to "16:9",
            3f / 4f to "3:4",
            4f / 3f to "4:3",
            9f / 20f to "9:20",
            20f / 9f to "20:9",
            1f / 2f to "1:2",
            2f / 1f to "2:1",
            5f / 8f to "5:8",
            8f / 5f to "8:5",
            1f / 1f to "1:1",
            3f / 2f to "3:2",
            2f / 3f to "2:3",
            6f / 13f to "6:13",
            13f / 6f to "13:6"
        )

        for ((ratio, label) in knownRatios) {
            if (kotlin.math.abs(aspect - ratio) < 0.02f) {
                return label
            }
        }

        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        val g = gcd(width, height)
        val w = width / g
        val h = height / g
        if (w <= 50 && h <= 50) {
            return "$w:$h"
        }

        var bestW = width
        var bestH = height
        var minDiff = Float.MAX_VALUE
        for (d in 1..20) {
            val n = (aspect * d).roundToInt()
            if (n > 0) {
                val diff = kotlin.math.abs(aspect - n.toFloat() / d)
                if (diff < minDiff) {
                    minDiff = diff
                    bestW = n
                    bestH = d
                }
            }
        }
        val g2 = gcd(bestW, bestH)
        return "${bestW / g2}:${bestH / g2}"
    }
}
