package ar.mimbi.Selfie.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ErrorLogDao {
    @Insert
    suspend fun insert(errorLog: ErrorLog): Long

    @Query("SELECT * FROM error_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ErrorLog>>

    @Query("DELETE FROM error_logs")
    suspend fun clearAll(): Int
}
