package info.proteo.curtain.data.repository

import info.proteo.curtain.data.local.dao.CurtainCollectionDao
import info.proteo.curtain.data.local.entity.CollectionSessionEntity
import info.proteo.curtain.data.local.entity.CurtainCollectionEntity
import info.proteo.curtain.data.remote.api.CurtainApiService
import info.proteo.curtain.data.remote.model.CurtainCollectionDto
import info.proteo.curtain.di.NetworkModule
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurtainCollectionRepository @Inject constructor(
    private val collectionDao: CurtainCollectionDao
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    fun getAllCollections(): Flow<List<CurtainCollectionEntity>> {
        return collectionDao.getAllCollections()
    }

    fun getCollectionsByHostname(hostname: String): Flow<List<CurtainCollectionEntity>> {
        return collectionDao.getCollectionsByHostname(hostname)
    }

    fun getSessionsByCollectionId(collectionLocalId: Long): Flow<List<CollectionSessionEntity>> {
        return collectionDao.getSessionsByCollectionId(collectionLocalId)
    }

    suspend fun getSessionsByCollectionIdSync(collectionLocalId: Long): List<CollectionSessionEntity> {
        return collectionDao.getSessionsByCollectionIdSync(collectionLocalId)
    }

    fun searchCollections(query: String): Flow<List<CurtainCollectionEntity>> {
        return collectionDao.searchCollections(query)
    }

    suspend fun fetchCollectionFromApi(
        apiService: CurtainApiService,
        collectionId: Int,
        hostname: String,
        frontendURL: String?,
        curtainType: String? = "TP"
    ): Result<CurtainCollectionEntity> {
        return try {
            val dto = apiService.getCollectionById(collectionId, curtainType)
            val entity = dtoToEntity(dto, hostname, frontendURL)
            val sessions = dto.accessibleCurtains.map { curtain ->
                CollectionSessionEntity(
                    linkId = curtain.linkId,
                    id = curtain.id,
                    sessionName = curtain.name,
                    description = curtain.description,
                    created = parseDate(curtain.created),
                    curtainType = curtain.curtainType,
                    sourceHostname = hostname
                )
            }
            val localId = collectionDao.insertCollectionWithSessions(entity, sessions)
            val savedEntity = collectionDao.getCollectionByLocalId(localId)
            Result.success(savedEntity ?: entity.copy(localId = localId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCollectionsFromApi(
        apiService: CurtainApiService,
        hostname: String,
        frontendURL: String?,
        limit: Int = 10,
        offset: Int = 0,
        search: String? = null
    ): Result<List<CurtainCollectionEntity>> {
        return try {
            val response = apiService.getCollections(limit, offset, search)
            val entities = response.results.map { dto ->
                dtoToEntity(dto, hostname, frontendURL)
            }
            entities.forEach { entity ->
                val dto = response.results.find { it.id == entity.id }
                if (dto != null) {
                    val sessions = dto.accessibleCurtains.map { curtain ->
                        CollectionSessionEntity(
                            linkId = curtain.linkId,
                            id = curtain.id,
                            sessionName = curtain.name,
                            description = curtain.description,
                            created = parseDate(curtain.created),
                            curtainType = curtain.curtainType,
                            sourceHostname = hostname
                        )
                    }
                    collectionDao.insertCollectionWithSessions(entity, sessions)
                }
            }
            Result.success(entities)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshCollection(
        apiService: CurtainApiService,
        collectionLocalId: Long,
        curtainType: String? = "TP"
    ): Result<CurtainCollectionEntity> {
        val existingCollection = collectionDao.getCollectionByLocalId(collectionLocalId)
            ?: return Result.failure(Exception("Collection not found"))

        return try {
            val dto = apiService.getCollectionById(existingCollection.id, curtainType)
            val updatedEntity = dtoToEntity(dto, existingCollection.sourceHostname, existingCollection.frontendURL)
                .copy(localId = collectionLocalId)

            val sessions = dto.accessibleCurtains.map { curtain ->
                CollectionSessionEntity(
                    linkId = curtain.linkId,
                    id = curtain.id,
                    sessionName = curtain.name,
                    description = curtain.description,
                    created = parseDate(curtain.created),
                    curtainType = curtain.curtainType,
                    sourceHostname = existingCollection.sourceHostname
                )
            }

            collectionDao.updateCollection(updatedEntity)
            collectionDao.replaceCollectionSessions(collectionLocalId, sessions)
            Result.success(updatedEntity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCollection(collectionLocalId: Long) {
        collectionDao.deleteCollectionByLocalId(collectionLocalId)
        collectionDao.cleanupOrphanedSessions()
    }

    suspend fun getCollectionByLocalId(localId: Long): CurtainCollectionEntity? {
        return collectionDao.getCollectionByLocalId(localId)
    }

    suspend fun getSessionByLinkId(linkId: String): CollectionSessionEntity? {
        return collectionDao.getSessionByLinkId(linkId)
    }

    suspend fun getCollectionIdsForSession(linkId: String): List<Long> {
        return collectionDao.getCollectionIdsForSession(linkId)
    }

    private fun dtoToEntity(
        dto: CurtainCollectionDto,
        hostname: String,
        frontendURL: String?
    ): CurtainCollectionEntity {
        return CurtainCollectionEntity(
            id = dto.id,
            name = dto.name,
            description = dto.description,
            enable = dto.enable,
            ownerUsername = dto.ownerUsername,
            curtainCount = dto.accessibleCurtains.size,
            created = parseDate(dto.created),
            updated = parseDate(dto.updated),
            sourceHostname = hostname,
            frontendURL = frontendURL,
            lastFetched = System.currentTimeMillis()
        )
    }

    private fun parseDate(dateString: String): Long {
        return try {
            dateFormat.parse(dateString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
