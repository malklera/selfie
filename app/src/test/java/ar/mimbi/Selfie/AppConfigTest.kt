package ar.mimbi.Selfie

import ar.mimbi.Selfie.data.AppConfig
import ar.mimbi.Selfie.data.DecorationFit
import ar.mimbi.Selfie.data.FixedAspect
import ar.mimbi.Selfie.data.PreviewFit
import org.junit.Assert.*
import org.junit.Test

class AppConfigTest {

    @Test
    fun testAppConfigDefaultValues() {
        val config = AppConfig()

        assertNull(config.portadaPath)
        assertNull(config.captureBoxPath)
        assertEquals(3, config.countdownSeconds)
        assertEquals("/storage/emulated/0/Pictures/selfie", config.destinationPath)
        assertNull(config.inputResolution)
        assertEquals(PreviewFit.FIT, config.previewFit)
        assertEquals(FixedAspect.R16_9, config.fixedAspect)
        assertEquals(DecorationFit.STRETCH, config.decorationFit)
        assertEquals(0.35, config.lastSavedBytesPerPixel, 0.0001)
        assertEquals(0, config.savedSampleCount)
    }

    @Test
    fun testPictureResolutionBackwardsCompatibilityAlias() {
        val config = AppConfig(inputResolution = "4032x3024")

        @Suppress("DEPRECATION")
        assertEquals("4032x3024", config.pictureResolution)
        assertEquals("4032x3024", config.inputResolution)
    }

    @Test
    fun testEnumValues() {
        assertEquals("FIT", PreviewFit.FIT.name)
        assertEquals("FILL", PreviewFit.FILL.name)
        assertEquals("FIXED_ASPECT", PreviewFit.FIXED_ASPECT.name)

        assertEquals("R4_3", FixedAspect.R4_3.name)
        assertEquals("R16_9", FixedAspect.R16_9.name)

        assertEquals("STRETCH", DecorationFit.STRETCH.name)
        assertEquals("COVER", DecorationFit.COVER.name)
    }
}
