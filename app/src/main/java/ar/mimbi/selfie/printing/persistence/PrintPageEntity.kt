package ar.mimbi.selfie.printing.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "print_pages")
data class PrintPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Int,
    val templateVersion: Int,
    val templateSnapshotJson: String,
    val pageNumber: Int,
    val slotCount: Int,
    val createdAt: Long,
    val readyAt: Long? = null,
    val status: String, // PrintPageStatus.name
    val wasPartial: Boolean = false,
    val statusMessage: String? = null,
    val lastPrintJobId: String? = null,
    val printedAt: Long? = null
)
