package info.proteo.curtain.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import info.proteo.curtain.data.local.CurtainDatabase
import info.proteo.curtain.data.local.dao.CurtainCollectionDao
import info.proteo.curtain.data.local.dao.CurtainDao
import info.proteo.curtain.data.local.dao.DataFilterListDao
import info.proteo.curtain.data.local.dao.ProteinSearchListDao
import info.proteo.curtain.data.local.dao.SavedCrossDatasetSearchDao
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
     * Migration from version 5 to 6.
     * Changes collection_session to many-to-many relationship:
     * - Creates junction table collection_session_cross_ref
     * - Recreates collection_session with linkId as primary key
     * - Only affects collection tables, preserves all other user data
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS collection_session_cross_ref (
                    collectionLocalId INTEGER NOT NULL,
                    linkId TEXT NOT NULL,
                    PRIMARY KEY(collectionLocalId, linkId),
                    FOREIGN KEY(collectionLocalId) REFERENCES curtain_collection(localId) ON DELETE CASCADE
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_session_cross_ref_collectionLocalId ON collection_session_cross_ref(collectionLocalId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_session_cross_ref_linkId ON collection_session_cross_ref(linkId)")

            db.execSQL("""
                INSERT OR IGNORE INTO collection_session_cross_ref (collectionLocalId, linkId)
                SELECT collectionLocalId, linkId FROM collection_session
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS collection_session_new (
                    linkId TEXT NOT NULL PRIMARY KEY,
                    id INTEGER NOT NULL,
                    description TEXT NOT NULL,
                    created INTEGER NOT NULL,
                    curtainType TEXT,
                    sourceHostname TEXT NOT NULL DEFAULT ''
                )
            """)

            db.execSQL("""
                INSERT OR IGNORE INTO collection_session_new (linkId, id, description, created, curtainType, sourceHostname)
                SELECT cs.linkId, cs.id, cs.description, cs.created, cs.curtainType,
                       COALESCE(cc.sourceHostname, '') as sourceHostname
                FROM collection_session cs
                LEFT JOIN curtain_collection cc ON cs.collectionLocalId = cc.localId
            """)

            db.execSQL("DROP TABLE collection_session")
            db.execSQL("ALTER TABLE collection_session_new RENAME TO collection_session")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_session_sourceHostname ON collection_session(sourceHostname)")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS saved_cross_dataset_search (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    searchTerms TEXT NOT NULL,
                    searchType TEXT NOT NULL,
                    datasetLinkIds TEXT NOT NULL,
                    significantOnly INTEGER NOT NULL,
                    useRegex INTEGER NOT NULL,
                    resultSummariesJson TEXT NOT NULL,
                    proteinCount INTEGER NOT NULL,
                    datasetCount INTEGER NOT NULL,
                    created INTEGER NOT NULL,
                    lastOpened INTEGER NOT NULL
                )
            """)
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE curtain ADD COLUMN sessionName TEXT")
            db.execSQL("ALTER TABLE collection_session ADD COLUMN sessionName TEXT")
        }
    }

    /**
     * Provides the Room database instance.
     * Uses proper migrations to preserve user data across schema changes.
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
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
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

    @Provides
    @Singleton
    fun provideCurtainCollectionDao(database: CurtainDatabase): CurtainCollectionDao {
        return database.curtainCollectionDao()
    }

    @Provides
    @Singleton
    fun provideSavedCrossDatasetSearchDao(database: CurtainDatabase): SavedCrossDatasetSearchDao {
        return database.savedCrossDatasetSearchDao()
    }
}
