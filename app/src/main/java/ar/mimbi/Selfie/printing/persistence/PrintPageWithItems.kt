package ar.mimbi.Selfie.printing.persistence

import androidx.room.Embedded
import androidx.room.Relation

data class PrintPageWithItems(
    @Embedded val page: PrintPageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "pageId"
    )
    val items: List<PrintItemEntity>
)
