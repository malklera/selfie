package ar.mimbi.selfie.printing.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "print_items")
data class PrintItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageId: Long,
    val slotIndex: Int,
    val photoUri: String,
    val sequence: Long,
    val status: String
)
