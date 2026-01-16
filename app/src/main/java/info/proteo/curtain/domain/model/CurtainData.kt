package info.proteo.curtain.domain.model

import com.google.gson.annotations.SerializedName

data class CurtainData(
    val raw: String? = null,
    val rawForm: CurtainRawForm = CurtainRawForm(),
    val differentialForm: CurtainDifferentialForm = CurtainDifferentialForm(),
    val processed: String? = null,
    val password: String = "",
    val selections: Map<String, List<Any>>? = null,
    val selectionsMap: Map<String, Any>? = null,
    val selectedMap: Map<String, Map<String, Boolean>>? = null,
    val selectionsName: List<String>? = null,
    @SerializedName("settings")
    private val _settings: CurtainSettings = CurtainSettings(),
    val fetchUniprot: Boolean = true,
    val annotatedData: Any? = null,
    val extraData: ExtraData? = null,
    val permanent: Boolean = false,
    val bypassUniProt: Boolean = false
) {
    val settings: CurtainSettings get() = _settings
    
    val linkId: String get() = settings.currentId
    val description: String get() = settings.description
    
    val curtainType: String get() {
        if (rawForm.samples.isNotEmpty()) {
            return "TP" // Total Proteome
        } else if (differentialForm.comparison.isNotEmpty()) {
            return "CC" // Comparative Analysis
        }
        return "TP"
    }

    val rawDataRowCount: Int get() {
        if (rawForm.samples.isNotEmpty()) {
            return rawForm.samples.size
        }
        return 0
    }

    val differentialDataRowCount: Int get() {
        return 0
    }

    @Deprecated("Use ProteomicsDataService to query database instead", ReplaceWith("proteomicsDataService.getProcessedDataForProtein(linkId, primaryId)"))
    val proteomicsData: Map<String, Any> get() = emptyMap()
    
    // Helper to find protein ID field in row data - use ONLY user-specified column
    private fun findProteinId(row: Map<String, Any>): String? {
        if (differentialForm.primaryIDs.isEmpty()) {
            return null
        }
        
        val primaryIdColumn = differentialForm.primaryIDs
        return (row[primaryIdColumn] as? String)?.takeIf { it.isNotEmpty() }
    }
    
    // Helper method to convert JavaScript Map serialization formats
    private fun convertDataMapToDict(dataMap: Any): Map<String, Any> {
        if (dataMap is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val map = dataMap as Map<String, Any>
            
            // Check for JavaScript Map serialization format: {value: [[key, value], ...]}
            if (map.containsKey("value")) {
                (map["value"] as? List<*>)?.let { 
                    return convertArrayToDict(it)
                }
            }
            return map
        } else if (dataMap is List<*>) {
            return convertArrayToDict(dataMap)
        }
        return emptyMap()
    }
    
    private fun convertArrayToDict(arrayData: List<*>): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        arrayData.forEachIndexed { index, pair ->
            if (pair is List<*> && pair.size >= 2) {
                val key = pair[0] as? String
                val value = pair[1]

                if (index == 0) {
                    android.util.Log.d("CurtainData", "convertArrayToDict: First pair - key=$key, value type=${value?.let { it::class.simpleName }}, value=$value")
                }

                if (key != null && value != null) {
                    result[key] = value
                }
            }
        }
        android.util.Log.d("CurtainData", "convertArrayToDict: Converted ${result.size} entries")
        return result
    }
}

data class CurtainRawForm(
    @SerializedName("_primaryIDs") val primaryIDs: String = "",
    @SerializedName("_samples") val samples: List<String> = emptyList(),
    @SerializedName("_log2") val log2: Boolean = false
)

data class CurtainDifferentialForm(
    @SerializedName("_primaryIDs") val primaryIDs: String = "",
    @SerializedName("_geneNames") val geneNames: String = "",
    @SerializedName("_foldChange") val foldChange: String = "",
    @SerializedName("_transformFC") val transformFC: Boolean = false,
    @SerializedName("_significant") val significant: String = "",
    @SerializedName("_transformSignificant") val transformSignificant: Boolean = false,
    @SerializedName("_comparison") val comparison: String = "",
    @SerializedName("_comparisonSelect") val comparisonSelect: List<String> = emptyList(),
    @SerializedName("_reverseFoldChange") val reverseFoldChange: Boolean = false
)

data class ExtraData(
    val uniprot: UniprotExtraData? = null,
    val data: DataMapContainer? = null
)

data class DataMapContainer(
    val dataMap: Any? = null,
    val genesMap: Any? = null,
    val primaryIDsMap: Any? = null,
    val allGenes: List<String>? = null
)

data class UniprotExtraData(
    val results: Map<String, Any> = emptyMap(),
    val dataMap: Any? = null,
    val db: Any? = null,
    val organism: String? = null,
    val accMap: Any? = null,
    val geneNameToAcc: Any? = null
)
