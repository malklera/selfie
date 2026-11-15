package ar.mimbi.Selfie.printing.model

data class PrintPage(
    val id: Long = 0,
    val templateId: Int,
    val templateVersion: Int,
    val templateSnapshotJson: String,
    val pageNumber: Int,
    val slotCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val readyAt: Long? = null,
    val status: PrintPageStatus = PrintPageStatus.OPEN,
    val wasPartial: Boolean = false,
    val statusMessage: String? = null,
    val lastPrintJobId: String? = null,
    val printedAt: Long? = null,
    val items: List<PrintItem> = emptyList()
) {
    val itemCount: Int get() = items.size

    val headerText: String
        get() = "Página $pageNumber - Plantilla $templateId - $itemCount/$slotCount fotos - ${status.labelSpanish}"

    fun getTemplateSnapshot(): PrintTemplate? {
        if (templateSnapshotJson.isBlank()) {
            return ar.mimbi.Selfie.printing.template.DefaultTemplates.getById(templateId)
        }
        return PrintTemplateSnapshot.deserialize(templateSnapshotJson)
    }
}
