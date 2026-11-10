package ar.mimbi.Selfie.printing.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PrintItemDao {
    @Query("SELECT * FROM print_items WHERE pageId = :pageId ORDER BY slotIndex ASC")
    suspend fun getItemsByPageId(pageId: Long): List<PrintItemEntity>

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

    @Update
    suspend fun updateItems(items: List<PrintItemEntity>): Int

    @Query("UPDATE print_items SET status = :status WHERE id IN (:ids)")
    suspend fun updateItemStatuses(ids: List<Long>, status: String): Int

    @Query("UPDATE print_items SET status = :status WHERE pageId = :pageId")
    suspend fun updateItemStatusesForPage(pageId: Long, status: String): Int

    @Query("UPDATE print_items SET status = :status WHERE pageId IN (:pageIds)")
    suspend fun updateItemStatusesForPages(pageIds: List<Long>, status: String): Int

    @Query("UPDATE print_items SET status = 'PENDING' WHERE status = 'PRINTING'")
    suspend fun resetPrintingToPending(): Int

    @Query("UPDATE print_items SET status = 'PENDING' WHERE status = 'FAILED' OR status = 'CANCELED'")
    suspend fun resetFailedToPending(): Int

    @Query("DELETE FROM print_items WHERE status = 'PRINTED'")
    suspend fun deletePrintedItems(): Int

    @Query("DELETE FROM print_items WHERE id IN (:ids)")
    suspend fun deleteItemsByIds(ids: List<Long>): Int

    @Query("DELETE FROM print_items WHERE pageId = :pageId")
    suspend fun deleteItemsByPageId(pageId: Long): Int

    @Query("DELETE FROM print_items WHERE pageId IN (:pageIds)")
    suspend fun deleteItemsByPageIds(pageIds: List<Long>): Int

    @Query("SELECT * FROM print_items WHERE id IN (:ids) ORDER BY sequence ASC")
    suspend fun getItemsByIds(ids: List<Long>): List<PrintItemEntity>

    @Query("SELECT * FROM print_items WHERE id = :id")
    suspend fun getItemById(id: Long): PrintItemEntity?

    @Query("DELETE FROM print_items")
    suspend fun deleteAllItems(): Int
}
