package info.proteo.curtain.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import info.proteo.curtain.data.local.dao.ProteomicsDataDao
import info.proteo.curtain.data.local.entity.AllGenesEntity
import info.proteo.curtain.data.local.entity.CurtainMetadataEntity
import info.proteo.curtain.data.local.entity.GeneNameToAccEntity
import info.proteo.curtain.data.local.entity.GenesMapEntity
import info.proteo.curtain.data.local.entity.PrimaryIdsMapEntity
import info.proteo.curtain.data.local.entity.ProcessedProteomicsDataEntity
import info.proteo.curtain.data.local.entity.ProteomicsDataMetadataEntity
import info.proteo.curtain.data.local.entity.RawProteomicsDataEntity

@Database(
    entities = [
        ProcessedProteomicsDataEntity::class,
        RawProteomicsDataEntity::class,
        ProteomicsDataMetadataEntity::class,
        GenesMapEntity::class,
        PrimaryIdsMapEntity::class,
        GeneNameToAccEntity::class,
        AllGenesEntity::class,
        CurtainMetadataEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class ProteomicsDataDatabase : RoomDatabase() {
    abstract fun proteomicsDataDao(): ProteomicsDataDao
}
