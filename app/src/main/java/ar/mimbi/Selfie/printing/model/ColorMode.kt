package ar.mimbi.Selfie.printing.model

enum class ColorMode(val label: String) {
    COLOR("Color"),
    MONOCHROME("Blanco y Negro");

    companion object {
        fun fromName(name: String?): ColorMode {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: COLOR
        }
    }
}
