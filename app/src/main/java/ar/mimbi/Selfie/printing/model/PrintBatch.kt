package ar.mimbi.Selfie.printing.model

enum class BatchStatus {
    ACTIVE,
    PRINTING,
    COMPLETED,
    FAILED
}

/**
 * Represents a group of print items tied to a specific template and version.
 */
data class PrintBatch(
    val id: Long = 0,
    val templateId: String,
    val templateVersion: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val status: BatchStatus = BatchStatus.ACTIVE
)
