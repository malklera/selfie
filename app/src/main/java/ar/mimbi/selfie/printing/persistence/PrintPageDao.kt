package ar.mimbi.selfie.printing.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PrintPageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PrintPageEntity): Long

    @Update
    suspend fun updatePage(page: PrintPageEntity): Int

    @Query("UPDATE print_pages SET status = :status, statusMessage = :statusMessage WHERE id = :pageId")
    suspend fun updatePageStatus(pageId: Long, status: String, statusMessage: String? = null): Int

    @Query("UPDATE print_pages SET status = :status, statusMessage = :statusMessage WHERE id IN (:pageIds)")
    suspend fun updatePageStatuses(pageIds: List<Long>, status: String, statusMessage: String? = null): Int

    @Query("UPDATE print_pages SET status = :status, statusMessage = :statusMessage, lastPrintJobId = :jobId WHERE id IN (:pageIds)")
    suspend fun updatePageSentJob(pageIds: List<Long>, status: String, jobId: String?, statusMessage: String? = null): Int

    @Query("UPDATE print_pages SET status = :status, printedAt = :printedAt, statusMessage = :statusMessage WHERE id IN (:pageIds)")
    suspend fun updatePagesPrintedInfo(pageIds: List<Long>, printedAt: Long?, status: String, statusMessage: String? = null): Int

    @Query("SELECT * FROM print_pages WHERE id = :id")
    suspend fun getPageById(id: Long): PrintPageEntity?

    @Query("SELECT * FROM print_pages WHERE templateId = :templateId AND templateVersion = :templateVersion AND status = 'OPEN' ORDER BY id DESC LIMIT 1")
    suspend fun getLatestOpenPage(templateId: Int, templateVersion: Int): PrintPageEntity?

    @Query("SELECT MAX(pageNumber) FROM print_pages WHERE templateId = :templateId AND templateVersion = :templateVersion")
    suspend fun getMaxPageNumber(templateId: Int, templateVersion: Int): Int?

    @Transaction
    @Query("SELECT * FROM print_pages ORDER BY createdAt DESC, pageNumber DESC, id DESC")
    fun getAllPagesWithItemsFlow(): Flow<List<PrintPageWithItems>>

    @Transaction
    @Query("SELECT * FROM print_pages WHERE id IN (:ids) ORDER BY createdAt ASC, pageNumber ASC, id ASC")
    suspend fun getPagesWithItemsByIds(ids: List<Long>): List<PrintPageWithItems>

    @Transaction
    @Query("SELECT * FROM print_pages WHERE status = :status ORDER BY createdAt ASC, pageNumber ASC, id ASC")
    suspend fun getPagesWithItemsByStatus(status: String): List<PrintPageWithItems>

    @Transaction
    @Query("SELECT * FROM print_pages WHERE status = 'FAILED' OR status = 'CANCELED' ORDER BY createdAt ASC, pageNumber ASC, id ASC")
    suspend fun getFailedOrCanceledPagesWithItems(): List<PrintPageWithItems>

    @Query("DELETE FROM print_pages WHERE id IN (:pageIds)")
    suspend fun deletePages(pageIds: List<Long>): Int

    @Query("DELETE FROM print_pages WHERE status = 'PRINTED'")
    suspend fun deletePrintedPages(): Int

    @Query("DELETE FROM print_pages")
    suspend fun deleteAllPages(): Int
}
