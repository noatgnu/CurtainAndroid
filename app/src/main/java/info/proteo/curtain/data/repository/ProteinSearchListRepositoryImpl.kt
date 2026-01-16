package info.proteo.curtain.data.repository

import com.google.gson.Gson
import info.proteo.curtain.data.local.dao.ProteinSearchListDao
import info.proteo.curtain.data.local.entity.ProteinSearchListEntity
import info.proteo.curtain.domain.model.ProteinSearchList
import info.proteo.curtain.domain.repository.ProteinSearchListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProteinSearchListRepositoryImpl @Inject constructor(
    private val searchListDao: ProteinSearchListDao,
    private val gson: Gson
) : ProteinSearchListRepository {

    override fun getSearchListsByCurtainId(curtainLinkId: String): Flow<List<ProteinSearchList>> {
        return searchListDao.getSearchListsByCurtainId(curtainLinkId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getSearchListById(id: String): ProteinSearchList? {
        return searchListDao.getSearchListById(id)?.toDomain()
    }

    override suspend fun getSearchListByName(curtainLinkId: String, name: String): ProteinSearchList? {
        return searchListDao.getSearchListByName(curtainLinkId, name)?.toDomain()
    }

    override suspend fun createSearchList(
        curtainLinkId: String,
        name: String,
        proteinIds: List<String>,
        description: String
    ): Result<ProteinSearchList> {
        return try {
            val currentTime = System.currentTimeMillis()
            val searchList = ProteinSearchList(
                id = UUID.randomUUID().toString(),
                curtainLinkId = curtainLinkId,
                name = name,
                proteinIds = proteinIds,
                description = description,
                createdAt = currentTime,
                modifiedAt = currentTime
            )

            val entity = ProteinSearchListEntity.fromDomain(searchList)
            searchListDao.insertSearchList(entity)

            Result.success(searchList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSearchList(searchList: ProteinSearchList): Result<Unit> {
        return try {
            val updatedList = searchList.copy(modifiedAt = System.currentTimeMillis())
            val entity = ProteinSearchListEntity.fromDomain(updatedList)
            searchListDao.updateSearchList(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateName(id: String, name: String): Result<Unit> {
        return try {
            searchListDao.updateName(id, name, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateDescription(id: String, description: String): Result<Unit> {
        return try {
            searchListDao.updateDescription(id, description, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addProteins(id: String, proteinIds: List<String>): Result<Unit> {
        return try {
            val searchList = searchListDao.getSearchListById(id)?.toDomain()
                ?: return Result.failure(IllegalArgumentException("Search list not found"))

            val updatedProteinIds = (searchList.proteinIds + proteinIds).distinct()
            val proteinIdsJson = gson.toJson(updatedProteinIds)

            searchListDao.updateProteinIds(id, proteinIdsJson, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeProteins(id: String, proteinIds: List<String>): Result<Unit> {
        return try {
            val searchList = searchListDao.getSearchListById(id)?.toDomain()
                ?: return Result.failure(IllegalArgumentException("Search list not found"))

            val updatedProteinIds = searchList.proteinIds.filter { it !in proteinIds }
            val proteinIdsJson = gson.toJson(updatedProteinIds)

            searchListDao.updateProteinIds(id, proteinIdsJson, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSearchList(id: String): Result<Unit> {
        return try {
            searchListDao.deleteSearchListById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllSearchListsForCurtain(curtainLinkId: String): Result<Unit> {
        return try {
            searchListDao.deleteAllSearchListsForCurtain(curtainLinkId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getRecentSearchLists(limit: Int): List<ProteinSearchList> {
        return try {
            searchListDao.getRecentSearchLists(limit).map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
