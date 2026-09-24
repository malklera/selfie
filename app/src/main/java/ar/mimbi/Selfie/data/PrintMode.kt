package ar.mimbi.Selfie.data

data class PrintMode(
    val id: String,
    val name: String,
    val description: String,
    val isFullWidth: Boolean,
    val imageCount: Int,
    val widthCm: Float,
    val heightCm: Float,
    val quality: String
) {
    companion object {
        val ALL_MODES = listOf(
            PrintMode(
                id = "mode_full_width_single",
                name = "Foto Única - Ancho Completo",
                description = "Impresión de una sola fotografía aprovechando todo el ancho disponible del papel.",
                isFullWidth = true,
                imageCount = 1,
                widthCm = 10.0f,
                heightCm = 15.0f,
                quality = "Alta (300 DPI)"
            ),
            PrintMode(
                id = "mode_single_margin",
                name = "Foto Única - Con Márgenes",
                description = "Foto centrada con margen blanco uniforme alrededor de la imagen.",
                isFullWidth = false,
                imageCount = 1,
                widthCm = 9.0f,
                heightCm = 13.0f,
                quality = "Estándar (200 DPI)"
            ),
            PrintMode(
                id = "mode_double_strip",
                name = "Tira Doble de Fotos",
                description = "Dos tiras verticales idénticas con cuadrícula de fotos en la misma hoja.",
                isFullWidth = false,
                imageCount = 2,
                widthCm = 5.0f,
                heightCm = 15.0f,
                quality = "Alta (300 DPI)"
            )
        )

        fun getById(id: String?): PrintMode {
            return ALL_MODES.firstOrNull { it.id == id } ?: ALL_MODES.first()
        }
    }
}
