package ar.mimbi.Selfie.data

import android.content.Context
import ar.mimbi.Selfie.BuildConfig
import ar.mimbi.Selfie.data.db.AppDatabase
import ar.mimbi.Selfie.data.db.ErrorLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ErrorLogger {
    private var database: AppDatabase? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        database = AppDatabase.getDatabase(context)
    }

    fun log(message: String) {
        val logEntry = ErrorLog(
            timestamp = System.currentTimeMillis(),
            buildCommit = BuildConfig.GIT_COMMIT,
            lastAction = UserActionTracker.lastAction,
            screen = UserActionTracker.currentScreen,
            errorMessage = message
        )
        
        scope.launch {
            database?.errorLogDao()?.insert(logEntry)
        }
    }
}
