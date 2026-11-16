package ar.mimbi.selfie

import ar.mimbi.selfie.data.AppConfig
import ar.mimbi.selfie.data.AppOrientation
import ar.mimbi.selfie.data.ConfigSessionState
import org.junit.Assert.*
import org.junit.Test

class ConfigSessionStateTest {

    @Test
    fun testInitialStateHasNoChanges() {
        val initialConfig = AppConfig(
            showPrintButton = false,
            maxPrintCount = 10,
            printCount = 2
        )
        val session = ConfigSessionState(initialConfig, initialTemplateId = 1, defaultResKey = null)

        assertFalse(session.hasChanges())
    }

    @Test
    fun testMainConfigChangesDetected() {
        val initialConfig = AppConfig()
        val session = ConfigSessionState(initialConfig, initialTemplateId = 1, defaultResKey = null)

        session.countdownSeconds = "10"
        assertTrue(session.hasChanges())

        session.countdownSeconds = "3"
        assertFalse(session.hasChanges())

        session.appOrientation = AppOrientation.LANDSCAPE
        assertTrue(session.hasChanges())
    }

    @Test
    fun testPrintConfigChangesDetectedAndSavedInAppConfig() {
        val initialConfig = AppConfig(showPrintButton = false, maxPrintCount = 5, printCount = 3)
        val session = ConfigSessionState(initialConfig, initialTemplateId = 1, defaultResKey = null)

        session.showPrintButton = true
        assertTrue(session.hasChanges())

        session.maxPrintCount = "20"
        assertTrue(session.hasChanges())

        session.printCount = 0
        session.wasResetToZero = true
        assertTrue(session.hasChanges())

        val updatedConfig = session.toAppConfig()
        assertTrue(updatedConfig.showPrintButton)
        assertEquals(20, updatedConfig.maxPrintCount)
        assertEquals(0, updatedConfig.printCount)
        assertTrue(session.wasResetToZero)
    }

    @Test
    fun testTemplateConfigChangesDetected() {
        val initialConfig = AppConfig()
        val session = ConfigSessionState(initialConfig, initialTemplateId = 1, defaultResKey = null)

        session.selectedTemplateId = 3
        assertTrue(session.hasChanges())

        session.selectedTemplateId = 1
        assertFalse(session.hasChanges())
    }
}
