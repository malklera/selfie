package ar.mimbi.Selfie.printing.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PrinterConfigDao {
    @Query("SELECT * FROM printer_configs WHERE id = :id LIMIT 1")
    suspend fun getConfig(id: String = "default"): PrinterConfigEntity?

    @Query("SELECT * FROM printer_configs WHERE id = :id LIMIT 1")
    fun getConfigFlow(id: String = "default"): Flow<PrinterConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(config: PrinterConfigEntity)
}
