package ar.mimbi.Selfie.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ConfigDataStore(private val context: Context) {
    companion object {
        val PORTADA_PATH = stringPreferencesKey("portada_path")
        val CAPTURE_BOX_PATH = stringPreferencesKey("capture_box_path")
        val COUNTDOWN_SECONDS = intPreferencesKey("countdown_seconds")
        val DESTINATION_PATH = stringPreferencesKey("destination_path")
        val PICTURE_RESOLUTION = stringPreferencesKey("picture_resolution")
        val PREVIEW_FIT = stringPreferencesKey("preview_fit")
        val FIXED_ASPECT = stringPreferencesKey("fixed_aspect")
        val DECORATION_FIT = stringPreferencesKey("decoration_fit")
        val APP_ORIENTATION = stringPreferencesKey("app_orientation")
        val SHOW_PRINT_BUTTON = booleanPreferencesKey("show_print_button")
        val MAX_PRINT_COUNT = intPreferencesKey("max_print_count")
        val PRINT_COUNT = intPreferencesKey("print_count")
    }

    val appConfigFlow: Flow<AppConfig> = context.dataStore.data
        .map { preferences ->
            val previewFitStr = preferences[PREVIEW_FIT]
            val previewFit = previewFitStr?.let {
                runCatching { PreviewFit.valueOf(it) }.getOrNull()
            } ?: PreviewFit.FIT

            val fixedAspectStr = preferences[FIXED_ASPECT]
            val fixedAspect = fixedAspectStr?.let {
                runCatching { FixedAspect.valueOf(it) }.getOrNull()
            } ?: FixedAspect.R16_9

            val decorationFitStr = preferences[DECORATION_FIT]
            val decorationFit = decorationFitStr?.let {
                runCatching { DecorationFit.valueOf(it) }.getOrNull()
            } ?: DecorationFit.STRETCH

            val appOrientationStr = preferences[APP_ORIENTATION]
            val appOrientation = appOrientationStr?.let {
                runCatching { AppOrientation.valueOf(it) }.getOrNull()
            } ?: AppOrientation.AUTO

            AppConfig(
                portadaPath = preferences[PORTADA_PATH],
                captureBoxPath = preferences[CAPTURE_BOX_PATH],
                countdownSeconds = preferences[COUNTDOWN_SECONDS] ?: 3,
                destinationPath = preferences[DESTINATION_PATH] ?: "/storage/emulated/0/Pictures/selfie",
                inputResolution = preferences[PICTURE_RESOLUTION],
                previewFit = previewFit,
                fixedAspect = fixedAspect,
                decorationFit = decorationFit,
                appOrientation = appOrientation,
                showPrintButton = preferences[SHOW_PRINT_BUTTON] ?: false,
                maxPrintCount = preferences[MAX_PRINT_COUNT] ?: 0,
                printCount = preferences[PRINT_COUNT] ?: 0
            )
        }

    suspend fun saveConfig(config: AppConfig) {
        context.dataStore.edit { preferences ->
            if (config.portadaPath != null) {
                preferences[PORTADA_PATH] = config.portadaPath
            } else {
                preferences.remove(PORTADA_PATH)
            }
            if (config.captureBoxPath != null) {
                preferences[CAPTURE_BOX_PATH] = config.captureBoxPath
            } else {
                preferences.remove(CAPTURE_BOX_PATH)
            }
            preferences[COUNTDOWN_SECONDS] = config.countdownSeconds
            preferences[DESTINATION_PATH] = config.destinationPath
            if (config.inputResolution != null) {
                preferences[PICTURE_RESOLUTION] = config.inputResolution
            } else {
                preferences.remove(PICTURE_RESOLUTION)
            }
            preferences[PREVIEW_FIT] = config.previewFit.name
            preferences[FIXED_ASPECT] = config.fixedAspect.name
            preferences[DECORATION_FIT] = config.decorationFit.name
            preferences[APP_ORIENTATION] = config.appOrientation.name
            preferences[SHOW_PRINT_BUTTON] = config.showPrintButton
            preferences[MAX_PRINT_COUNT] = config.maxPrintCount
            preferences[PRINT_COUNT] = config.printCount
        }
    }

    suspend fun incrementPrintCount(copies: Int) {
        context.dataStore.edit { preferences ->
            val current = preferences[PRINT_COUNT] ?: 0
            preferences[PRINT_COUNT] = current + copies
        }
    }
}
