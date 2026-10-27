package ar.mimbi.Selfie

import ar.mimbi.Selfie.data.FixedAspect
import ar.mimbi.Selfie.data.ImageGeometryHelper
import ar.mimbi.Selfie.data.PreviewFit
import org.junit.Assert.*
import org.junit.Test

class ImageGeometryHelperTest {

    @Test
    fun testCoverCrop_4_3_to_16_9() {
        // Landscape 4032x3024 (4:3) cropped to 16:9 (aspect ~ 1.7777778)
        val crop = ImageGeometryHelper.coverCrop(4032, 3024, 16.0f / 9.0f)
        assertEquals(0, crop.left)
        assertEquals(378, crop.top)
        assertEquals(4032, crop.right)
        assertEquals(2646, crop.bottom)
        assertEquals(4032, crop.width)
        assertEquals(2268, crop.height)
    }

    @Test
    fun testCoverCrop_4_3_to_screen_aspect() {
        // Landscape 4032x3024 (4:3) cropped to screen aspect 1.6 (e.g., 1920x1200)
        val crop = ImageGeometryHelper.coverCrop(4032, 3024, 1.6f)
        assertEquals(0, crop.left)
        assertEquals(252, crop.top)
        assertEquals(4032, crop.right)
        assertEquals(2772, crop.bottom)
        assertEquals(4032, crop.width)
        assertEquals(2520, crop.height)
    }

    @Test
    fun testCoverCrop_equal_aspect() {
        val crop = ImageGeometryHelper.coverCrop(1920, 1080, 16.0f / 9.0f)
        assertEquals(0, crop.left)
        assertEquals(0, crop.top)
        assertEquals(1920, crop.right)
        assertEquals(1080, crop.bottom)
    }

    @Test
    fun testCoverCrop_odd_sizes() {
        val crop = ImageGeometryHelper.coverCrop(1001, 1001, 16.0f / 9.0f)
        assertEquals(0, crop.left)
        assertEquals(1001, crop.width)
        assertTrue(crop.height <= 1001)
    }

    @Test
    fun testFitSize_container_wider() {
        // Container 1920x1080 (16:9 ~ 1.777), Aspect 4:3 (~1.333) -> container is wider
        val size = ImageGeometryHelper.fitSize(1920f, 1080f, 4.0f / 3.0f)
        assertEquals(1440f, size.width, 0.01f)
        assertEquals(1080f, size.height, 0.01f)
    }

    @Test
    fun testFitSize_container_taller() {
        // Container 1080x1920 (9:16 ~ 0.5625), Aspect 4:3 (~1.333) -> container is taller
        val size = ImageGeometryHelper.fitSize(1080f, 1920f, 4.0f / 3.0f)
        assertEquals(1080f, size.width, 0.01f)
        assertEquals(810f, size.height, 0.01f)
    }

    @Test
    fun testCalculateOutputRegion_mode_FIT() {
        val region = ImageGeometryHelper.calculateOutputRegion(
            frameW = 4032,
            frameH = 3024,
            previewFit = PreviewFit.FIT,
            fixedAspect = FixedAspect.R16_9,
            screenAspect = 1.6f
        )
        assertEquals(4032, region.width)
        assertEquals(3024, region.height)
        assertEquals(4032.0f / 3024.0f, region.aspect, 0.001f)
    }

    @Test
    fun testCalculateOutputRegion_mode_FILL() {
        val region = ImageGeometryHelper.calculateOutputRegion(
            frameW = 4032,
            frameH = 3024,
            previewFit = PreviewFit.FILL,
            fixedAspect = FixedAspect.R16_9,
            screenAspect = 1.6f
        )
        assertEquals(4032, region.width)
        assertEquals(2520, region.height)
        assertEquals(1.6f, region.aspect, 0.001f)
    }

    @Test
    fun testCalculateOutputRegion_mode_FIXED_ASPECT() {
        val region16_9 = ImageGeometryHelper.calculateOutputRegion(
            frameW = 4032,
            frameH = 3024,
            previewFit = PreviewFit.FIXED_ASPECT,
            fixedAspect = FixedAspect.R16_9,
            screenAspect = 1.6f
        )
        assertEquals(4032, region16_9.width)
        assertEquals(2268, region16_9.height)
        assertEquals(16.0f / 9.0f, region16_9.aspect, 0.001f)

        val region4_3 = ImageGeometryHelper.calculateOutputRegion(
            frameW = 4032,
            frameH = 3024,
            previewFit = PreviewFit.FIXED_ASPECT,
            fixedAspect = FixedAspect.R4_3,
            screenAspect = 1.6f
        )
        assertEquals(4032, region4_3.width)
        assertEquals(3024, region4_3.height)
        assertEquals(4.0f / 3.0f, region4_3.aspect, 0.001f)
    }
}
