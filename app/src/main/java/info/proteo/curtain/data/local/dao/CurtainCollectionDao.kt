package info.proteo.curtain.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import info.proteo.curtain.data.local.entity.CollectionSessionCrossRef
import info.proteo.curtain.data.local.entity.CollectionSessionEntity
import info.proteo.curtain.data.local.entity.CurtainCollectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurtainCollectionDao {

    @Query("SELECT * FROM curtain_collection ORDER BY updated DESC")
    fun getAllCollections(): Flow<List<CurtainCollectionEntity>>

    @Query("SELECT * FROM curtain_collection WHERE sourceHostname = :hostname ORDER BY updated DESC")
    fun getCollectionsByHostname(hostname: String): Flow<List<CurtainCollectionEntity>>

    @Query("SELECT * FROM curtain_collection WHERE localId = :localId")
    suspend fun getCollectionByLocalId(localId: Long): CurtainCollectionEntity?

    @Query("SELECT * FROM curtain_collection WHERE id = :id AND sourceHostname = :hostname")
    suspend fun getCollectionByIdAndHostname(id: Int, hostname: String): CurtainCollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CurtainCollectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollections(collections: List<CurtainCollectionEntity>)

    @Update
    suspend fun updateCollection(collection: CurtainCollectionEntity)

    @Query("DELETE FROM curtain_collection WHERE localId = :localId")
    suspend fun deleteCollectionByLocalId(localId: Long)

    @Query("DELETE FROM curtain_collection WHERE id = :id AND sourceHostname = :hostname")
    suspend fun deleteCollectionByIdAndHostname(id: Int, hostname: String)

    @Query("""
        SELECT cs.* FROM collection_session cs
        INNER JOIN collection_session_cross_ref ref ON cs.linkId = ref.linkId
        WHERE ref.collectionLocalId = :collectionLocalId
        ORDER BY cs.created DESC
    """)
    fun getSessionsByCollectionId(collectionLocalId: Long): Flow<List<CollectionSessionEntity>>

    @Query("""
        SELECT cs.* FROM collection_session cs
        INNER JOIN collection_session_cross_ref ref ON cs.linkId = ref.linkId
        WHERE ref.collectionLocalId = :collectionLocalId
        ORDER BY cs.created DESC
    """)
    suspend fun getSessionsByCollectionIdSync(collectionLocalId: Long): List<CollectionSessionEntity>

    @Query("SELECT * FROM collection_session WHERE linkId = :linkId")
    suspend fun getSessionByLinkId(linkId: String): CollectionSessionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: CollectionSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessions(sessions: List<CollectionSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: CollectionSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessions(sessions: List<CollectionSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: CollectionSessionCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(crossRefs: List<CollectionSessionCrossRef>)

    @Query("DELETE FROM collection_session_cross_ref WHERE collectionLocalId = :collectionLocalId")
    suspend fun deleteCrossRefsByCollectionId(collectionLocalId: Long)

    @Query("DELETE FROM collection_session_cross_ref WHERE linkId = :linkId AND collectionLocalId = :collectionLocalId")
    suspend fun deleteCrossRef(linkId: String, collectionLocalId: Long)

    @Query("""
        SELECT collectionLocalId FROM collection_session_cross_ref WHERE linkId = :linkId
    """)
    suspend fun getCollectionIdsForSession(linkId: String): List<Long>

    @Transaction
    suspend fun insertCollectionWithSessions(
        collection: CurtainCollectionEntity,
        sessions: List<CollectionSessionEntity>
    ): Long {
        val collectionLocalId = insertCollection(collection)
        insertSessions(sessions)
        val crossRefs = sessions.map { CollectionSessionCrossRef(collectionLocalId, it.linkId) }
        insertCrossRefs(crossRefs)
        return collectionLocalId
    }

    @Transaction
    suspend fun replaceCollectionSessions(
        collectionLocalId: Long,
        sessions: List<CollectionSessionEntity>
    ) {
        deleteCrossRefsByCollectionId(collectionLocalId)
        upsertSessions(sessions)
        val crossRefs = sessions.map { CollectionSessionCrossRef(collectionLocalId, it.linkId) }
        insertCrossRefs(crossRefs)
        deleteOrphanedSessions()
    }

    @Transaction
    suspend fun cleanupOrphanedSessions() {
        deleteOrphanedSessions()
    }

    @Query("""
        DELETE FROM collection_session
        WHERE linkId NOT IN (SELECT DISTINCT linkId FROM collection_session_cross_ref)
    """)
    suspend fun deleteOrphanedSessions()

    @Query("SELECT COUNT(*) FROM curtain_collection")
    suspend fun getCollectionCount(): Int

    @Query("SELECT * FROM curtain_collection WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY updated DESC")
    fun searchCollections(query: String): Flow<List<CurtainCollectionEntity>>
}
