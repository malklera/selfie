package ar.mimbi.selfie.printing.model

/**
 * States for a print item in the queue.
 */
enum class PrintItemStatus {
    PENDING,   // Pending - Waiting for available printer
    QUEUED,    // In printing queue
    PRINTING,  // Printing - Sent to printer
    BLOCKED,   // Blocked - Check printer
    SENT,      // Sent to Android print service
    PRINTED,   // Successfully printed
    FAILED,    // Print error
    CANCELED   // Cancel by user
}

/**
 * Represents an individual photo copy in a print page.
 */
data class PrintItem(
    val id: Long = 0,
    val pageId: Long,
    val slotIndex: Int,
    val photoUri: String,
    val sequence: Long,
    val status: PrintItemStatus = PrintItemStatus.PENDING,
    val statusMessage: String? = null
)
