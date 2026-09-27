package ar.mimbi.Selfie.printing.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PrintTemplateDao {
    @Query("SELECT * FROM print_templates")
    fun getAllTemplates(): Flow<List<PrintTemplateEntity>>

    @Query("SELECT * FROM print_templates WHERE id = :id LIMIT 1")
    suspend fun getTemplateById(id: Int): PrintTemplateEntity?

    @Query("SELECT * FROM print_templates WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTemplate(): PrintTemplateEntity?

    @Query("SELECT * FROM print_templates WHERE isActive = 1 LIMIT 1")
    fun getActiveTemplateFlow(): Flow<PrintTemplateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(template: PrintTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(templates: List<PrintTemplateEntity>)

    @Query("UPDATE print_templates SET isActive = 0")
    suspend fun clearActiveTemplates(): Int

    @Query("UPDATE print_templates SET isActive = CASE WHEN id = :activeId THEN 1 ELSE 0 END")
    suspend fun setActiveTemplate(activeId: Int): Int
}
