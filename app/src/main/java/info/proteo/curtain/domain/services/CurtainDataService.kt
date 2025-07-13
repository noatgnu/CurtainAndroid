package info.proteo.curtain

import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import info.proteo.curtain.utils.NavigationUtil
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import java.io.BufferedReader
import java.io.Reader
import org.jetbrains.kotlinx.dataframe.api.getColumn
import kotlin.collections.get
import kotlin.text.contains
import kotlin.math.log2
import kotlin.math.log10

class CurtainDataService {
    // State and event flows
    private val _loadDataTrigger = MutableSharedFlow<Boolean>()
    val loadDataTrigger: SharedFlow<Boolean> = _loadDataTrigger

    private val _externalBarChartDownloadTrigger = MutableSharedFlow<Boolean>()
    val externalBarChartDownloadTrigger: SharedFlow<Boolean> = _externalBarChartDownloadTrigger

    private val _stringDBColorMapSubject = MutableSharedFlow<Boolean>()
    val stringDBColorMapSubject: SharedFlow<Boolean> = _stringDBColorMapSubject

    private val _interactomeDBColorMapSubject = MutableSharedFlow<Boolean>()
    val interactomeDBColorMapSubject: SharedFlow<Boolean> = _interactomeDBColorMapSubject

    private val _volcanoAdditionalShapesSubject = MutableSharedFlow<Boolean>()
    val volcanoAdditionalShapesSubject: SharedFlow<Boolean> = _volcanoAdditionalShapesSubject

    private val _downloadProgress = MutableSharedFlow<Int>()
    val downloadProgress: SharedFlow<Int> = _downloadProgress

    private val _uploadProgress = MutableSharedFlow<Int>()
    val uploadProgress: SharedFlow<Int> = _uploadProgress

    private val _finishedProcessingData = MutableStateFlow<Boolean>(false)
    val finishedProcessingData: StateFlow<Boolean> = _finishedProcessingData

    private val _selectionUpdateTrigger = MutableSharedFlow<Boolean>()
    val selectionUpdateTrigger: SharedFlow<Boolean> = _selectionUpdateTrigger

    // State fields
    var instructorMode: Boolean = false
    var tempLink: Boolean = false
    var bypassUniProt: Boolean = false
    var draftDataCiteCount: Int = 0
    var colorMap: Map<String, Any> = mapOf()
    var session: CurtainSession? = null

    // Data structures
    val dataMap = mutableMapOf<String, String>()
    val uniprotData = UniprotData()
    val curtainData = AppData()
    var curtainSettings = CurtainSettings()

    // Data deserializers
    private val deserializer = CurtainSettingsDeserializer()
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /**
     * Restores settings from a JSON object
     * @param jsonObject The object containing settings to restore
     */
    suspend fun restoreSettings(jsonObject: Any) {
        try {

            // If direct deserialization fails, try to process the raw JSON
            val dataObject = when (jsonObject) {
                is String -> parseJsonObject(jsonObject)
                else -> jsonObject as? Map<*, *> ?: return
            }
            Log.d("CurtainDataService", "Restoring settings from JSON object: ${dataObject["settings"]}")
            Log.d("CurtainDataService", dataObject["settings"]?.javaClass?.simpleName ?: "null")
            @Suppress("UNCHECKED_CAST")
            val settingsData = when (val settings = dataObject["settings"]) {
                is String -> deserializer.deserializeCurtainSettings(settings)
                is Map<*, *> -> {
                    val settingsMap = settings as Map<String, Any?>
                    manualDeserializeSettings(settingsMap)
                }
                else -> CurtainSettings()
            } ?: CurtainSettings()
            Log.d("CurtainDataService", "Restored settings: ${settingsData.colorMap}")
            curtainSettings = settingsData
            // Process extraData if available (for UniProt data)
            if (dataObject["fetchUniprot"] == true) {
                @Suppress("UNCHECKED_CAST")
                val extraDataObj = when (val extraData = dataObject["extraData"]) {
                    is String -> parseJsonObject(extraData)
                    is Map<*, *> -> extraData as Map<String, Any>
                    else -> null
                }

                extraDataObj?.let { extraData ->
                    @Suppress("UNCHECKED_CAST")
                    val uniprotObj = extraData["uniprot"] as? Map<String, Any>
                    uniprotObj?.let { uniprot ->
                        // Process Uniprot data
                        uniprotData.results = convertToMutableMap(uniprot["results"]) as? Map<String, Any> ?: mapOf()
                        uniprotData.dataMap = convertToMutableMap(uniprot["dataMap"])
                        uniprotData.accMap = convertToMutableAccMap(uniprot["accMap"])
                        uniprotData.db = convertToMutableMap(uniprot["db"])
                        uniprotData.organism = uniprot["organism"] as? String ?: ""
                        uniprotData.geneNameToAcc = convertToMutableMap(uniprot["geneNameToAcc"])
                    }
                    Log.d("CurtainDataService", "Uniprot DB Size: ${uniprotData.db.size}")

                    @Suppress("UNCHECKED_CAST")
                    val dataObj = extraData["data"] as? Map<String, Any>
                    dataObj?.let { data ->
                        // Process app data
                        this.curtainData.dataMap = convertToMutableMap(data["dataMap"])
                        this.curtainData.genesMap = data["genesMap"]
                        this.curtainData.primaryIDsMap = data["primaryIDsmap"]
                        this.curtainData.allGenes = data["allGenes"] as? List<String> ?: listOf()
                    }

                    this.curtainData.bypassUniProt = true
                }
            }

            // Process raw and differential forms if available
            @Suppress("UNCHECKED_CAST")
            val rawFormData = dataObject["rawForm"] as? Map<String, Any>
            if (rawFormData != null) {
                // Create new RawForm instance instead of trying to modify val properties
                this.curtainData.rawForm = RawForm(
                    primaryIDs = rawFormData["_primaryIDs"] as? String ?: "",
                    samples = rawFormData["_samples"] as? List<String> ?: listOf(),
                    log2 = rawFormData["_log2"] as? Boolean ?: false
                )
            }
            Log.d("CurtainDataService", "Restoring settings: ${this.curtainData.rawForm}")
            @Suppress("UNCHECKED_CAST")
            val diffFormData = dataObject["differentialForm"] as? Map<String, Any>
            if (diffFormData != null) {
                // Create new DifferentialForm instance instead of trying to modify val properties
                @Suppress("UNCHECKED_CAST")
                val compSelectValue = diffFormData["_comparisonSelect"]
                val comparisonSelectList = when {
                    compSelectValue is String -> listOf(compSelectValue)
                    compSelectValue is List<*> -> compSelectValue as List<String>
                    else -> listOf()
                }

                this.curtainData.differentialForm = DifferentialForm(
                    primaryIDs = diffFormData["_primaryIDs"] as? String ?: "",
                    geneNames = diffFormData["_geneNames"] as? String ?: "",
                    foldChange = diffFormData["_foldChange"] as? String ?: "",
                    transformFC = diffFormData["_transformFC"] as? Boolean ?: false,
                    significant = diffFormData["_significant"] as? String ?: "",
                    transformSignificant = diffFormData["_transformSignificant"] as? Boolean ?: false,
                    comparison = diffFormData["_comparison"] as? String ?: "",
                    comparisonSelect = comparisonSelectList,
                    reverseFoldChange = diffFormData["_reverseFoldChange"] as? Boolean ?: false
                )
            }

            // Version handling
            val version = settingsData.version
            if (version == 2.0) {
                @Suppress("UNCHECKED_CAST")
                this.curtainData.selected = dataObject["selections"] as? Map<String, List<Any>> ?: mapOf()

                @Suppress("UNCHECKED_CAST")
                this.curtainData.selectedMap = dataObject["selectionsMap"] as? Map<String, Map<String, Boolean>> ?: mapOf()

                @Suppress("UNCHECKED_CAST")
                this.curtainData.selectOperationNames = dataObject["selectionsName"] as? List<String> ?: listOf()
            }

            // Process raw and processed data strings
            val rawString = dataObject["raw"] as? String ?: ""
            if (rawString.isNotEmpty()) {
                this.curtainData.raw = if (rawString.contains("\t")) {
                    InputFile(filename = "rawFile.txt", originalFile = rawString)
                } else {
                    InputFile(filename = "rawFile.txt", originalFile = rawString)
                }
            }

            val processedString = dataObject["processed"] as? String ?: ""
            if (processedString.isNotEmpty()) {
                this.curtainData.differential = if (processedString.contains("\t")) {
                    InputFile(filename = "processedFile.txt", originalFile = processedString)
                } else {
                    InputFile(filename = "processedFile.txt", originalFile = processedString)
                }
            }

            this.curtainData.fetchUniprot = settingsData.fetchUniprot

            processDataAfterImport()

        } catch (e: Exception) {
            Log.e("CurtainDataService", "Error restoring settings: ${e.message}", e)
        }
    }


    /**
     * Parses a JSON string into a Map structure
     */
    private fun parseJsonObject(jsonString: String): Map<String, Any> {
        return try {
            val result = mutableMapOf<String, Any>()

            // Use Moshi or stream processing for large objects instead of creating JSONObject
            // which can cause memory issues with large data
            val jsonAdapter: JsonAdapter<Map<String, Any>> = moshi.adapter(
                Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            )

            // Parse directly to Map without intermediate JSONObject
            val parsedMap = jsonAdapter.fromJson(jsonString) ?: mapOf()

            // Process the map without creating additional large string copies
            for ((key, value) in parsedMap) {
                result[key] = when (value) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        value as Map<String, Any>
                    }
                    is List<*> -> value
                    else -> value
                }
            }

            result
        } catch (e: Exception) {
            Log.e("CurtainDataService", "Error parsing JSON: ${e.message}", e)
            mapOf()
        }
    }

    /**
     * Parses a JSON array string into a List structure
     */
    private fun parseJsonArray(jsonArray: JSONArray): List<Any> {
        val result = mutableListOf<Any>()
        for (i in 0 until jsonArray.length()) {
            when (val value = jsonArray.get(i)) {
                is JSONObject -> result.add(parseJsonObject(value.toString()))
                is JSONArray -> result.add(parseJsonArray(value))
                else -> result.add(value)
            }
        }
        return result
    }

    private fun convertToMutableAccMap(data: Any?): MutableMap<String, List<String>> {
        val mapValue = (data as? Map<*, *>)?.get("value")
        when (mapValue) {
            is List<*> -> {
                val result = mutableMapOf<String, List<String>>()
                mapValue.forEachIndexed { index, pair ->
                    if (pair is List<*> && pair.size >= 2) {
                        val key = pair[0]?.toString()
                        val valueList = pair[1] as? List<String>
                        if (key != null && valueList != null) {
                            result[key] = valueList
                        }
                    }
                }
                return result
            }
            else -> {
                Log.w("CurtainDataService", "No valid accMap value array found, returning empty map")
                return mutableMapOf()
            }
        }
    }

    /**
     * Convert various map representations to MutableMap
     */
    @Suppress("UNCHECKED_CAST")
    private fun convertToMutableMap(data: Any?): MutableMap<String, Any> {
        val mapValue = (data as? Map<*, *>)?.get("value")
        when (mapValue) {
            is List<*> -> {
                val result = mutableMapOf<String, Any>()
                mapValue.forEachIndexed { index, pair ->
                    if (pair is List<*> && pair.size >= 2) {
                        val key = pair[0]?.toString()
                        val value = pair[1]
                        if (key != null && value != null) {
                            result[key] = value
                        }
                    }
                }
                Log.d("CurtainDataService", "Final result size: ${result.size}")
                return result
            }
            else -> {
                Log.w("CurtainDataService", "No valid value array found, returning the full map or empty map")
                return data as MutableMap<String, Any>? ?: mutableMapOf()
            }
        }
    }

    // Current activity reference for navigation
    private var currentActivity: android.app.Activity? = null

    // Function to set current activity for navigation
    fun setCurrentActivity(activity: android.app.Activity?) {
        this.currentActivity = activity
    }

    // Navigation event flow
    private val _navigationEvent = MutableSharedFlow<NavigationDestination>()
    val navigationEvent: SharedFlow<NavigationDestination> = _navigationEvent

    // Navigation destinations
    enum class NavigationDestination {
        CURTAIN_DETAILS
    }

    // Function to trigger navigation after data is processed
    private suspend fun navigateAfterProcessing() {
        // Navigate to details screen using our utility
        _navigationEvent.emit(NavigationDestination.CURTAIN_DETAILS)
    }

    // Function to actually perform the navigation
    fun performNavigation(destination: NavigationDestination) {
        when (destination) {
            NavigationDestination.CURTAIN_DETAILS -> {
                NavigationUtil.navigateToCurtainDetails(currentActivity)
            }
        }
    }

    /**
     * Restores settings from a Reader, which allows for processing large files
     * without loading the entire content into memory at once
     */
    suspend fun restoreSettingsFromReader(reader: Reader) {
        try {
            // Create a buffered reader for efficiency
            val bufferedReader = if (reader is BufferedReader) reader else BufferedReader(reader)

            // Read the content in chunks to avoid OOM errors
            val jsonContent = StringBuilder()
            val charBuffer = CharArray(8192) // 8KB buffer
            var bytesRead: Int

            // Read the file in chunks
            while (bufferedReader.read(charBuffer).also { bytesRead = it } != -1) {
                jsonContent.append(charBuffer, 0, bytesRead)
            }

            // Process the content using the existing method
            // This avoids creating a large string at once by building it incrementally
            restoreSettings(jsonContent.toString())

            Log.d("CurtainDataService", "Successfully parsed large JSON file")
        } catch (e: Exception) {
            Log.e("CurtainDataService", "Error parsing JSON from reader: ${e.message}", e)
            throw e
        }
    }

    /**
     * Processes differential data after loading from JSON
     * This mirrors the JavaScript "processDifferentialFile" functionality
     */
    suspend fun processDifferentialData() {
        try {
            if (curtainData.differential.originalFile.isEmpty()) {
                return
            }

            // Handle the comparison field defaulting
            if (curtainData.differentialForm.comparison.isEmpty() ||
                curtainData.differentialForm.comparison == "CurtainSetComparison") {

                curtainData.differentialForm = curtainData.differentialForm.copy(
                    comparison = "CurtainSetComparison",
                    comparisonSelect = listOf("1")
                )
            }

            // Ensure comparisonSelect has a value
            if (curtainData.differentialForm.comparisonSelect.isEmpty()) {
                val column = curtainData.differentialForm.comparison
                val firstComparison = if (column.isNotEmpty() &&
                    curtainData.differential.df.rowsCount() > 0) {
                    val columnData = curtainData.differential.df.getColumn(column)
                    columnData.get(0)?.toString() ?: "1"
                } else {
                    "1"
                }

                curtainData.differentialForm = curtainData.differentialForm.copy(
                    comparisonSelect = listOf(firstComparison)
                )
            }

            // Process the numeric values in the differential data
            val modifiedData = mutableListOf<Map<String, Any>>()
            val comparisonColumn = curtainData.differentialForm.comparison
            val selectedComparisons = curtainData.differentialForm.comparisonSelect

            // Define essential columns to keep
            val essentialColumns = mutableSetOf<String>()

            // Add required columns for visualization and analysis
            val fcColumn = curtainData.differentialForm.foldChange
            val sigColumn = curtainData.differentialForm.significant
            val idColumn = curtainData.differentialForm.primaryIDs
            val geneNameColumn = curtainData.differentialForm.geneNames
            val customTextColumn = curtainSettings.customVolcanoTextCol

            // Only add columns that are actually present
            listOfNotNull(
                fcColumn.takeIf { it.isNotEmpty() },
                sigColumn.takeIf { it.isNotEmpty() },
                idColumn.takeIf { it.isNotEmpty() },
                geneNameColumn.takeIf { it.isNotEmpty() },
                customTextColumn.takeIf { it?.isNotEmpty() == true },
                comparisonColumn.takeIf { it.isNotEmpty() }
            ).forEach { essentialColumns.add(it) }

            for (rowIndex in 0 until curtainData.differential.df.rowsCount()) {
                // Check if this row should be included based on comparison value
                if (comparisonColumn.isNotEmpty() && selectedComparisons.isNotEmpty()) {
                    val compValue = curtainData.differential.df.getColumn(comparisonColumn).get(rowIndex)?.toString()?.trim() ?: ""
                    if (compValue !in selectedComparisons) {
                        continue
                    }
                }

                // Create a sparse row map with only essential columns
                val rowMap = mutableMapOf<String, Any>()

                // Add only essential columns to the rowMap
                for (column in essentialColumns) {
                    val value = curtainData.differential.df.getColumn(column).get(rowIndex) ?: ""
                    rowMap[column] = value
                }

                // Process fold change values
                if (fcColumn.isNotEmpty() && rowMap.containsKey(fcColumn)) {
                    var fcValue = (rowMap[fcColumn]?.toString()?.toDoubleOrNull() ?: 0.0)

                    if (curtainData.differentialForm.transformFC) {
                        fcValue = if (fcValue > 0) log2(fcValue) else 0.0
                    }
                    if (curtainData.differentialForm.reverseFoldChange) {
                        fcValue = -fcValue
                    }

                    rowMap[fcColumn] = fcValue
                }

                // Process significance values
                if (sigColumn.isNotEmpty() && rowMap.containsKey(sigColumn)) {
                    var sigValue = (rowMap[sigColumn]?.toString()?.toDoubleOrNull() ?: 0.0)

                    if (curtainData.differentialForm.transformSignificant) {
                        sigValue = if (sigValue > 0) -log10(sigValue) else 0.0
                    }

                    rowMap[sigColumn] = sigValue
                }

                modifiedData.add(rowMap)
            }

            // Store the processed data
            curtainData.dataMap["processedDifferentialData"] = modifiedData

            // Log memory-efficient info
            Log.d("CurtainDataService", "Processed ${modifiedData.size} differential data rows with ${essentialColumns.size} essential columns")
        } catch (e: Exception) {
            Log.e("CurtainDataService", "Error processing differential data: ${e.message}", e)
        }
    }


    /**
     * Processes raw data after loading from JSON
     * This mirrors the JavaScript "processRawFile" functionality
     */
    suspend fun processRawData() {
        try {
            if (curtainData.raw.originalFile.isEmpty()) {
                return
            }

            val samples = curtainData.rawForm.samples
            val conditions = mutableListOf<String>()
            val colorMap = mutableMapOf<String, String>()
            var colorPosition = 0

            // Create sample mapping and process conditions
            val sampleMap = mutableMapOf<String, Map<String, String>>()

            for (sample in samples) {
                val parts = sample.split(".")
                val replicate = parts.lastOrNull() ?: ""
                val condition = if (parts.size > 1) parts.dropLast(1).joinToString(".") else ""

                // Use existing condition if available in sampleMap
                val actualCondition = curtainSettings.sampleMap[sample]?.get("condition") ?: condition

                // Add new conditions to the list and assign colors
                if (actualCondition.isNotEmpty() && !conditions.contains(actualCondition)) {
                    conditions.add(actualCondition)

                    // Cycle through colors
                    if (colorPosition >= curtainSettings.defaultColorList.size) {
                        colorPosition = 0
                    }
                    colorMap[actualCondition] = curtainSettings.defaultColorList[colorPosition]
                    colorPosition++
                }

                // Get current sample order for this condition or create new list
                val currentSampleOrder = curtainSettings.sampleOrder[actualCondition]?.toTypedMutableList() ?: mutableListOf()

                // Add sample if not already in the list
                if (!currentSampleOrder.contains(sample)) {
                    currentSampleOrder.add(sample)
                }

                // Build new complete sampleOrder map
                val newSampleOrder = curtainSettings.sampleOrder.toMutableMap()
                newSampleOrder[actualCondition] = currentSampleOrder

                // Using reflection to update the val property is not recommended
                // Instead, store the updated map separately and use it later

                // Create sample visibility map
                val newSampleVisible = curtainSettings.sampleVisible.toMutableMap()
                if (!newSampleVisible.containsKey(sample)) {
                    newSampleVisible[sample] = true
                }

                // Update the sample visibility map
                curtainSettings = curtainSettings.copy(
                    sampleOrder = newSampleOrder,
                    sampleVisible = newSampleVisible
                )

                // Add to sample map
                sampleMap[sample] = mapOf(
                    "replicate" to replicate,
                    "condition" to actualCondition,
                    "name" to sample
                )
            }
            if (sampleMap.isNotEmpty()) {
                val updatedMap = if (curtainSettings.sampleMap.isEmpty()) {
                    sampleMap
                } else {
                    val mergedMap = curtainSettings.sampleMap.toMutableMap()

                    // Remove missing samples
                    mergedMap.keys.toList().forEach { sample ->
                        if (sample !in sampleMap.keys) {
                            mergedMap.remove(sample)
                        }
                    }

                    // Add new samples
                    sampleMap.forEach { (sample, info) ->
                        if (sample !in mergedMap.keys) {
                            mergedMap[sample] = info
                        }
                    }
                    mergedMap
                }

                val sampleMapField = CurtainSettings::class.java.getDeclaredField("sampleMap")
                sampleMapField.isAccessible = true
                sampleMapField.set(curtainSettings, updatedMap)
            }

            // Merge sample map with settings
            if (curtainSettings.sampleMap.isEmpty()) {
                curtainSettings = curtainSettings.copy(
                    sampleMap = sampleMap
                )
            } else {
                // Remove missing samples
                val currentSampleKeys = sampleMap.keys
                curtainSettings = curtainSettings.copy(
                    sampleMap = curtainSettings.sampleMap.filterKeys { it in currentSampleKeys }.toMutableMap()
                )

                val mutableSampleMap = curtainSettings.sampleMap.toMutableMap()
                for ((sample, info) in sampleMap) {
                    if (!curtainSettings.sampleMap.containsKey(sample)) {
                        mutableSampleMap[sample] = info
                    }
                }
                curtainSettings = curtainSettings.copy(
                    sampleMap = mutableSampleMap
                )
            }

            // Clean up sample visibility for removed samples
            curtainSettings = curtainSettings.copy(
                sampleVisible = curtainSettings.sampleVisible.filterKeys { it in sampleMap.keys }.toMutableMap()
            )

            // Update color map
            val updatedColorMap =mutableMapOf<String, String>()
            for ((selection, color) in colorMap) {
                if (!curtainSettings.colorMap.containsKey(selection)) {
                    updatedColorMap[selection] = color
                }
            }

            // Merge color map with existing settings
            if (curtainSettings.colorMap.isEmpty()) {
                curtainSettings = curtainSettings.copy(
                    colorMap = updatedColorMap
                )
            } else {

                val newCombinedMap = curtainSettings.colorMap.toMutableMap()
                for ((condition, color) in updatedColorMap) {
                    if (!curtainSettings.colorMap.containsKey(condition)) {
                        newCombinedMap[condition] = color
                    }
                }
                curtainSettings = curtainSettings.copy(
                    colorMap = newCombinedMap
                )
            }

            Log.d("CurtainDataService", "Color map after processing: ${curtainSettings.colorMap}")

            // Update condition order
            if (curtainSettings.conditionOrder.isEmpty()) {
                // Use copy to create a new instance with updated conditionOrder
                curtainSettings = curtainSettings.copy(
                    conditionOrder = conditions
                )
            } else {
                // Create new list with filtered conditions
                val updatedConditionOrder = mutableListOf<String>()

                // Keep existing order for conditions still present
                for (condition in curtainSettings.conditionOrder) {
                    if (conditions.contains(condition)) {
                        updatedConditionOrder.add(condition)
                    }
                }

                // Add any new conditions
                for (condition in conditions) {
                    if (!updatedConditionOrder.contains(condition)) {
                        updatedConditionOrder.add(condition)
                    }
                }

                // Update settings with the new list
                curtainSettings = curtainSettings.copy(
                    conditionOrder = updatedConditionOrder
                )
            }

            // Clean up sample order for removed conditions
            curtainSettings = curtainSettings.copy(
                sampleOrder = curtainSettings.sampleOrder.filterKeys { it in curtainSettings.conditionOrder }.toMutableMap()
            )

            _finishedProcessingData.value = true
        } catch (e: Exception) {
            Log.e("CurtainDataService", "Error processing raw data: ${e.message}", e)
        }
    }

    suspend fun processDataAfterImport() {
        try {
            if (curtainData.differential.originalFile.isNotEmpty()) {
                processDifferentialData()
            }

            if (curtainData.raw.originalFile.isNotEmpty()) {
                processRawData()
            }

            _loadDataTrigger.emit(true)
            _finishedProcessingData.value = true
        } catch (e: Exception) {
            Log.e("CurtainDataService", "Error processing data after import: ${e.message}", e)
        }
    }

    // Add this method to CurtainDataService.kt
    fun clearMemory() {
        // Clear large data structures
        curtainData.raw = InputFile()
        curtainData.differential = InputFile()
        curtainData.dataMap.clear()
        uniprotData.results = mapOf()
        uniprotData.dataMap.clear()
        uniprotData.accMap.clear()
        uniprotData.db.clear()

        // Reset other large properties
        curtainData.allGenes = listOf()
        curtainData.selected = mapOf()
        curtainData.selectedMap = mapOf()

        // Force garbage collection
        System.gc()
        Log.d("CurtainDataService", "Memory cleared")
    }
}

/**
 * Supporting data classes specific to this service
 */
data class CurtainSession(val id: String)

/**
 * UniProt data container
 */
data class UniprotData(
    var results: Map<String, Any> = mapOf(),
    var dataMap: MutableMap<String, Any> = mutableMapOf(),
    var db: MutableMap<String, Any> = mutableMapOf(),
    var organism: String = "",
    var accMap: MutableMap<String, List<String>> = mutableMapOf(),
    var geneNameToAcc: MutableMap<String, Any> = mutableMapOf()
)

/**
 * Data container for the application
 */
data class AppData(
    var rawForm: RawForm = RawForm(),
    var differentialForm: DifferentialForm = DifferentialForm(),
    var raw: InputFile = InputFile(),
    var differential: InputFile = InputFile(),
    var fetchUniprot: Boolean = true,
    var annotatedData: Any? = null,
    var bypassUniProt: Boolean = false,
    var dataMap: MutableMap<String, Any> = mutableMapOf(),
    var genesMap: Any? = null,
    var primaryIDsMap: Any? = null,
    var allGenes: List<String> = listOf(),
    var selected: Map<String, List<Any>> = mapOf(),
    var selectedMap: Map<String, Map<String, Boolean>> = mapOf(),
    var selectOperationNames: List<String> = listOf()
)

/**
 * Input file representation with DataFrame support
 */
data class InputFile(
    val filename: String = "",
    val originalFile: String = "",
    val df: DataFrame<*> = DataFrame.empty(),
    val other: Any? = null
) {
    constructor(filename: String, originalFile: String, other: Any? = null) : this(
        filename = filename,
        originalFile = originalFile,
        // Parse the original file content to create a DataFrame, treating all values as strings
        df = if (originalFile.isNotEmpty()) {
            try {
                val rows = originalFile.trim().split("\n")
                if (rows.isNotEmpty()) {
                    val header = rows[0].split("\t").map { it.trim() }

                    val nameCounts = mutableMapOf<String, Int>()
                    header.forEach { colName ->
                        nameCounts[colName] = (nameCounts[colName] ?: 0) + 1
                    }

                    val uniqueHeader = mutableListOf<String>()
                    val processedCounts = mutableMapOf<String, Int>()

                    header.forEach { colName ->
                        if (nameCounts[colName] == 1) {
                            uniqueHeader.add(colName)
                        } else {
                            processedCounts[colName] = (processedCounts[colName] ?: 0) + 1
                            uniqueHeader.add("$colName.${processedCounts[colName]}")
                        }
                    }

                    Log.d("InputFile", "Parsed header: $uniqueHeader")
                    val columns = uniqueHeader.mapIndexed { colIndex, colName ->
                        val values = rows.drop(1).map { row ->
                            val cells = row.split("\t")
                            if (colIndex < cells.size) cells[colIndex] else ""
                        }
                        colName to values
                    }

                    dataFrameOf(*columns.toTypedArray())
                } else {
                    DataFrame.empty()
                }
            } catch (e: Exception) {
                Log.e("InputFile", "Error parsing file to DataFrame: ${e.message}", e)
                DataFrame.empty()
            }
        } else {
            DataFrame.empty()
        },
        other = other
    )
}
@Suppress("UNCHECKED_CAST")
private fun processSampleMap(map: Any?): Map<String, Map<String, String>> {
    return when (map) {
        is Map<*, *> -> map as Map<String, Map<String, String>>
        else -> mapOf()
    }
}
/**
 * Safely gets a value from the sampleMap with proper type casting
 */
private fun Map<String, Map<String, String>>.getSampleInfo(sample: String, field: String): String? {
    return this[sample]?.get(field)
}

/**
 * Safely converts various collection types to mutable lists
 */
@Suppress("UNCHECKED_CAST")
private fun Any?.toTypedMutableList(): MutableList<String> {
    return when (this) {
        is List<*> -> (this as? List<String>)?.toMutableList() ?: mutableListOf()
        is Collection<*> -> (this as? Collection<String>)?.toMutableList() ?: mutableListOf()
        else -> mutableListOf()
    }
}

