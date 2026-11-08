package ar.mimbi.Selfie.printing.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PrintItemDao {
    @Query("SELECT * FROM print_items WHERE batchId = :batchId AND status = :status ORDER BY sequence ASC")
    suspend fun getItemsByBatchAndStatus(batchId: Long, status: String): List<PrintItemEntity>

    @Query("SELECT * FROM print_items WHERE status = 'PENDING' ORDER BY sequence ASC")
    suspend fun getAllPendingItems(): List<PrintItemEntity>

    @Query("SELECT * FROM print_items WHERE status = 'FAILED' OR status = 'CANCELED' ORDER BY sequence ASC")
    suspend fun getAllFailedItems(): List<PrintItemEntity>

    @Query("SELECT * FROM print_items ORDER BY sequence DESC LIMIT 1")
    suspend fun getLastItem(): PrintItemEntity?

    @Query("SELECT * FROM print_items ORDER BY sequence DESC")
    fun getAllItemsFlow(): Flow<List<PrintItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PrintItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PrintItemEntity>): List<Long>

    @Update
    suspend fun updateItem(item: PrintItemEntity): Int

    @Query("UPDATE print_items SET status = :status WHERE id IN (:ids)")
    suspend fun updateItemStatuses(ids: List<Long>, status: String): Int

    @Query("UPDATE print_items SET status = 'PENDING' WHERE status = 'PRINTING'")
    suspend fun resetPrintingToPending(): Int

    @Query("UPDATE print_items SET status = 'PENDING' WHERE status = 'FAILED' OR status = 'CANCELED'")
    suspend fun resetFailedToPending(): Int

    @Query("DELETE FROM print_items WHERE status = 'PRINTED'")
    suspend fun deletePrintedItems(): Int

    @Query("DELETE FROM print_items")
    suspend fun deleteAllItems(): Int
}
