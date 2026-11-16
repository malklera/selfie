package ar.mimbi.Selfie.data

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlin.math.abs

data class CameraResolution(
    val width: Int,
    val height: Int
) {
    val displayText: String
        get() = "$width x $height"

    val key: String
        get() = "${width}x${height}"

    val totalPixels: Long
        get() = width.toLong() * height.toLong()

    val is9To16: Boolean
        get() {
            val maxDim = maxOf(width, height).toDouble()
            val minDim = minOf(width, height).toDouble()
            return if (minDim > 0) abs(maxDim / minDim - 16.0 / 9.0) < 0.05 else false
        }

    val is3To4: Boolean
        get() {
            val maxDim = maxOf(width, height).toDouble()
            val minDim = minOf(width, height).toDouble()
            return if (minDim > 0) abs(maxDim / minDim - 4.0 / 3.0) < 0.05 else false
        }
}

data class NativeSizes(
    val aspect: Float,
    val sizes: List<CameraResolution>
)

object CameraResolutionHelper {

    internal fun filterNativeSizes(resolutions: List<CameraResolution>): NativeSizes {
        if (resolutions.isEmpty()) return NativeSizes(4.0f / 3.0f, emptyList())

        val maxSize = resolutions.maxByOrNull { it.totalPixels }
            ?: return NativeSizes(4.0f / 3.0f, emptyList())

        val nativeAspect = maxSize.height.toFloat() / maxSize.width.toFloat()

        val filtered = resolutions.filter { res ->
            val resAspect = res.height.toFloat() / res.width.toFloat()
            abs(resAspect - nativeAspect) / nativeAspect <= 0.02f
        }.sortedByDescending { it.totalPixels }

        return NativeSizes(aspect = nativeAspect, sizes = filtered)
    }

    fun getNativeSizes(context: Context): NativeSizes {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return NativeSizes(4.0f / 3.0f, emptyList())

            val cameraIdList = cameraManager.cameraIdList
            if (cameraIdList.isEmpty()) return NativeSizes(4.0f / 3.0f, emptyList())

            var selectedCameraId: String? = null
            for (id in cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    selectedCameraId = id
                    break
                }
            }

            if (selectedCameraId == null && cameraIdList.isNotEmpty()) {
                selectedCameraId = cameraIdList[0]
            }

            if (selectedCameraId == null) return NativeSizes(4.0f / 3.0f, emptyList())

            val characteristics = cameraManager.getCameraCharacteristics(selectedCameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(ImageFormat.JPEG)
                ?: map?.getOutputSizes(SurfaceTexture::class.java)
                ?: emptyArray()

            val cameraResolutions = sizes.map { size ->
                val portraitW = minOf(size.width, size.height)
                val portraitH = maxOf(size.width, size.height)
                CameraResolution(portraitW, portraitH)
            }.distinctBy { "${it.width}x${it.height}" }

            return filterNativeSizes(cameraResolutions)
        } catch (e: Exception) {
            ErrorLogger.log("Error consultando resoluciones nativas de cámara: ${e.message}")
            return NativeSizes(4.0f / 3.0f, emptyList())
        }
    }

    fun getMaxResolution(context: Context): CameraResolution? {
        return getNativeSizes(context).sizes.firstOrNull() ?: getDefaultResolution(context)
    }

    fun estimateFileSize(pixels: Long, bytesPerPixel: Double = 0.35): Double {
        if (pixels <= 0) return 0.0
        val bytes = pixels * bytesPerPixel
        return bytes / (1024.0 * 1024.0)
    }

    fun getSupportedResolutions(context: Context): Pair<List<CameraResolution>, List<CameraResolution>> {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return Pair(emptyList(), emptyList())

            val cameraIdList = cameraManager.cameraIdList
            if (cameraIdList.isEmpty()) return Pair(emptyList(), emptyList())

            var selectedCameraId: String? = null
            for (id in cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    selectedCameraId = id
                    break
                }
            }

            if (selectedCameraId == null && cameraIdList.isNotEmpty()) {
                selectedCameraId = cameraIdList[0]
            }

            if (selectedCameraId == null) return Pair(emptyList(), emptyList())

            val characteristics = cameraManager.getCameraCharacteristics(selectedCameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(ImageFormat.JPEG)
                ?: map?.getOutputSizes(SurfaceTexture::class.java)
                ?: emptyArray()

            val list9To16 = mutableListOf<CameraResolution>()
            val list3To4 = mutableListOf<CameraResolution>()

            sizes.map { size ->
                val portraitW = minOf(size.width, size.height)
                val portraitH = maxOf(size.width, size.height)
                CameraResolution(portraitW, portraitH)
            }.distinctBy { "${it.width}x${it.height}" }.forEach { res ->
                if (res.is9To16) {
                    list9To16.add(res)
                } else if (res.is3To4) {
                    list3To4.add(res)
                }
            }

            list9To16.sortByDescending { it.totalPixels }
            list3To4.sortByDescending { it.totalPixels }

            return Pair(list9To16, list3To4)
        } catch (e: Exception) {
            ErrorLogger.log("Error consultando resoluciones de cámara: ${e.message}")
            return Pair(emptyList(), emptyList())
        }
    }

    fun getDefaultResolution(context: Context): CameraResolution? {
        val (list9To16, list3To4) = getSupportedResolutions(context)
        return list9To16.firstOrNull() ?: list3To4.firstOrNull()
    }

    fun parseResolution(str: String?): CameraResolution? {
        if (str.isNullOrBlank()) return null
        val parts = str.split("x", "X")
        if (parts.size == 2) {
            val w = parts[0].trim().toIntOrNull()
            val h = parts[1].trim().toIntOrNull()
            if (w != null && h != null && w > 0 && h > 0) {
                return CameraResolution(minOf(w, h), maxOf(w, h))
            }
        }
        return null
    }
}
