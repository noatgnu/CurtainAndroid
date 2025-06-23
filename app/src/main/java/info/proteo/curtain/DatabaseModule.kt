package info.proteo.curtain

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "curtain_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideCurtainDao(appDatabase: AppDatabase): CurtainDao {
        return appDatabase.curtainDao()
    }

    @Provides
    @Singleton
    fun provideDataFilterListDao(appDatabase: AppDatabase): DataFilterListDao {
        return appDatabase.dataFilterListDao()
    }

    /**
     * Provides a singleton instance of CurtainDataService
     */
    @Provides
    @Singleton
    fun provideCurtainDataService(): CurtainDataService {
        return CurtainDataService()
    }
}
