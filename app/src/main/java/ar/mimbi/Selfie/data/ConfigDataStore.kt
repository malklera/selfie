package ar.mimbi.Selfie.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
    }

    val appConfigFlow: Flow<AppConfig> = context.dataStore.data
        .map { preferences ->
            AppConfig(
                portadaPath = preferences[PORTADA_PATH],
                captureBoxPath = preferences[CAPTURE_BOX_PATH],
                countdownSeconds = preferences[COUNTDOWN_SECONDS] ?: 3,
                destinationPath = preferences[DESTINATION_PATH] ?: "/storage/emulated/0/Pictures/selfie",
                pictureResolution = preferences[PICTURE_RESOLUTION]
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
        }
    }
}
