package ar.mimbi.Selfie.data

object UserActionTracker {
    var currentScreen: String = "Unknown"
        private set
    
    var lastAction: String = "None"
        private set

    fun updateScreen(screenName: String) {
        currentScreen = screenName
    }

    fun trackAction(action: String) {
        lastAction = action
    }
}
