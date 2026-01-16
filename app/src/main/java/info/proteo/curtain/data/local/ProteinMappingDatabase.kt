package info.proteo.curtain.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import info.proteo.curtain.data.local.dao.ProteinMappingDao
import info.proteo.curtain.data.local.entity.GeneNameMappingEntity
import info.proteo.curtain.data.local.entity.PrimaryIdMappingEntity
import info.proteo.curtain.data.local.entity.ProteinMappingMetadataEntity

@Database(
    entities = [
        PrimaryIdMappingEntity::class,
        GeneNameMappingEntity::class,
        ProteinMappingMetadataEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ProteinMappingDatabase : RoomDatabase() {
    abstract fun proteinMappingDao(): ProteinMappingDao
}
