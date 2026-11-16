package ar.mimbi.selfie.printing.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "printer_configs")
data class PrinterConfigEntity(
    @PrimaryKey val id: String = "default",
    val selectedPrinterId: String,
    val printerType: String,
    val settingsJson: String
)
