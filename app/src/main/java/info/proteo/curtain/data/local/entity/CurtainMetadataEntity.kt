package info.proteo.curtain.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "curtain_metadata")
data class CurtainMetadataEntity(
    @PrimaryKey
    val id: Int = 1,
    val settingsJson: String,
    val rawFormJson: String,
    val differentialFormJson: String,
    val selectionsJson: String?,
    val selectionsMapJson: String?,
    val selectedMapJson: String?,
    val selectionsNameJson: String?,
    val extraDataJson: String?,
    val annotatedDataJson: String?,
    val password: String,
    val fetchUniprot: Boolean,
    val permanent: Boolean,
    val bypassUniProt: Boolean
)
