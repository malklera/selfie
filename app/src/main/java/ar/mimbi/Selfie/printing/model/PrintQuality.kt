package ar.mimbi.Selfie.printing.model

enum class PrintQuality(val label: String, val dpi: Int) {
    HIGH("Alta (300 DPI)", 300),
    STANDARD("Estándar (200 DPI)", 200),
    DRAFT("Borrador (150 DPI)", 150);

    companion object {
        fun fromName(name: String?): PrintQuality {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: HIGH
        }
    }
}
