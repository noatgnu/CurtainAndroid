package info.proteo.curtain.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import info.proteo.curtain.data.local.dao.CurtainDao
import info.proteo.curtain.data.local.dao.DataFilterListDao
import info.proteo.curtain.data.local.dao.ProteinSearchListDao
import info.proteo.curtain.data.local.dao.SelectionGroupDao
import info.proteo.curtain.data.local.dao.SettingsVariantDao
import info.proteo.curtain.data.local.dao.SiteSettingsDao
import info.proteo.curtain.data.local.entity.CurtainEntity
import info.proteo.curtain.data.local.entity.CurtainSiteSettingsEntity
import info.proteo.curtain.data.local.entity.DataFilterListEntity
import info.proteo.curtain.data.local.entity.ProteinSearchListEntity
import info.proteo.curtain.data.local.entity.SelectionGroupEntity
import info.proteo.curtain.data.local.entity.SettingsVariantEntity

/**
 * Room database for the Curtain application.
 * Manages local persistence for curtain datasets, site settings, filter lists, and selection groups.
 *
 * Version 1: Initial database schema with three entities:
 * - CurtainEntity: Dataset metadata
 * - CurtainSiteSettingsEntity: Backend server configurations
 * - DataFilterListEntity: Protein/gene filter lists
 *
 * Version 2: Added selection groups support:
 * - SelectionGroupEntity: Protein selection groups with color coding
 *
 * Version 3: Added protein search lists support:
 * - ProteinSearchListEntity: Named protein search lists
 *
 * Version 4: Added settings variants support:
 * - SettingsVariantEntity: Save and load analysis configuration presets
 *
 * Schema export is enabled for version control and migrations.
 * Schema location is configured in app/build.gradle.kts (ksp.arg).
 */
@Database(
    entities = [
        CurtainEntity::class,
        CurtainSiteSettingsEntity::class,
        DataFilterListEntity::class,
        SelectionGroupEntity::class,
        ProteinSearchListEntity::class,
        SettingsVariantEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class CurtainDatabase : RoomDatabase() {

    /**
     * Data Access Object for curtain dataset operations.
     *
     * @return CurtainDao instance
     */
    abstract fun curtainDao(): CurtainDao

    /**
     * Data Access Object for site settings operations.
     *
     * @return SiteSettingsDao instance
     */
    abstract fun siteSettingsDao(): SiteSettingsDao

    /**
     * Data Access Object for filter list operations.
     *
     * @return DataFilterListDao instance
     */
    abstract fun dataFilterListDao(): DataFilterListDao

    /**
     * Data Access Object for selection group operations.
     *
     * @return SelectionGroupDao instance
     */
    abstract fun selectionGroupDao(): SelectionGroupDao

    /**
     * Data Access Object for protein search list operations.
     *
     * @return ProteinSearchListDao instance
     */
    abstract fun proteinSearchListDao(): ProteinSearchListDao

    /**
     * Data Access Object for settings variant operations.
     *
     * @return SettingsVariantDao instance
     */
    abstract fun settingsVariantDao(): SettingsVariantDao

    companion object {
        const val DATABASE_NAME = "curtain_database"
    }
}
