package ar.mimbi.Selfie.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ar.mimbi.Selfie.printing.persistence.PrintBatchDao
import ar.mimbi.Selfie.printing.persistence.PrintBatchEntity
import ar.mimbi.Selfie.printing.persistence.PrintItemDao
import ar.mimbi.Selfie.printing.persistence.PrintItemEntity
import ar.mimbi.Selfie.printing.persistence.PrintPageDao
import ar.mimbi.Selfie.printing.persistence.PrintPageEntity
import ar.mimbi.Selfie.printing.persistence.PrinterConfigDao
import ar.mimbi.Selfie.printing.persistence.PrinterConfigEntity

@Database(
    entities = [
        ErrorLog::class,
        PrintBatchEntity::class,
        PrintPageEntity::class,
        PrintItemEntity::class,
        PrinterConfigEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun errorLogDao(): ErrorLogDao
    abstract fun printBatchDao(): PrintBatchDao
    abstract fun printPageDao(): PrintPageDao
    abstract fun printItemDao(): PrintItemDao
    abstract fun printerConfigDao(): PrinterConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Templates now live in code (DefaultTemplates). Drop their old table,
        // keeping the print queue intact.
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `print_templates`")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "selfie_database"
                )
                    .addMigrations(MIGRATION_8_9)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
