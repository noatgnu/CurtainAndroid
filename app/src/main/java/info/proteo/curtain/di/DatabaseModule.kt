package info.proteo.curtain.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import info.proteo.curtain.data.local.CurtainDatabase
import info.proteo.curtain.data.local.dao.CurtainDao
import info.proteo.curtain.data.local.dao.DataFilterListDao
import info.proteo.curtain.data.local.dao.ProteinSearchListDao
import info.proteo.curtain.data.local.dao.SelectionGroupDao
import info.proteo.curtain.data.local.dao.SettingsVariantDao
import info.proteo.curtain.data.local.dao.SiteSettingsDao
import javax.inject.Singleton

/**
 * Hilt module providing Room database dependencies.
 * Configures the Curtain database and provides DAO instances.
 *
 * Matches iOS SwiftData persistent container setup.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provides the Room database instance.
     * Database is created with fallback to destructive migration for development.
     *
     * In production, implement proper migration strategies:
     * - .addMigrations(MIGRATION_1_2, MIGRATION_2_3, ...)
     * - .fallbackToDestructiveMigration() only for development
     *
     * @param context Application context
     * @return CurtainDatabase instance
     */
    @Provides
    @Singleton
    fun provideCurtainDatabase(
        @ApplicationContext context: Context
    ): CurtainDatabase {
        return Room.databaseBuilder(
            context,
            CurtainDatabase::class.java,
            CurtainDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * Provides CurtainDao for dataset operations.
     *
     * @param database CurtainDatabase instance
     * @return CurtainDao
     */
    @Provides
    @Singleton
    fun provideCurtainDao(database: CurtainDatabase): CurtainDao {
        return database.curtainDao()
    }

    /**
     * Provides SiteSettingsDao for backend site management.
     *
     * @param database CurtainDatabase instance
     * @return SiteSettingsDao
     */
    @Provides
    @Singleton
    fun provideSiteSettingsDao(database: CurtainDatabase): SiteSettingsDao {
        return database.siteSettingsDao()
    }

    /**
     * Provides DataFilterListDao for filter list operations.
     *
     * @param database CurtainDatabase instance
     * @return DataFilterListDao
     */
    @Provides
    @Singleton
    fun provideDataFilterListDao(database: CurtainDatabase): DataFilterListDao {
        return database.dataFilterListDao()
    }

    /**
     * Provides SelectionGroupDao for selection group operations.
     *
     * @param database CurtainDatabase instance
     * @return SelectionGroupDao
     */
    @Provides
    @Singleton
    fun provideSelectionGroupDao(database: CurtainDatabase): SelectionGroupDao {
        return database.selectionGroupDao()
    }

    /**
     * Provides ProteinSearchListDao for protein search list operations.
     *
     * @param database CurtainDatabase instance
     * @return ProteinSearchListDao
     */
    @Provides
    @Singleton
    fun provideProteinSearchListDao(database: CurtainDatabase): ProteinSearchListDao {
        return database.proteinSearchListDao()
    }

    /**
     * Provides SettingsVariantDao for settings variant operations.
     *
     * @param database CurtainDatabase instance
     * @return SettingsVariantDao
     */
    @Provides
    @Singleton
    fun provideSettingsVariantDao(database: CurtainDatabase): SettingsVariantDao {
        return database.settingsVariantDao()
    }
}
