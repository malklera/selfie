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

    val is9_16: Boolean
        get() {
            val maxDim = maxOf(width, height).toDouble()
            val minDim = minOf(width, height).toDouble()
            return if (minDim > 0) abs(maxDim / minDim - 16.0 / 9.0) < 0.05 else false
        }

    val is3_4: Boolean
        get() {
            val maxDim = maxOf(width, height).toDouble()
            val minDim = minOf(width, height).toDouble()
            return if (minDim > 0) abs(maxDim / minDim - 4.0 / 3.0) < 0.05 else false
        }
}

object CameraResolutionHelper {

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

            val list9_16 = mutableListOf<CameraResolution>()
            val list3_4 = mutableListOf<CameraResolution>()

            sizes.map { size ->
                val portraitW = minOf(size.width, size.height)
                val portraitH = maxOf(size.width, size.height)
                CameraResolution(portraitW, portraitH)
            }.distinctBy { "${it.width}x${it.height}" }.forEach { res ->
                if (res.is9_16) {
                    list9_16.add(res)
                } else if (res.is3_4) {
                    list3_4.add(res)
                }
            }

            list9_16.sortByDescending { it.totalPixels }
            list3_4.sortByDescending { it.totalPixels }

            return Pair(list9_16, list3_4)
        } catch (e: Exception) {
            ErrorLogger.log("Error consultando resoluciones de cámara: ${e.message}")
            return Pair(emptyList(), emptyList())
        }
    }

    fun getDefaultResolution(context: Context): CameraResolution? {
        val (list9_16, list3_4) = getSupportedResolutions(context)
        return list9_16.firstOrNull() ?: list3_4.firstOrNull()
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
