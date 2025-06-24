package info.proteo.curtain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for handling DataFilterList operations, combining both local database storage
 * and remote API access.
 */
@Singleton
class DataFilterListRepository @Inject constructor(
    private val dataFilterListDao: DataFilterListDao,
    private val dataFilterListApi: DataFilterListApi
) {
    // Local database operations

    /**
     * Get all saved data filter lists from the local database
     */
    suspend fun getAllDataFilterLists(): List<DataFilterListEntity> {
        return dataFilterListDao.getAll()
    }

    /**
     * Get a specific data filter list by ID from the local database
     */
    suspend fun getDataFilterListById(id: Int): DataFilterListEntity? {
        return dataFilterListDao.getById(id)
    }

    /**
     * Save a data filter list to the local database
     */
    suspend fun saveDataFilterList(dataFilterList: DataFilterListEntity) {
        dataFilterListDao.insert(dataFilterList)
    }

    /**
     * Save multiple data filter lists to the local database
     */
    suspend fun saveDataFilterLists(dataFilterLists: List<DataFilterListEntity>) {
        dataFilterListDao.insertAll(dataFilterLists)
    }

    /**
     * Delete a data filter list from the local database
     */
    suspend fun deleteDataFilterList(dataFilterList: DataFilterListEntity) {
        dataFilterListDao.delete(dataFilterList)
    }

    // Remote API operations

    /**
     * Fetch all data filter lists from the remote API
     * First gets all categories and then fetches lists for each category
     */
    suspend fun fetchAllDataFilterLists(): Result<List<Pair<String, DataFilterList>>> = withContext(Dispatchers.IO) {
        try {
            // First get all categories
            val categoriesResponse = dataFilterListApi.getAllCategory()
            if (!categoriesResponse.isSuccessful || categoriesResponse.body() == null) {
                return@withContext Result.failure(
                    Exception("Error fetching categories: ${categoriesResponse.code()} ${categoriesResponse.message()}")
                )
            }

            val categories = categoriesResponse.body()!!
            val allFilterLists = mutableListOf<Pair<String, DataFilterList>>()

            for (category in categories) {
                var nextUrl: String? = null

                do {
                    val response = if (nextUrl == null) {
                        dataFilterListApi.getDataFilterListsByCategory(category)
                    } else {
                        val offset = extractOffsetFromUrl(nextUrl)
                        val limit = extractLimitFromUrl(nextUrl)
                        dataFilterListApi.getDataFilterListsByCategory(category, limit, offset)
                    }

                    if (response.isSuccessful && response.body() != null) {
                        val paginatedResponse = response.body()!!

                        val filterLists = paginatedResponse.results

                        val listsWithCategory = filterLists.map { filterList ->
                            Pair(category, filterList)
                        }
                        allFilterLists.addAll(listsWithCategory)

                        nextUrl = paginatedResponse.next
                    } else {
                        return@withContext Result.failure(
                            Exception("Error fetching filter lists for category $category: ${response.code()} ${response.message()}")
                        )
                    }
                } while (nextUrl != null)
            }

            Result.success(allFilterLists)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Helper functions to extract pagination parameters from URLs
    private fun extractOffsetFromUrl(url: String): Int? {
        val regex = "offset=(\\d+)".toRegex()
        val matchResult = regex.find(url)
        return matchResult?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractLimitFromUrl(url: String): Int? {
        val regex = "limit=(\\d+)".toRegex()
        val matchResult = regex.find(url)
        return matchResult?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Fetch a specific data filter list from the remote API
     */
    suspend fun fetchDataFilterListById(id: Int): Result<DataFilterList> = withContext(Dispatchers.IO) {
        try {
            val response = dataFilterListApi.getDataFilterList(id)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Error fetching data filter list: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new data filter list on the remote API
     */
    suspend fun createDataFilterList(request: DataFilterListRequest): Result<DataFilterList> = withContext(Dispatchers.IO) {
        try {
            val response = dataFilterListApi.createDataFilterList(request)
            if (response.isSuccessful && response.body() != null) {
                val createdFilterList = response.body()!!
                // Also save to local database
                saveDataFilterList(mapApiToEntity(createdFilterList))
                Result.success(createdFilterList)
            } else {
                Result.failure(Exception("Error creating data filter list: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update an existing data filter list on the remote API
     */
    suspend fun updateDataFilterList(id: Int, request: DataFilterListRequest): Result<DataFilterList> =
        withContext(Dispatchers.IO) {
            try {
                val response = dataFilterListApi.updateDataFilterList(id, request)
                if (response.isSuccessful && response.body() != null) {
                    val updatedFilterList = response.body()!!
                    // Also update in local database
                    saveDataFilterList(mapApiToEntity(updatedFilterList))
                    Result.success(updatedFilterList)
                } else {
                    Result.failure(Exception("Error updating data filter list: ${response.code()} ${response.message()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Delete a data filter list on the remote API
     */
    suspend fun deleteRemoteDataFilterList(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = dataFilterListApi.deleteDataFilterList(id)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Error deleting data filter list: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all available categories from the remote API
     */
    suspend fun getAllCategories(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val response = dataFilterListApi.getAllCategory()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Error fetching categories: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sync data from remote API to local database
     */
    suspend fun syncDataFilterLists() {
        val result = fetchAllDataFilterLists()
        if (result.isSuccess) {
            val apiFilterListsWithCategories = result.getOrNull() ?: return
            val entityFilterLists = apiFilterListsWithCategories.map { (category, filterList) ->
                mapApiToEntity(filterList, category)
            }
            saveDataFilterLists(entityFilterLists)
        }
    }

    /**
     * Fetch data filter lists for a specific category
     */
    suspend fun fetchDataFilterListsByCategory(category: String): Result<List<Pair<String, DataFilterList>>> = withContext(Dispatchers.IO) {
        try {
            val allFilterLists = mutableListOf<Pair<String, DataFilterList>>()
            var nextUrl: String? = null

            do {
                // If this is the first request, use the API method, otherwise extract params from nextUrl
                val response = if (nextUrl == null) {
                    dataFilterListApi.getDataFilterListsByCategory(category)
                } else {
                    // We're assuming your Retrofit setup can handle full URLs
                    // If not, you'll need to extract the query parameters from the URL
                    val offset = extractOffsetFromUrl(nextUrl)
                    val limit = extractLimitFromUrl(nextUrl)
                    dataFilterListApi.getDataFilterListsByCategory(category, limit, offset)
                }

                if (response.isSuccessful && response.body() != null) {
                    val paginatedResponse = response.body()!!

                    // Extract results from the current page
                    val filterLists = paginatedResponse.results.map { filterList ->
                        Pair(category, filterList)
                    }
                    allFilterLists.addAll(filterLists)

                    // Update nextUrl for pagination
                    nextUrl = paginatedResponse.next
                } else {
                    return@withContext Result.failure(
                        Exception("Error fetching filter lists for category $category: ${response.code()} ${response.message()}")
                    )
                }
            } while (nextUrl != null) // Continue until there are no more pages

            Result.success(allFilterLists)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Make mapApiToEntity public for use in ViewModel
    fun mapApiToEntity(apiModel: DataFilterList, category: String): DataFilterListEntity {
        return DataFilterListEntity(
            id = apiModel.id,
            name = apiModel.name,
            data = apiModel.data,
            category = category,
            isDefault = apiModel.isDefault,
            user = null
        )
    }

    // Overload for backward compatibility
    private fun mapApiToEntity(apiModel: DataFilterList): DataFilterListEntity {
        return mapApiToEntity(apiModel, "")
    }

    /**
     * Get all unique categories from the database
     */
    suspend fun getAllCategoriesLocal(): List<String> {
        return dataFilterListDao.getAllCategories()
    }
}
