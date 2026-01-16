package info.proteo.curtain.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import info.proteo.curtain.data.repository.CurtainRepositoryImpl
import info.proteo.curtain.data.repository.DataFilterListRepositoryImpl
import info.proteo.curtain.data.repository.ProteinSearchListRepositoryImpl
import info.proteo.curtain.data.repository.SelectionGroupRepositoryImpl
import info.proteo.curtain.data.repository.SiteSettingsRepositoryImpl
import info.proteo.curtain.domain.repository.CurtainRepository
import info.proteo.curtain.domain.repository.DataFilterListRepository
import info.proteo.curtain.domain.repository.ProteinSearchListRepository
import info.proteo.curtain.domain.repository.SelectionGroupRepository
import info.proteo.curtain.domain.repository.SiteSettingsRepository
import javax.inject.Singleton

/**
 * Hilt module providing repository bindings.
 * Maps repository interfaces to their implementations.
 *
 * Uses @Binds for efficient binding without additional boilerplate.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /**
     * Binds CurtainRepositoryImpl to CurtainRepository interface.
     *
     * @param impl Implementation instance
     * @return Repository interface
     */
    @Binds
    @Singleton
    abstract fun bindCurtainRepository(
        impl: CurtainRepositoryImpl
    ): CurtainRepository

    /**
     * Binds SiteSettingsRepositoryImpl to SiteSettingsRepository interface.
     *
     * @param impl Implementation instance
     * @return Repository interface
     */
    @Binds
    @Singleton
    abstract fun bindSiteSettingsRepository(
        impl: SiteSettingsRepositoryImpl
    ): SiteSettingsRepository

    /**
     * Binds DataFilterListRepositoryImpl to DataFilterListRepository interface.
     *
     * @param impl Implementation instance
     * @return Repository interface
     */
    @Binds
    @Singleton
    abstract fun bindDataFilterListRepository(
        impl: DataFilterListRepositoryImpl
    ): DataFilterListRepository

    /**
     * Binds SelectionGroupRepositoryImpl to SelectionGroupRepository interface.
     *
     * @param impl Implementation instance
     * @return Repository interface
     */
    @Binds
    @Singleton
    abstract fun bindSelectionGroupRepository(
        impl: SelectionGroupRepositoryImpl
    ): SelectionGroupRepository

    /**
     * Binds ProteinSearchListRepositoryImpl to ProteinSearchListRepository interface.
     *
     * @param impl Implementation instance
     * @return Repository interface
     */
    @Binds
    @Singleton
    abstract fun bindProteinSearchListRepository(
        impl: ProteinSearchListRepositoryImpl
    ): ProteinSearchListRepository
}
