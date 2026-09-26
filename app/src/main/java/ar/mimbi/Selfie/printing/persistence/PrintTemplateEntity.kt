package ar.mimbi.Selfie.printing.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "print_templates")
data class PrintTemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val slotsJson: String,
    val version: Int,
    val isActive: Boolean = false,
    val quality: String = "Alta (300 DPI)",
    val widthCm: Float = 10.0f,
    val heightCm: Float = 15.0f,
    val marginMm: Float = 0.0f,
    val isFullWidth: Boolean = true
)
