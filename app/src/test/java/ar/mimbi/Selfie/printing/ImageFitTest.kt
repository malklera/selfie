package ar.mimbi.Selfie.printing

import ar.mimbi.Selfie.printing.template.ImageFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageFitTest {

    @Test
    fun testSameAspectRatioFit() {
        // Square image 1000x1000 into square slot 500x500
        val fit = ImageFit.calculateFit(1000, 1000, 500, 500)
        assertEquals(500, fit.scaledWidth)
        assertEquals(500, fit.scaledHeight)
        assertEquals(0, fit.offsetX)
        assertEquals(0, fit.offsetY)
    }

    @Test
    fun testLandscapeInPortraitSlot() {
        // Landscape image 1200x800 into portrait slot 600x1000
        val fit = ImageFit.calculateFit(1200, 800, 600, 1000)
        assertEquals(600, fit.scaledWidth)
        assertEquals(400, fit.scaledHeight) // 600 * (800/1200) = 400
        assertEquals(0, fit.offsetX)
        assertEquals(300, fit.offsetY) // (1000 - 400) / 2 = 300
    }

    @Test
    fun testPortraitInLandscapeSlot() {
        // Portrait image 800x1200 into landscape slot 1000x600
        val fit = ImageFit.calculateFit(800, 1200, 1000, 600)
        assertEquals(400, fit.scaledWidth) // 600 * (800/1200) = 400
        assertEquals(600, fit.scaledHeight)
        assertEquals(300, fit.offsetX) // (1000 - 400) / 2 = 300
        assertEquals(0, fit.offsetY)
    }

    @Test
    fun testAspectPreservationNoDistortion() {
        val imgWidth = 4000
        val imgHeight = 3000
        val fit = ImageFit.calculateFit(imgWidth, imgHeight, 800, 1200)

        val originalRatio = imgWidth.toFloat() / imgHeight.toFloat()
        val fitRatio = fit.scaledWidth.toFloat() / fit.scaledHeight.toFloat()

        assertEquals(originalRatio, fitRatio, 0.01f)
        assertTrue(fit.scaledWidth <= 800)
        assertTrue(fit.scaledHeight <= 1200)
    }
}
