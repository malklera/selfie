package ar.mimbi.selfie

import android.os.Environment
import ar.mimbi.selfie.data.AppConfig
import ar.mimbi.selfie.data.AppOrientation
import ar.mimbi.selfie.data.DecorationFit
import ar.mimbi.selfie.data.FixedAspect
import ar.mimbi.selfie.data.PreviewFit
import org.junit.Assert.*
import org.junit.Test

class AppConfigTest {

    @Test
    fun testAppConfigDefaultValues() {
        val config = AppConfig()

        assertNull(config.frontPath)
        assertNull(config.captureBoxPath)
        assertEquals(3, config.countdownSeconds)
        assertEquals(Environment.DIRECTORY_PICTURES + "/selfie", config.destinationPath)
        assertNull(config.inputResolution)
        assertEquals(PreviewFit.FIT, config.previewFit)
        assertEquals(FixedAspect.R9_16, config.fixedAspect)
        assertEquals(DecorationFit.STRETCH, config.decorationFit)
        assertEquals(AppOrientation.AUTO, config.appOrientation)
    }

    @Test
    fun testEnumValues() {
        assertEquals("FIT", PreviewFit.FIT.name)
        assertEquals("FILL", PreviewFit.FILL.name)
        assertEquals("FIXED_ASPECT", PreviewFit.FIXED_ASPECT.name)

        assertEquals("R4_3", FixedAspect.R4_3.name)
        assertEquals("R16_9", FixedAspect.R16_9.name)
        assertEquals("R3_4", FixedAspect.R3_4.name)
        assertEquals("R9_16", FixedAspect.R9_16.name)

        assertEquals("STRETCH", DecorationFit.STRETCH.name)
        assertEquals("COVER", DecorationFit.COVER.name)

        assertEquals("PORTRAIT", AppOrientation.PORTRAIT.name)
        assertEquals("LANDSCAPE", AppOrientation.LANDSCAPE.name)
        assertEquals("AUTO", AppOrientation.AUTO.name)
    }
}
