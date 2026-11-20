package ar.mimbi.selfie.printing.model

enum class PrintPageStatus(val labelSpanish: String) {
    INCOMPLETE("Incompleta"), // Accepts more photos; itemCount < slotCount
    READY("Lista"),        // Full; eligible for normal print
    SENT("Enviada"),       // Included in an Android print job
    PRINTING("Imprimiendo"),   // Optional, for Android job in progress
    BLOCKED("Bloqueada"),   // Optional, printer blocked
    PRINTED("Impresa"),     // Job completed successfully
    FAILED("Fallida"),      // Render or print failure; retryable
    CANCELED("Cancelada")   // Job canceled; retryable
}
