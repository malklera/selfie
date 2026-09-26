package ar.mimbi.Selfie.printing.model

enum class PrintItemStatus {
    PENDING,
    PRINTING,
    PRINTED,
    FAILED
}

/**
 * Represents an individual photo copy in the queue.
 */
data class PrintItem(
    val id: Long = 0,
    val batchId: Long,
    val photoUri: String,
    val sequence: Long,
    val status: PrintItemStatus = PrintItemStatus.PENDING
)
