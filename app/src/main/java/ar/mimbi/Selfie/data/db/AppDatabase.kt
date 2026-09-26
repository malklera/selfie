package ar.mimbi.Selfie.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ar.mimbi.Selfie.printing.persistence.PrintBatchDao
import ar.mimbi.Selfie.printing.persistence.PrintBatchEntity
import ar.mimbi.Selfie.printing.persistence.PrintItemDao
import ar.mimbi.Selfie.printing.persistence.PrintItemEntity
import ar.mimbi.Selfie.printing.persistence.PrintTemplateDao
import ar.mimbi.Selfie.printing.persistence.PrintTemplateEntity
import ar.mimbi.Selfie.printing.persistence.PrinterConfigDao
import ar.mimbi.Selfie.printing.persistence.PrinterConfigEntity

@Database(
    entities = [
        ErrorLog::class,
        PrintTemplateEntity::class,
        PrintBatchEntity::class,
        PrintItemEntity::class,
        PrinterConfigEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun errorLogDao(): ErrorLogDao
    abstract fun printTemplateDao(): PrintTemplateDao
    abstract fun printBatchDao(): PrintBatchDao
    abstract fun printItemDao(): PrintItemDao
    abstract fun printerConfigDao(): PrinterConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "selfie_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

