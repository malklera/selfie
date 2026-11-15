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
        heightCm = 29.7f
    )

    // 2 landscape photos arranged one above the other with 2mm separation
    // 210mm width x 147.5mm height each (147.5mm + 2mm gap + 147.5mm = 297mm)
    // Photos are rotated when needed and always fitted (contain), never cropped.
    val TEMPLATE_DOUBLE_STRIP = PrintTemplate(
        id = 2,
        description = "Tira Doble de Fotos (A4)",
        slots = listOf(
            PhotoSlot(left = 0.0f, top = 0.0f, width = 1.0f, height = 147.5f / 297.0f),
            PhotoSlot(left = 0.0f, top = 149.5f / 297.0f, width = 1.0f, height = 147.5f / 297.0f)
        ),
        version = 1,
        quality = "Alta (300 DPI)",
        widthCm = 21.0f,
        heightCm = 29.7f
    )

    // 4 photos with 2mm total separation between pictures (+) and 0mm outer edge margins
    // 210mm width: 104mm + 2mm gap + 104mm = 210mm
    // 297mm height: 147.5mm + 2mm gap + 147.5mm = 297mm
    val TEMPLATE_4_PHOTOS = PrintTemplate(
        id = 3,
        description = "4 Fotos por Página (Separación 2 mm en + - A4)",
        slots = listOf(
            PhotoSlot(left = 0.0f, top = 0.0f, width = 104.0f / 210.0f, height = 147.5f / 297.0f),
            PhotoSlot(left = 106.0f / 210.0f, top = 0.0f, width = 104.0f / 210.0f, height = 147.5f / 297.0f),
            PhotoSlot(left = 0.0f, top = 149.5f / 297.0f, width = 104.0f / 210.0f, height = 147.5f / 297.0f),
            PhotoSlot(left = 106.0f / 210.0f, top = 149.5f / 297.0f, width = 104.0f / 210.0f, height = 147.5f / 297.0f)
        ),
        version = 1,
        quality = "Alta (300 DPI)",
        widthCm = 21.0f,
        heightCm = 29.7f
    )

    val ALL_TEMPLATES = listOf(
        TEMPLATE_FULL_WIDTH_SINGLE,
        TEMPLATE_DOUBLE_STRIP,
        TEMPLATE_4_PHOTOS
    )

    fun getById(id: Int?): PrintTemplate {
        return ALL_TEMPLATES.firstOrNull { it.id == id } ?: TEMPLATE_FULL_WIDTH_SINGLE
    }
}
