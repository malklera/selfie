package ar.mimbi.selfie

import ar.mimbi.selfie.data.CameraResolution
import ar.mimbi.selfie.data.CameraResolutionHelper
import org.junit.Assert.*
import org.junit.Test

class CameraResolutionHelperTest {

    @Test
    fun testParseResolution_valid() {
        val res = CameraResolutionHelper.parseResolution("1080x1920")
        assertNotNull(res)
        assertEquals(1080, res!!.width)
        assertEquals(1920, res.height)
        assertEquals("1080 x 1920", res.displayText)
        assertEquals("1080x1920", res.key)
        assertTrue(res.is9To16)
        assertFalse(res.is3To4)
    }

    @Test
    fun testParseResolution_withUppercaseX() {
        val res = CameraResolutionHelper.parseResolution("1920X1080")
        assertNotNull(res)
        assertEquals(1080, res!!.width)
        assertEquals(1920, res.height)
    }

    @Test
    fun testParseResolution_invalid() {
        assertNull(CameraResolutionHelper.parseResolution(null))
        assertNull(CameraResolutionHelper.parseResolution(""))
        assertNull(CameraResolutionHelper.parseResolution("invalid"))
        assertNull(CameraResolutionHelper.parseResolution("1080xabc"))
        assertNull(CameraResolutionHelper.parseResolution("0x1920"))
    }

    @Test
    fun testAspectRatioDetection() {
        val res16to9_fhd = CameraResolution(1080, 1920)
        assertTrue(res16to9_fhd.is9To16)
        assertFalse(res16to9_fhd.is3To4)

        val res16to9_hd = CameraResolution(720, 1280)
        assertTrue(res16to9_hd.is9To16)
        assertFalse(res16to9_hd.is3To4)

        val res16to9_4k = CameraResolution(2160, 3840)
        assertTrue(res16to9_4k.is9To16)
        assertFalse(res16to9_4k.is3To4)

        val res4to3_12mp = CameraResolution(3000, 4000)
        assertTrue(res4to3_12mp.is3To4)
        assertFalse(res4to3_12mp.is9To16)

        val res4to3VGA = CameraResolution(480, 640)
        assertTrue(res4to3VGA.is3To4)
        assertFalse(res4to3VGA.is9To16)

        val res1to1 = CameraResolution(1080, 1080)
        assertFalse(res1to1.is9To16)
        assertFalse(res1to1.is3To4)
    }

    @Test
    fun testTotalPixelsCalculation() {
        val res = CameraResolution(1080, 1920)
        assertEquals(1080L * 1920L, res.totalPixels)
    }

    @Test
    fun testNativeAspectFiltering() {
        val sizes = listOf(
            CameraResolution(3024, 4032), // 12 MP (4:3) -> largest
            CameraResolution(2160, 3840), // 4K (16:9) -> non-native aspect
            CameraResolution(2448, 3264), // 8 MP (4:3)
            CameraResolution(1080, 1920), // FHD (16:9) -> non-native aspect
            CameraResolution(480, 640)    // VGA (4:3)
        )

        val nativeSizes = CameraResolutionHelper.filterNativeSizes(sizes)

        // Native aspect ratio should be 4032 / 3024 = 1.3333334
        assertEquals(4032.0f / 3024.0f, nativeSizes.aspect, 0.01f)

        // Only 4:3 resolutions should remain
        assertEquals(3, nativeSizes.sizes.size)
        assertEquals("3024x4032", nativeSizes.sizes[0].key)
        assertEquals("2448x3264", nativeSizes.sizes[1].key)
        assertEquals("480x640", nativeSizes.sizes[2].key)
    }

    @Test
    fun testEstimateFileSize() {
        val pixels = 12_192_768L // 4032 x 3024
        val estimatedMb = CameraResolutionHelper.estimateFileSize(pixels, 0.35)
        assertEquals(4.069, estimatedMb, 0.01)

        assertEquals(0.0, CameraResolutionHelper.estimateFileSize(0, 0.35), 0.001)
        assertEquals(0.0, CameraResolutionHelper.estimateFileSize(-100, 0.35), 0.001)
    }
}
