package ar.mimbi.selfie.printing.model

data class QueueCounts(
    val total: Int = 0,
    val ready: Int = 0,
    val incomplete: Int = 0,
    val printed: Int = 0,
    val failed: Int = 0
)
