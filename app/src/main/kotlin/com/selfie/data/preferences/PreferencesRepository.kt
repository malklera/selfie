package com.selfie.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.selfie.domain.model.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "selfie_prefs")

class PreferencesRepository(private val context: Context) {

    private companion object {
        val COVER_IMAGE_URI = stringPreferencesKey("cover_image_uri")
        val COUNTDOWN_SECONDS = intPreferencesKey("countdown_seconds")
        val DESTINATION_PATH = stringPreferencesKey("destination_path")
    }

    fun getConfig(): Flow<AppConfig> = context.dataStore.data.map { prefs ->
        AppConfig(
            coverImageUri = prefs[COVER_IMAGE_URI],
            countdownSeconds = prefs[COUNTDOWN_SECONDS] ?: 3,
            destinationPath = prefs[DESTINATION_PATH] ?: "Pictures/selfie"
        )
    }

    suspend fun save(config: AppConfig) {
        context.dataStore.edit { prefs ->
            if (config.coverImageUri != null) {
                prefs[COVER_IMAGE_URI] = config.coverImageUri
            } else {
                prefs.remove(COVER_IMAGE_URI)
            }
            prefs[COUNTDOWN_SECONDS] = config.countdownSeconds
            prefs[DESTINATION_PATH] = config.destinationPath
        }
    }
}
