package ar.mimbi.Selfie.printing.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PrintBatchDao {
    @Query("SELECT * FROM print_batches WHERE status = :status ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveBatch(status: String = "ACTIVE"): PrintBatchEntity?

    @Query("SELECT * FROM print_batches WHERE id = :id LIMIT 1")
    suspend fun getBatchById(id: Long): PrintBatchEntity?

    @Query("SELECT * FROM print_batches ORDER BY createdAt DESC")
    fun getAllBatchesFlow(): Flow<List<PrintBatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: PrintBatchEntity): Long

    @Update
    suspend fun updateBatch(batch: PrintBatchEntity): Int

    @Query("UPDATE print_batches SET status = :status WHERE id = :id")
    suspend fun updateBatchStatus(id: Long, status: String): Int

    @Query("DELETE FROM print_batches")
    suspend fun deleteAllBatches(): Int
}
