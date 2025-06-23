package info.proteo.curtain

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CurtainEntity::class, CurtainSiteSettings::class, DataFilterListEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun curtainDao(): CurtainDao
    abstract fun dataFilterListDao(): DataFilterListDao
}