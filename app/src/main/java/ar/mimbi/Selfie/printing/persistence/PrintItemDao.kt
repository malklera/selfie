package ar.mimbi.Selfie.printing.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PrintItemDao {
    @Query("SELECT * FROM print_items WHERE pageId = :pageId ORDER BY slotIndex ASC")
    suspend fun getItemsByPageId(pageId: Long): List<PrintItemEntity>

    @Query("SELECT * FROM print_items ORDER BY sequence DESC LIMIT 1")
    suspend fun getLastItem(): PrintItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: PrintItemEntity): Long

    @Query("UPDATE print_items SET status = :status WHERE pageId = :pageId")
    suspend fun updateItemStatusesForPage(pageId: Long, status: String): Int

    @Query("UPDATE print_items SET status = :status WHERE pageId IN (:pageIds)")
    suspend fun updateItemStatusesForPages(pageIds: List<Long>, status: String): Int

    @Query("DELETE FROM print_items WHERE status = 'PRINTED'")
    suspend fun deletePrintedItems(): Int

    @Query("DELETE FROM print_items WHERE pageId IN (:pageIds)")
    suspend fun deleteItemsByPageIds(pageIds: List<Long>): Int

    @Query("DELETE FROM print_items")
    suspend fun deleteAllItems(): Int
}
