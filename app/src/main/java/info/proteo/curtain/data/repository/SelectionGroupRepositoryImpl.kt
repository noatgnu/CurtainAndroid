package info.proteo.curtain.data.repository

import com.google.gson.Gson
import info.proteo.curtain.data.local.dao.SelectionGroupDao
import info.proteo.curtain.data.local.entity.SelectionGroupEntity
import info.proteo.curtain.domain.model.SelectionGroup
import info.proteo.curtain.domain.repository.SelectionGroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SelectionGroupRepositoryImpl @Inject constructor(
    private val selectionGroupDao: SelectionGroupDao,
    private val gson: Gson
) : SelectionGroupRepository {

    override fun getSelectionGroupsByCurtainId(curtainLinkId: String): Flow<List<SelectionGroup>> {
        return selectionGroupDao.getSelectionGroupsByCurtainId(curtainLinkId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun getActiveSelectionGroups(curtainLinkId: String): Flow<List<SelectionGroup>> {
        return selectionGroupDao.getActiveSelectionGroups(curtainLinkId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getSelectionGroupById(id: String): SelectionGroup? {
        return selectionGroupDao.getSelectionGroupById(id)?.toDomain()
    }

    override suspend fun createSelectionGroup(
        curtainLinkId: String,
        name: String,
        color: String,
        proteins: List<String>
    ): Result<SelectionGroup> {
        return try {
            val currentTime = System.currentTimeMillis()
            val selectionGroup = SelectionGroup(
                id = UUID.randomUUID().toString(),
                curtainLinkId = curtainLinkId,
                name = name,
                color = color,
                proteins = proteins,
                isActive = true,
                createdAt = currentTime,
                modifiedAt = currentTime
            )

            val entity = SelectionGroupEntity.fromDomain(selectionGroup)
            selectionGroupDao.insertSelectionGroup(entity)

            Result.success(selectionGroup)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateSelectionGroup(selectionGroup: SelectionGroup): Result<Unit> {
        return try {
            val updatedGroup = selectionGroup.copy(modifiedAt = System.currentTimeMillis())
            val entity = SelectionGroupEntity.fromDomain(updatedGroup)
            selectionGroupDao.updateSelectionGroup(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateActiveStatus(id: String, isActive: Boolean): Result<Unit> {
        return try {
            selectionGroupDao.updateActiveStatus(id, isActive, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addProteinsToGroup(id: String, proteins: List<String>): Result<Unit> {
        return try {
            val group = selectionGroupDao.getSelectionGroupById(id)?.toDomain()
                ?: return Result.failure(IllegalArgumentException("Selection group not found"))

            val updatedProteins = (group.proteins + proteins).distinct()
            val proteinsJson = gson.toJson(updatedProteins)

            selectionGroupDao.updateProteins(id, proteinsJson, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeProteinsFromGroup(id: String, proteins: List<String>): Result<Unit> {
        return try {
            val group = selectionGroupDao.getSelectionGroupById(id)?.toDomain()
                ?: return Result.failure(IllegalArgumentException("Selection group not found"))

            val updatedProteins = group.proteins.filter { it !in proteins }
            val proteinsJson = gson.toJson(updatedProteins)

            selectionGroupDao.updateProteins(id, proteinsJson, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateGroupName(id: String, name: String): Result<Unit> {
        return try {
            selectionGroupDao.updateName(id, name, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateGroupColor(id: String, color: String): Result<Unit> {
        return try {
            selectionGroupDao.updateColor(id, color, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSelectionGroup(id: String): Result<Unit> {
        return try {
            selectionGroupDao.deleteSelectionGroupById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteAllSelectionGroupsForCurtain(curtainLinkId: String): Result<Unit> {
        return try {
            selectionGroupDao.deleteAllSelectionGroupsForCurtain(curtainLinkId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getGroupsContainingProtein(curtainLinkId: String, proteinId: String): List<SelectionGroup> {
        return try {
            selectionGroupDao.getGroupsContainingProtein(curtainLinkId, proteinId)
                .map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun bulkSelectProteins(
        curtainLinkId: String,
        groupId: String,
        proteinIds: List<String>
    ): Result<Unit> {
        return addProteinsToGroup(groupId, proteinIds)
    }

    override suspend fun bulkDeselectProteins(
        curtainLinkId: String,
        groupId: String,
        proteinIds: List<String>
    ): Result<Unit> {
        return removeProteinsFromGroup(groupId, proteinIds)
    }
}
