package ar.mimbi.Selfie.printing.template

import ar.mimbi.Selfie.printing.model.PhotoSlot
import ar.mimbi.Selfie.printing.model.PrintTemplate

object DefaultTemplates {
    val TEMPLATE_FULL_WIDTH_SINGLE = PrintTemplate(
        id = 1,
        description = "Foto Única - Ancho Completo",
        slots = listOf(
            PhotoSlot(left = 0.0f, top = 0.0f, width = 1.0f, height = 1.0f)
        ),
        version = 1,
        quality = "Alta (300 DPI)",
        widthCm = 10.0f,
        heightCm = 15.0f,
        marginMm = 0.0f,
        isFullWidth = true
    )

    val TEMPLATE_SINGLE_MARGIN = PrintTemplate(
        id = 2,
        description = "Foto Única - Con Márgenes",
        slots = listOf(
            PhotoSlot(left = 0.05f, top = 0.05f, width = 0.9f, height = 0.9f)
        ),
        version = 1,
        quality = "Estándar (200 DPI)",
        widthCm = 9.0f,
        heightCm = 13.0f,
        marginMm = 5.0f,
        isFullWidth = false
    )

    val TEMPLATE_DOUBLE_STRIP = PrintTemplate(
        id = 3,
        description = "Tira Doble de Fotos",
        slots = listOf(
            PhotoSlot(left = 0.0f, top = 0.0f, width = 1.0f, height = 0.5f),
            PhotoSlot(left = 0.0f, top = 0.5f, width = 1.0f, height = 0.5f)
        ),
        version = 1,
        quality = "Alta (300 DPI)",
        widthCm = 5.0f,
        heightCm = 15.0f,
        marginMm = 0.0f,
        isFullWidth = false
    )

    val TEMPLATE_4_PHOTOS = PrintTemplate(
        id = 4,
        description = "4 Fotos por Página (Cuadrícula 2x2)",
        slots = listOf(
            PhotoSlot(left = 0.0f, top = 0.0f, width = 0.5f, height = 0.5f),
            PhotoSlot(left = 0.5f, top = 0.0f, width = 0.5f, height = 0.5f),
            PhotoSlot(left = 0.0f, top = 0.5f, width = 0.5f, height = 0.5f),
            PhotoSlot(left = 0.5f, top = 0.5f, width = 0.5f, height = 0.5f)
        ),
        version = 1,
        quality = "Alta (300 DPI)",
        widthCm = 10.0f,
        heightCm = 15.0f,
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
