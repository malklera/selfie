package ar.mimbi.Selfie.printing.model

/**
 * States for a print item in the queue.
 */
enum class PrintItemStatus {
    PENDING,   // Pendiente - Esperando impresora disponible
    QUEUED,    // En cola de impresión
    PRINTING,  // Imprimiendo / Enviando a la impresora
    BLOCKED,   // Bloqueado - Verificar impresora (papel/atasco)
    PRINTED,   // Completado exitosamente
    FAILED,    // Error de impresión
    CANCELED   // Cancelado por el usuario
}

/**
 * Represents an individual photo copy in the queue.
 */
data class PrintItem(
    val id: Long = 0,
    val batchId: Long,
    val photoUri: String,
    val sequence: Long,
    val status: PrintItemStatus = PrintItemStatus.PENDING,
    val statusMessage: String? = null
)
