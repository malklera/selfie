package ar.mimbi.Selfie.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "error_logs")
data class ErrorLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val buildCommit: String,
    val lastAction: String,
    val screen: String,
    val errorMessage: String
)
