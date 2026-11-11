package ar.mimbi.Selfie.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ConfigSessionState(
    val initialConfig: AppConfig,
    val initialTemplateId: Int,
    private val defaultResKey: String?
) {
    // ConfigurationScreen state
    var portadaPath by mutableStateOf(initialConfig.portadaPath)
    var captureBoxPath by mutableStateOf(initialConfig.captureBoxPath)
    var countdownSeconds by mutableStateOf(initialConfig.countdownSeconds.toString())
    var destinationPath by mutableStateOf(initialConfig.destinationPath)
    var pictureResolution by mutableStateOf(initialConfig.inputResolution ?: defaultResKey)
    var previewFit by mutableStateOf(initialConfig.previewFit)
    var fixedAspect by mutableStateOf(initialConfig.fixedAspect)
    var decorationFit by mutableStateOf(initialConfig.decorationFit)
    var appOrientation by mutableStateOf(initialConfig.appOrientation)

    // PrintingScreen state
    var showPrintButton by mutableStateOf(initialConfig.showPrintButton)
    var maxPrintCount by mutableStateOf(initialConfig.maxPrintCount.toString())
    var printCount by mutableIntStateOf(initialConfig.printCount)
    var wasResetToZero by mutableStateOf(false)

    // TemplateConfigScreen state
    var selectedTemplateId by mutableIntStateOf(initialTemplateId)

    var isSaved by mutableStateOf(false)

    fun hasChanges(): Boolean {
        if (isSaved) return false
        val finalCountdown = countdownSeconds.toIntOrNull() ?: initialConfig.countdownSeconds
        val finalMaxPrint = maxPrintCount.toIntOrNull() ?: initialConfig.maxPrintCount

        return portadaPath != initialConfig.portadaPath ||
                captureBoxPath != initialConfig.captureBoxPath ||
                finalCountdown != initialConfig.countdownSeconds ||
                countdownSeconds != initialConfig.countdownSeconds.toString() ||
                destinationPath != initialConfig.destinationPath ||
                pictureResolution != (initialConfig.inputResolution ?: defaultResKey) ||
                previewFit != initialConfig.previewFit ||
                fixedAspect != initialConfig.fixedAspect ||
                decorationFit != initialConfig.decorationFit ||
                appOrientation != initialConfig.appOrientation ||
                showPrintButton != initialConfig.showPrintButton ||
                finalMaxPrint != initialConfig.maxPrintCount ||
                maxPrintCount != initialConfig.maxPrintCount.toString() ||
                printCount != initialConfig.printCount ||
                selectedTemplateId != initialTemplateId
    }

    fun toAppConfig(): AppConfig {
        val finalSeconds = countdownSeconds.toIntOrNull() ?: 3
        val finalMaxPrintCount = maxPrintCount.toIntOrNull() ?: 0
        return AppConfig(
            portadaPath = portadaPath,
            captureBoxPath = captureBoxPath,
            countdownSeconds = finalSeconds,
            destinationPath = destinationPath,
            inputResolution = pictureResolution,
            previewFit = previewFit,
            fixedAspect = fixedAspect,
            decorationFit = decorationFit,
            appOrientation = appOrientation,
            showPrintButton = showPrintButton,
            maxPrintCount = finalMaxPrintCount,
            printCount = printCount
        )
    }
}
