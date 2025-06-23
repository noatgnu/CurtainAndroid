package info.proteo.curtain

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Top-level data model that matches the full JSON structure used in Curtain
 * This represents the entire JSON including settings, raw data, and extraData
 */
@JsonClass(generateAdapter = true)
data class CurtainData(
    @Json(name = "raw") val raw: String? = null,
    @Json(name = "rawForm") val rawForm: RawForm? = RawForm(),
    @Json(name = "differentialForm") val differentialForm: DifferentialForm? = DifferentialForm(),
    @Json(name = "processed") val processed: String? = null,
    @Json(name = "password") val password: String = "",
    @Json(name = "selections") val selections: Map<String, List<Any>>? = null,
    @Json(name = "selectionsMap") val selectionsMap: Map<String, Any>? = null,
    @Json(name = "selectionsName") val selectionsName: List<String>? = null,
    @Json(name = "settings") val settings: CurtainSettings = CurtainSettings(),
    @Json(name = "fetchUniprot") val fetchUniprot: Boolean = true,
    @Json(name = "annotatedData") val annotatedData: Any? = null,
    @Json(name = "extraData") val extraData: Any? = null,
    @Json(name = "permanent") val permanent: Boolean = false
)


@JsonClass(generateAdapter = true)
data class ExtraData(
    @Json(name = "uniprot") val uniprot: UniprotExtraData? = null,
    @Json(name = "data") val data: DataMapContainer? = null
)

/**
 * Data mapping information stored in the extraData section
 */
@JsonClass(generateAdapter = true)
data class DataMapContainer(
    @Json(name = "dataMap") val dataMap: Any? = null, // Can be Map or {value: [[key, value], ...]}
    @Json(name = "genesMap") val genesMap: Any? = null,
    @Json(name = "primaryIDsmap") val primaryIDsMap: Any? = null,
    @Json(name = "allGenes") val allGenes: List<String>? = null
)

