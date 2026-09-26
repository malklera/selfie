package ar.mimbi.Selfie.printing.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "print_batches")
data class PrintBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: String,
    val templateVersion: Int,
    val createdAt: Long,
    val status: String
)
