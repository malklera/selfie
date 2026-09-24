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
        val SHOW_PRINT_BUTTON = booleanPreferencesKey("show_print_button")
        val MAX_PRINT_COUNT = intPreferencesKey("max_print_count")
        val PRINT_COUNT = intPreferencesKey("print_count")
        val PRINT_MODE = stringPreferencesKey("print_mode")
    }

    val appConfigFlow: Flow<AppConfig> = context.dataStore.data
        .map { preferences ->
            AppConfig(
                portadaPath = preferences[PORTADA_PATH],
                captureBoxPath = preferences[CAPTURE_BOX_PATH],
                countdownSeconds = preferences[COUNTDOWN_SECONDS] ?: 3,
                destinationPath = preferences[DESTINATION_PATH] ?: "/storage/emulated/0/Pictures/selfie",
                pictureResolution = preferences[PICTURE_RESOLUTION],
                showPrintButton = preferences[SHOW_PRINT_BUTTON] ?: false,
                maxPrintCount = preferences[MAX_PRINT_COUNT] ?: 0,
                printCount = preferences[PRINT_COUNT] ?: 0,
                printMode = preferences[PRINT_MODE] ?: "mode_full_width_single"
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
            if (config.pictureResolution != null) {
                preferences[PICTURE_RESOLUTION] = config.pictureResolution
            } else {
                preferences.remove(PICTURE_RESOLUTION)
            }
            preferences[SHOW_PRINT_BUTTON] = config.showPrintButton
            preferences[MAX_PRINT_COUNT] = config.maxPrintCount
            preferences[PRINT_COUNT] = config.printCount
            preferences[PRINT_MODE] = config.printMode
        }
    }
}
