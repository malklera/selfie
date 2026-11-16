package ar.mimbi.Selfie.printing.persistence

import androidx.room.Dao
import androidx.room.Query

@Dao
interface PrintBatchDao {

    @Query("DELETE FROM print_batches")
    suspend fun deleteAllBatches(): Int
}
