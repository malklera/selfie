package ar.mimbi.selfie.data

import java.util.concurrent.atomic.AtomicReference

object UserActionTracker {
    private val _currentScreen = AtomicReference("Unknown")
    val currentScreen: String
        get() = _currentScreen.get()

    private val _lastAction = AtomicReference("None")
    val lastAction: String
        get() = _lastAction.get()

    fun updateScreen(screenName: String) {
        _currentScreen.set(screenName)
    }

    fun trackAction(action: String) {
        _lastAction.set(action)
    }
}

