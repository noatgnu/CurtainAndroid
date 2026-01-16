package info.proteo.curtain.domain.repository

import info.proteo.curtain.domain.model.ProteinSearchList
import kotlinx.coroutines.flow.Flow

interface ProteinSearchListRepository {

    fun getSearchListsByCurtainId(curtainLinkId: String): Flow<List<ProteinSearchList>>

    suspend fun getSearchListById(id: String): ProteinSearchList?

    suspend fun getSearchListByName(curtainLinkId: String, name: String): ProteinSearchList?

    suspend fun createSearchList(
        curtainLinkId: String,
        name: String,
        proteinIds: List<String>,
        description: String = ""
    ): Result<ProteinSearchList>

    suspend fun updateSearchList(searchList: ProteinSearchList): Result<Unit>

    suspend fun updateName(id: String, name: String): Result<Unit>

    suspend fun updateDescription(id: String, description: String): Result<Unit>

    suspend fun addProteins(id: String, proteinIds: List<String>): Result<Unit>

    suspend fun removeProteins(id: String, proteinIds: List<String>): Result<Unit>

    suspend fun deleteSearchList(id: String): Result<Unit>

    suspend fun deleteAllSearchListsForCurtain(curtainLinkId: String): Result<Unit>

    suspend fun getRecentSearchLists(limit: Int): List<ProteinSearchList>
}
