package info.proteo.curtain.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import info.proteo.curtain.CurtainDao
import info.proteo.curtain.DataFilterListDao
import info.proteo.curtain.DataFilterListEntity
import info.proteo.curtain.data.local.database.entities.CurtainEntity
import info.proteo.curtain.data.local.database.entities.CurtainSiteSettings

@Database(
    entities = [CurtainEntity::class, CurtainSiteSettings::class, DataFilterListEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun curtainDao(): CurtainDao
    abstract fun dataFilterListDao(): DataFilterListDao
}