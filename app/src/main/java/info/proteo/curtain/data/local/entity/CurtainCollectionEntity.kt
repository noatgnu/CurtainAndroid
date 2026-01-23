package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for Curtain collection metadata.
 * A collection groups multiple Curtain sessions together.
 */
@Entity(
    tableName = "curtain_collection",
    indices = [
        Index(value = ["id", "sourceHostname"], unique = true)
    ]
)
data class CurtainCollectionEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val id: Int,
    val name: String,
    val description: String,
    val enable: Boolean,
    val ownerUsername: String,
    val curtainCount: Int,
    val created: Long,
    val updated: Long,
    val sourceHostname: String,
    val frontendURL: String?,
    val lastFetched: Long = System.currentTimeMillis()
)

/**
 * Room entity for Curtain session metadata within collections.
 * Uses linkId as primary key since session metadata is shared across collections.
 * The many-to-many relationship with collections is handled by CollectionSessionCrossRef.
 */
@Entity(
    tableName = "collection_session",
    indices = [
        Index(value = ["sourceHostname"])
    ]
)
data class
CollectionSessionEntity(
    @PrimaryKey
    val linkId: String,
    val id: Int,
    val sessionName: String?,
    val description: String,
    val created: Long,
    val curtainType: String?,
    val sourceHostname: String
)
