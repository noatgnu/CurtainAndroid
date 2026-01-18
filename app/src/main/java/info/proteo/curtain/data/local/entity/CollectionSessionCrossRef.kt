package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction table for many-to-many relationship between collections and sessions.
 * A session can belong to multiple collections, and a collection can have multiple sessions.
 */
@Entity(
    tableName = "collection_session_cross_ref",
    primaryKeys = ["collectionLocalId", "linkId"],
    foreignKeys = [
        ForeignKey(
            entity = CurtainCollectionEntity::class,
            parentColumns = ["localId"],
            childColumns = ["collectionLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["collectionLocalId"]),
        Index(value = ["linkId"])
    ]
)
data class CollectionSessionCrossRef(
    val collectionLocalId: Long,
    val linkId: String
)
