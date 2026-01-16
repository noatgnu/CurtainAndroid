package info.proteo.curtain.domain.repository

import info.proteo.curtain.domain.model.SelectionGroup
import kotlinx.coroutines.flow.Flow

interface SelectionGroupRepository {

    fun getSelectionGroupsByCurtainId(curtainLinkId: String): Flow<List<SelectionGroup>>

    fun getActiveSelectionGroups(curtainLinkId: String): Flow<List<SelectionGroup>>

    suspend fun getSelectionGroupById(id: String): SelectionGroup?

    suspend fun createSelectionGroup(
        curtainLinkId: String,
        name: String,
        color: String,
        proteins: List<String>
    ): Result<SelectionGroup>

    suspend fun updateSelectionGroup(selectionGroup: SelectionGroup): Result<Unit>

    suspend fun updateActiveStatus(id: String, isActive: Boolean): Result<Unit>

    suspend fun addProteinsToGroup(id: String, proteins: List<String>): Result<Unit>

    suspend fun removeProteinsFromGroup(id: String, proteins: List<String>): Result<Unit>

    suspend fun updateGroupName(id: String, name: String): Result<Unit>

    suspend fun updateGroupColor(id: String, color: String): Result<Unit>

    suspend fun deleteSelectionGroup(id: String): Result<Unit>

    suspend fun deleteAllSelectionGroupsForCurtain(curtainLinkId: String): Result<Unit>

    suspend fun getGroupsContainingProtein(curtainLinkId: String, proteinId: String): List<SelectionGroup>

    suspend fun bulkSelectProteins(curtainLinkId: String, groupId: String, proteinIds: List<String>): Result<Unit>

    suspend fun bulkDeselectProteins(curtainLinkId: String, groupId: String, proteinIds: List<String>): Result<Unit>
}
