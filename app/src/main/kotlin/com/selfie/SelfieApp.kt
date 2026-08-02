package com.selfie

import android.app.Application
import com.selfie.data.preferences.PreferencesRepository

class SelfieApp : Application() {

    lateinit var preferencesRepository: PreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = PreferencesRepository(this)
    }
}
