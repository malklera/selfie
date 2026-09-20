package ar.mimbi.Selfie

import ar.mimbi.Selfie.data.CameraResolution
import ar.mimbi.Selfie.data.CameraResolutionHelper
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
        assertTrue(res.is9_16)
        assertFalse(res.is3_4)
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
        val res16_9_fhd = CameraResolution(1080, 1920)
        assertTrue(res16_9_fhd.is9_16)
        assertFalse(res16_9_fhd.is3_4)

        val res16_9_hd = CameraResolution(720, 1280)
        assertTrue(res16_9_hd.is9_16)
        assertFalse(res16_9_hd.is3_4)

        val res16_9_4k = CameraResolution(2160, 3840)
        assertTrue(res16_9_4k.is9_16)
        assertFalse(res16_9_4k.is3_4)

        val res4_3_12mp = CameraResolution(3000, 4000)
        assertTrue(res4_3_12mp.is3_4)
        assertFalse(res4_3_12mp.is9_16)

        val res4_3_vga = CameraResolution(480, 640)
        assertTrue(res4_3_vga.is3_4)
        assertFalse(res4_3_vga.is9_16)

        val res1_1 = CameraResolution(1080, 1080)
        assertFalse(res1_1.is9_16)
        assertFalse(res1_1.is3_4)
    }

    @Test
    fun testTotalPixelsCalculation() {
        val res = CameraResolution(1080, 1920)
        assertEquals(1080L * 1920L, res.totalPixels)
    }
}
