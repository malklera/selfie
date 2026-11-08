package ar.mimbi.Selfie.printing.template

import ar.mimbi.Selfie.printing.model.PhotoSlot
import ar.mimbi.Selfie.printing.model.PrintTemplate

object DefaultTemplates {
    val TEMPLATE_FULL_WIDTH_SINGLE = PrintTemplate(
        id = 1,
        description = "Foto Única - Página Completa (Ajustar)",
        slots = listOf(
            PhotoSlot(left = 0.0f, top = 0.0f, width = 1.0f, height = 1.0f)
        ),
        version = 1,
        quality = "Alta (300 DPI)",
        widthCm = 21.0f,
        heightCm = 29.7f,
        marginMm = 0.0f,
        isFullWidth = true
    )

    // A4 dimensions: 210mm x 297mm. Equal 10mm margins on all 4 sides:
    // left = 10/210, top = 10/297, width = 190/210, height = 277/297
    val TEMPLATE_SINGLE_MARGIN = PrintTemplate(
        id = 2,
        description = "Foto Única - Con Márgenes (10 mm)",
        slots = listOf(
            PhotoSlot(
                left = 10.0f / 210.0f,
                top = 10.0f / 297.0f,
                width = 190.0f / 210.0f,
                height = 277.0f / 297.0f
            )
        ),
        version = 1,
        quality = "Estándar (200 DPI)",
        widthCm = 21.0f,
        heightCm = 29.7f,
        marginMm = 10.0f,
        isFullWidth = false
    )

    // 2 landscape photos arranged one above the other with 10mm separation
    // 210mm width x 143.5mm height each (143.5mm + 10mm gap + 143.5mm = 297mm)
    val TEMPLATE_DOUBLE_STRIP = PrintTemplate(
        id = 3,
        description = "Tira Doble de Fotos (A4)",
        slots = listOf(
            PhotoSlot(left = 0.0f, top = 0.0f, width = 1.0f, height = 143.5f / 297.0f),
            PhotoSlot(left = 0.0f, top = 153.5f / 297.0f, width = 1.0f, height = 143.5f / 297.0f)
        ),
        version = 1,
        quality = "Alta (300 DPI)",
        widthCm = 21.0f,
        heightCm = 29.7f,
        marginMm = 0.0f,
        isFullWidth = false
    )

    // 4 photos with 10mm total separation between pictures (+) and 0mm outer edge margins
    // 210mm width: 100mm + 10mm gap + 100mm = 210mm
    // 297mm height: 143.5mm + 10mm gap + 143.5mm = 297mm
    val TEMPLATE_4_PHOTOS = PrintTemplate(
        id = 4,
        description = "4 Fotos por Página (Separación 10 mm en + - A4)",
        slots = listOf(
            PhotoSlot(left = 0.0f, top = 0.0f, width = 100.0f / 210.0f, height = 143.5f / 297.0f),
            PhotoSlot(left = 110.0f / 210.0f, top = 0.0f, width = 100.0f / 210.0f, height = 143.5f / 297.0f),
            PhotoSlot(left = 0.0f, top = 153.5f / 297.0f, width = 100.0f / 210.0f, height = 143.5f / 297.0f),
            PhotoSlot(left = 110.0f / 210.0f, top = 153.5f / 297.0f, width = 100.0f / 210.0f, height = 143.5f / 297.0f)
        ),
        version = 1,
        quality = "Alta (300 DPI)",
        widthCm = 21.0f,
        heightCm = 29.7f,
        marginMm = 0.0f,
        isFullWidth = false
    )

    val ALL_TEMPLATES = listOf(
        TEMPLATE_FULL_WIDTH_SINGLE,
        TEMPLATE_SINGLE_MARGIN,
        TEMPLATE_DOUBLE_STRIP,
        TEMPLATE_4_PHOTOS
    )

    fun getById(id: Int?): PrintTemplate {
        return ALL_TEMPLATES.firstOrNull { it.id == id } ?: TEMPLATE_FULL_WIDTH_SINGLE
    }
}
