package info.proteo.curtain

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for handling UniProt data processing
 * Provides functionality to store and retrieve UniProt information from the ExtraData
 * in the CurtainSettings JSON.
 */
@Singleton
class UniprotService @Inject constructor() {

    var run: Int = 0
    val uniprotPattern: Pattern = Pattern.compile("([OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9]([A-Z][A-Z0-9]{2}[0-9]){1,2})(-\\d+)?")

    var results: MutableMap<String, Any> = mutableMapOf()
    var dataMap: MutableMap<String, Any> = mutableMapOf()
    var db: MutableMap<String, Any> = mutableMapOf()
    var organism: String = ""
    var accMap: MutableMap<String, List<String>> = mutableMapOf()
    var geneNameToAcc: MutableMap<String, Any> = mutableMapOf()

    /**
     * Get UniProt data from a primary accession ID
     *
     * @param accessionId The primary accession ID to look up
     * @return The UniProt data for the accession ID, or null if not found
     */
    fun getUniprotFromPrimary(accessionId: String): MutableMap<String, Any>? {
        // Direct lookup in the database
        if (db.containsKey(accessionId)) {
            return db[accessionId] as MutableMap<String, Any>?
        }

        // Try to find through accession map
        if (accMap.containsKey(accessionId)) {
            val accessList = accMap[accessionId]
            if (accessList != null) {
                for (acc in accessList) {
                    if (dataMap.containsKey(acc)) {
                        val mappedAcc = dataMap[acc]
                        if (mappedAcc != null && db.containsKey(mappedAcc.toString())) {
                            return db[mappedAcc] as MutableMap<String, Any>?
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * Reset all UniProt data
     */
    fun reset() {
        run = 0
        results = mutableMapOf()
        dataMap = mutableMapOf()
        db = mutableMapOf()
        organism = ""
        accMap = mutableMapOf()
        geneNameToAcc = mutableMapOf()
    }

    /**
     * Extract UniProt data from CurtainData extraData
     *
     * @param curtainData The CurtainData containing extraData
     * @return true if successfully extracted, false otherwise
     */
    /*fun extractFromCurtainData(curtainData: CurtainData): Boolean {
        if (curtainData.extraData?.uniprot == null) {
            return false
        }

        return loadFromUniprotExtraData(curtainData.extraData.uniprot)
    }*/

    /**
     * Load UniProt data directly from UniprotExtraData
     *
     * @param uniprotData The UniprotExtraData object
     * @return true if successfully loaded, false otherwise
     */
    fun loadFromUniprotExtraData(uniprotData: UniprotExtraData?): Boolean {
        if (uniprotData == null) {
            return false
        }

        try {
            // Transfer the results
            uniprotData.results.let {
                results = it.toMutableMap()
            }

            // Handle dataMap
            uniprotData.dataMap?.let { mapData ->
                // Process based on how the Map was serialized in JSON
                processMapField(mapData) { processedMap ->
                    dataMap = processedMap
                }
            }

            // Handle db map
            uniprotData.db?.let { dbData ->
                processMapField(dbData) { processedMap ->
                    db = processedMap
                }
            }

            // Set organism
            organism = uniprotData.organism ?: ""

            // Handle accMap
            uniprotData.accMap?.let { accMapData ->
                // Different handling since this is a map of string to string list
                when (accMapData) {
                    is List<*> -> {
                        // If it was serialized as a List of key-value pairs
                        accMap = (accMapData as? List<List<*>>)?.associate { pair ->
                            val key = pair.getOrNull(0) as? String ?: ""
                            val value = when (val listValue = pair.getOrNull(1)) {
                                is List<*> -> listValue.filterIsInstance<String>()
                                else -> emptyList()
                            }
                            key to value
                        }?.toMutableMap() ?: mutableMapOf()
                    }
                    is Map<*, *> -> {
                        // If it was serialized as a JSON object
                        accMap = (accMapData as? Map<String, List<String>>)?.toMutableMap() ?: mutableMapOf()
                    }
                }
            }

            // Handle geneNameToAcc
            uniprotData.geneNameToAcc?.let { geneData ->
                when (geneData) {
                    is Map<*, *> -> {
                        geneNameToAcc = (geneData as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()
                    }
                    is List<*> -> {
                        geneNameToAcc = (geneData as? List<List<*>>)?.associate {
                            (it.getOrNull(0) as? String ?: "") to (it.getOrNull(1) ?: "")
                        }?.toMutableMap() ?: mutableMapOf()
                    }
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Process a field that could be Map or List based on how JavaScript Map was serialized
     *
     * @param mapField The field to process (from JSON deserialization)
     * @param onProcessed Callback to be called with the processed MutableMap
     */
    private inline fun processMapField(mapField: Any, onProcessed: (MutableMap<String, Any>) -> Unit) {
        when (mapField) {
            is List<*> -> {
                // If it was serialized as a List of key-value pairs (JavaScript Array format)
                val processedMap = (mapField as? List<List<*>>)?.associate { pair ->
                    val key = pair.getOrNull(0) as? String ?: ""
                    val value = pair.getOrNull(1) ?: ""
                    key to value
                }?.toMutableMap() ?: mutableMapOf()
                onProcessed(processedMap)
            }
            is Map<*, *> -> {
                // If it was serialized as a JSON object
                val processedMap = (mapField as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()
                onProcessed(processedMap)
            }
        }
    }

    /**
     * Extract UniProt data from a list of ExtraData entries
     *
     * @param extraDataList List of ExtraData entries
     * @return true if successfully extracted, false otherwise
     */
    /*fun extractFromExtraDataList(extraDataList: List<ExtraData>): Boolean {
        try {
            // Find the extraData entry that contains UniProt data
            val uniprotData = extraDataList.find { it.name == "uniprot" && it.type == "json" }
                ?: return false

            // Parse the JSON content
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val adapter = moshi.adapter(UniprotExtraData::class.java)
            val uniprotExtraData = adapter.fromJson(uniprotData.content) ?: return false

            return loadFromUniprotExtraData(uniprotExtraData)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }*/
}

/**
 * Data class for deserializing UniProt data from extraData JSON content
 */
@JsonClass(generateAdapter = true)
data class UniprotExtraData(
    @Json(name = "results") val results: Map<String, Any> = mapOf(),
    @Json(name = "dataMap") val dataMap: Any? = null,
    @Json(name = "db") val db: Any? = null,
    @Json(name = "organism") val organism: String? = "",
    @Json(name = "accMap") val accMap: Any? = null,
    @Json(name = "geneNameToAcc") val geneNameToAcc: Any? = null
)
