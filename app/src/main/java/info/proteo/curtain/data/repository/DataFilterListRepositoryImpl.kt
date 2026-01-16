package info.proteo.curtain.data.repository

import info.proteo.curtain.data.local.dao.DataFilterListDao
import info.proteo.curtain.data.local.entity.DataFilterListEntity
import info.proteo.curtain.data.remote.api.CurtainApiService
import info.proteo.curtain.di.NetworkModule
import info.proteo.curtain.domain.repository.DataFilterListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of DataFilterListRepository.
 * Manages predefined protein/gene filter lists combining local and remote sources.
 *
 * Matches iOS filter list management.
 */
@Singleton
class DataFilterListRepositoryImpl @Inject constructor(
    private val dataFilterListDao: DataFilterListDao,
    private val apiService: CurtainApiService,
    private val okHttpClient: OkHttpClient
) : DataFilterListRepository {

    override fun getAllFilters(): Flow<List<DataFilterListEntity>> {
        return dataFilterListDao.getAllFilters()
    }

    override fun getFiltersByCategory(category: String): Flow<List<DataFilterListEntity>> {
        return dataFilterListDao.getFiltersByCategory(category)
    }

    override fun getDefaultFilters(): Flow<List<DataFilterListEntity>> {
        return dataFilterListDao.getDefaultFilters()
    }

    override fun searchFilters(query: String): Flow<List<DataFilterListEntity>> {
        return dataFilterListDao.searchFilters(query)
    }

    override fun getAllCategories(): Flow<List<String>> {
        return dataFilterListDao.getAllCategories()
    }

    override suspend fun getFilterByApiId(apiId: Int): DataFilterListEntity? {
        return dataFilterListDao.getFilterByApiId(apiId)
    }

    override suspend fun getFilterById(id: Long): DataFilterListEntity? {
        return dataFilterListDao.getFilterById(id)
    }

    /**
     * Sync filters from backend API to local database.
     * Matches iOS implementation: first gets all categories, then fetches all filter lists
     * for each category with pagination.
     *
     * @param hostname Backend server hostname
     * @param limit Maximum number of results per page (default 10, max 10)
     * @return Result with list of synced filter entities
     */
    override suspend fun syncFilters(
        hostname: String,
        limit: Int
    ): Result<List<DataFilterListEntity>> = withContext(Dispatchers.IO) {
        try {
            val retrofit = NetworkModule.createRetrofitForHost(hostname, okHttpClient)
            val dynamicApiService = retrofit.create(CurtainApiService::class.java)

            val categories = dynamicApiService.getAllCategories()
            val allEntities = mutableListOf<DataFilterListEntity>()

            for (category in categories) {
                var offset = 0
                var hasMore = true

                while (hasMore) {
                    val response = dynamicApiService.getDataFilterListsByCategory(
                        category = category,
                        limit = limit,
                        offset = offset
                    )

                    val entities = response.results.mapNotNull { dto ->
                        val existing = dataFilterListDao.getFilterByApiId(dto.id)
                        if (existing == null) {
                            DataFilterListEntity(
                                apiId = dto.id,
                                name = dto.name,
                                category = category,
                                data = dto.data,
                                isDefault = dto.isDefault,
                                user = dto.user
                            )
                        } else {
                            null
                        }
                    }

                    if (entities.isNotEmpty()) {
                        dataFilterListDao.insertAll(entities)
                    }
                    allEntities.addAll(entities)

                    hasMore = response.next != null
                    if (hasMore) {
                        offset += limit
                    }
                }
            }

            Result.success(allEntities)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sync filters by specific category from backend.
     *
     * @param hostname Backend server hostname
     * @param category Filter category (e.g., "GO", "KEGG")
     * @param limit Maximum number of results
     * @return Result with list of synced filter entities
     */
    override suspend fun syncFiltersByCategory(
        hostname: String,
        category: String,
        limit: Int
    ): Result<List<DataFilterListEntity>> = withContext(Dispatchers.IO) {
        try {
            val retrofit = NetworkModule.createRetrofitForHost(hostname, okHttpClient)
            val dynamicApiService = retrofit.create(CurtainApiService::class.java)

            val response = dynamicApiService.getDataFilterListsByCategory(
                category = category,
                limit = limit
            )

            val entities = response.results.mapNotNull { dto ->
                val existing = dataFilterListDao.getFilterByApiId(dto.id)
                if (existing == null) {
                    DataFilterListEntity(
                        apiId = dto.id,
                        name = dto.name,
                        category = dto.category,
                        data = dto.data,
                        isDefault = dto.isDefault,
                        user = dto.user
                    )
                } else {
                    null
                }
            }

            if (entities.isNotEmpty()) {
                dataFilterListDao.insertAll(entities)
            }

            Result.success(entities)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun insertFilter(filter: DataFilterListEntity): Long {
        return dataFilterListDao.insertFilter(filter)
    }

    override suspend fun insertAll(filters: List<DataFilterListEntity>) {
        dataFilterListDao.insertAll(filters)
    }

    override suspend fun updateFilter(filter: DataFilterListEntity) {
        dataFilterListDao.updateFilter(filter)
    }

    override suspend fun deleteFilter(filter: DataFilterListEntity) {
        dataFilterListDao.deleteFilter(filter)
    }

    override suspend fun deleteFilterById(id: Long) {
        dataFilterListDao.deleteFilterById(id)
    }

    override suspend fun deleteFiltersByCategory(category: String) {
        dataFilterListDao.deleteFiltersByCategory(category)
    }

    override suspend fun getFilterCountByCategory(category: String): Int {
        return dataFilterListDao.getFilterCountByCategory(category)
    }

    override suspend fun getFilterCount(): Int {
        return dataFilterListDao.getFilterCount()
    }
}
