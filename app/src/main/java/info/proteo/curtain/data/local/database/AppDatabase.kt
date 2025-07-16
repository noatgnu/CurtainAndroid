package info.proteo.curtain.data.local.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import info.proteo.curtain.CurtainDao
import info.proteo.curtain.DataFilterListDao
import info.proteo.curtain.DataFilterListEntity
import info.proteo.curtain.data.local.database.entities.CurtainEntity
import info.proteo.curtain.data.local.database.entities.CurtainSiteSettings

@Database(
    entities = [CurtainEntity::class, CurtainSiteSettings::class, DataFilterListEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun curtainDao(): CurtainDao
    abstract fun dataFilterListDao(): DataFilterListDao
    
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add the is_pinned column to the curtain table
                database.execSQL("ALTER TABLE curtain ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}