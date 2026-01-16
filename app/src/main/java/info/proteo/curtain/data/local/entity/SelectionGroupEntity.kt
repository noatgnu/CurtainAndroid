package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import info.proteo.curtain.domain.model.SelectionGroup
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "selection_groups")
data class SelectionGroupEntity(
    @PrimaryKey val id: String,
    val curtainLinkId: String,
    val name: String,
    val color: String,
    val proteins: String,
    val isActive: Boolean,
    val createdAt: Long,
    val modifiedAt: Long
) {
    fun toDomain(): SelectionGroup {
        val gson = Gson()
        val type = object : TypeToken<List<String>>() {}.type
        val proteinsList = gson.fromJson<List<String>>(proteins, type)

        return SelectionGroup(
            id = id,
            curtainLinkId = curtainLinkId,
            name = name,
            color = color,
            proteins = proteinsList,
            isActive = isActive,
            createdAt = createdAt,
            modifiedAt = modifiedAt
        )
    }

    companion object {
        fun fromDomain(selectionGroup: SelectionGroup): SelectionGroupEntity {
            val gson = Gson()
            val proteinsJson = gson.toJson(selectionGroup.proteins)

            return SelectionGroupEntity(
                id = selectionGroup.id,
                curtainLinkId = selectionGroup.curtainLinkId,
                name = selectionGroup.name,
                color = selectionGroup.color,
                proteins = proteinsJson,
                isActive = selectionGroup.isActive,
                createdAt = selectionGroup.createdAt,
                modifiedAt = selectionGroup.modifiedAt
            )
        }
    }
}
