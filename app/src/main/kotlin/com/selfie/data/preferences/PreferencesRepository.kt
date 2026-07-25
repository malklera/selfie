package com.selfie.data.preferences

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.selfie.domain.model.AppConfig
import com.selfie.domain.model.MainContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val DEFAULT_CAMERA_FRONT = booleanPreferencesKey("default_camera_front")
        val SHOW_FLIP_BUTTON = booleanPreferencesKey("show_flip_button")
        val COUNTDOWN_DURATION = intPreferencesKey("countdown_duration")
        val OVERLAY_IMAGE_URI = stringPreferencesKey("overlay_image_uri")
        val SAVE_FOLDER_URI = stringPreferencesKey("save_folder_uri")
        val MAIN_CONTENT_TYPE = stringPreferencesKey("main_content_type")
        val MAIN_CONTENT_URI = stringPreferencesKey("main_content_uri")
        val SHOW_TO_MAIN_BUTTON = booleanPreferencesKey("show_to_main_button")
        val SHOW_QUICK_RETAKE_BUTTON = booleanPreferencesKey("show_quick_retake_button")
        val SHOW_GALLERY_BUTTON = booleanPreferencesKey("show_gallery_button")
    }

    val appConfig: Flow<AppConfig> = context.dataStore.data.map { preferences ->
        val contentType = preferences[Keys.MAIN_CONTENT_TYPE] ?: "NONE"
        val contentUri = preferences[Keys.MAIN_CONTENT_URI]?.let { Uri.parse(it) }

        val mainContent = when (contentType) {
            "IMAGE" -> contentUri?.let { MainContent.StaticImage(it) } ?: MainContent.None
            "VIDEO" -> contentUri?.let { MainContent.Video(it) } ?: MainContent.None
            "GIF" -> contentUri?.let { MainContent.AnimatedGif(it) } ?: MainContent.None
            else -> MainContent.None
        }

        AppConfig(
            defaultCameraFront = preferences[Keys.DEFAULT_CAMERA_FRONT] ?: true,
            showFlipButton = preferences[Keys.SHOW_FLIP_BUTTON] ?: true,
            countdownDurationSeconds = preferences[Keys.COUNTDOWN_DURATION] ?: 3,
            overlayImageUri = preferences[Keys.OVERLAY_IMAGE_URI]?.let { Uri.parse(it) },
            saveFolderUri = preferences[Keys.SAVE_FOLDER_URI]?.let { Uri.parse(it) },
            mainContent = mainContent,
            showToMainButton = preferences[Keys.SHOW_TO_MAIN_BUTTON] ?: true,
            showQuickRetakeButton = preferences[Keys.SHOW_QUICK_RETAKE_BUTTON] ?: true,
            showGalleryButton = preferences[Keys.SHOW_GALLERY_BUTTON] ?: true
        )
    }

    suspend fun updateConfig(config: AppConfig) {
        context.dataStore.edit { preferences ->
            preferences[Keys.DEFAULT_CAMERA_FRONT] = config.defaultCameraFront
            preferences[Keys.SHOW_FLIP_BUTTON] = config.showFlipButton
            preferences[Keys.COUNTDOWN_DURATION] = config.countdownDurationSeconds
            config.overlayImageUri?.let { preferences[Keys.OVERLAY_IMAGE_URI] = it.toString() }
                ?: preferences.remove(Keys.OVERLAY_IMAGE_URI)
            config.saveFolderUri?.let { preferences[Keys.SAVE_FOLDER_URI] = it.toString() }
                ?: preferences.remove(Keys.SAVE_FOLDER_URI)

            when (val content = config.mainContent) {
                is MainContent.None -> {
                    preferences[Keys.MAIN_CONTENT_TYPE] = "NONE"
                    preferences.remove(Keys.MAIN_CONTENT_URI)
                }
                is MainContent.StaticImage -> {
                    preferences[Keys.MAIN_CONTENT_TYPE] = "IMAGE"
                    preferences[Keys.MAIN_CONTENT_URI] = content.uri.toString()
                }
                is MainContent.Video -> {
                    preferences[Keys.MAIN_CONTENT_TYPE] = "VIDEO"
                    preferences[Keys.MAIN_CONTENT_URI] = content.uri.toString()
                }
                is MainContent.AnimatedGif -> {
                    preferences[Keys.MAIN_CONTENT_TYPE] = "GIF"
                    preferences[Keys.MAIN_CONTENT_URI] = content.uri.toString()
                }
            }

            preferences[Keys.SHOW_TO_MAIN_BUTTON] = config.showToMainButton
            preferences[Keys.SHOW_QUICK_RETAKE_BUTTON] = config.showQuickRetakeButton
            preferences[Keys.SHOW_GALLERY_BUTTON] = config.showGalleryButton
        }
    }
}
