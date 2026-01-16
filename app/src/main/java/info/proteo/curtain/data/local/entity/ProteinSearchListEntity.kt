package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import info.proteo.curtain.domain.model.ProteinSearchList

@Entity(tableName = "protein_search_lists")
data class ProteinSearchListEntity(
    @PrimaryKey val id: String,
    val curtainLinkId: String,
    val name: String,
    val proteinIds: String,
    val description: String,
    val createdAt: Long,
    val modifiedAt: Long
) {
    fun toDomain(): ProteinSearchList {
        val gson = Gson()
        val type = object : TypeToken<List<String>>() {}.type
        val proteinIdsList = gson.fromJson<List<String>>(proteinIds, type)

        return ProteinSearchList(
            id = id,
            curtainLinkId = curtainLinkId,
            name = name,
            proteinIds = proteinIdsList,
            description = description,
            createdAt = createdAt,
            modifiedAt = modifiedAt
        )
    }

    companion object {
        fun fromDomain(searchList: ProteinSearchList): ProteinSearchListEntity {
            val gson = Gson()
            val proteinIdsJson = gson.toJson(searchList.proteinIds)

            return ProteinSearchListEntity(
                id = searchList.id,
                curtainLinkId = searchList.curtainLinkId,
                name = searchList.name,
                proteinIds = proteinIdsJson,
                description = searchList.description,
                createdAt = searchList.createdAt,
                modifiedAt = searchList.modifiedAt
            )
        }
    }
}
