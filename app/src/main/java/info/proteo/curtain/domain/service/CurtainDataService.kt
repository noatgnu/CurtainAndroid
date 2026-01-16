package info.proteo.curtain.domain.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import info.proteo.curtain.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurtainDataService @Inject constructor(
    private val gson: Gson
) {

    data class LoadedCurtainData(
        val curtainData: CurtainData,
        val rawTsv: String?,
        val processedTsv: String?
    )

    suspend fun loadCurtainDataFromFile(filePath: String): Result<LoadedCurtainData> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("File not found: $filePath"))
            }

            var rawTsv: String? = null
            var processedTsv: String? = null

            val jsonObject = file.bufferedReader(bufferSize = 65536).use { reader ->
                val jsonReader = com.google.gson.stream.JsonReader(reader)
                val jsonObject = com.google.gson.JsonObject()

                jsonReader.beginObject()
                while (jsonReader.hasNext()) {
                    val name = jsonReader.nextName()
                    when (name) {
                        "raw" -> {
                            rawTsv = jsonReader.nextString()
                        }
                        "processed" -> {
                            processedTsv = jsonReader.nextString()
                        }
                        else -> {
                            jsonObject.add(name, JsonParser.parseReader(jsonReader))
                        }
                    }
                }
                jsonReader.endObject()

                jsonObject
            }

            val curtainData = parseJsonObjectToCurtainData(jsonObject)
            Result.success(LoadedCurtainData(curtainData, rawTsv, processedTsv))
        } catch (e: Exception) {
            android.util.Log.e("CurtainDataService", "Error loading curtain data from file", e)
            Result.failure(e)
        }
    }


    private fun parseJsonObjectToCurtainData(jsonObject: JsonObject): CurtainData {
        val settings = parseSettings(jsonObject)
        val rawForm = parseRawForm(jsonObject)
        val differentialForm = parseDifferentialForm(jsonObject)
        val extraData = parseExtraData(jsonObject)
        val selectedMapParsed = parseSelectedMap(jsonObject.get("selectionsMap"))

        return CurtainData(
            raw = null,
            rawForm = rawForm,
            differentialForm = differentialForm,
            processed = null,
            password = jsonObject.get("password")?.asString ?: "",
            selections = parseSelections(jsonObject.get("selections")),
            selectionsMap = null,
            selectedMap = selectedMapParsed,
            selectionsName = parseStringList(jsonObject.get("selectionsName")),
            _settings = settings,
            fetchUniprot = jsonObject.get("fetchUniprot")?.asBoolean ?: true,
            annotatedData = null,
            extraData = extraData,
            permanent = jsonObject.get("permanent")?.asBoolean ?: false,
            bypassUniProt = jsonObject.get("bypassUniProt")?.asBoolean ?: false
        )
    }

    private fun parseSettings(jsonObject: JsonObject): CurtainSettings {
        val settingsElement = jsonObject.get("settings") ?: return CurtainSettings()
        val settingsJson = when {
            settingsElement.isJsonObject -> settingsElement.asJsonObject
            settingsElement.isJsonPrimitive && settingsElement.asJsonPrimitive.isString -> {
                JsonParser.parseString(settingsElement.asString).asJsonObject
            }
            else -> return CurtainSettings()
        }
        return CurtainSettings(
            fetchUniprot = settingsJson.get("fetchUniprot")?.asBoolean ?: true,
            pCutoff = settingsJson.get("pCutoff")?.asDouble ?: 0.05,
            log2FCCutoff = settingsJson.get("log2FCCutoff")?.asDouble ?: 0.6,
            description = settingsJson.get("description")?.asString ?: "",
            uniprot = settingsJson.get("uniprot")?.asBoolean ?: true,
            colorMap = parseStringMap(settingsJson.get("colorMap")),
            academic = settingsJson.get("academic")?.asBoolean ?: false,
            backGroundColorGrey = settingsJson.get("backGroundColorGrey")?.asBoolean ?: false,
            currentComparison = settingsJson.get("currentComparison")?.asString ?: "",
            version = settingsJson.get("version")?.asDouble ?: 1.0,
            currentId = settingsJson.get("currentID")?.asString ?: "",
            fdrCurveText = settingsJson.get("fdrCurveText")?.asString ?: "",
            fdrCurveTextEnable = settingsJson.get("fdrCurveTextEnable")?.asBoolean ?: false,
            prideAccession = settingsJson.get("prideAccession")?.asString ?: "",
            project = parseProject(settingsJson.get("project")),
            sampleOrder = parseSampleOrder(settingsJson.get("sampleOrder")),
            sampleVisible = parseStringBooleanMap(settingsJson.get("sampleVisible")),
            conditionOrder = parseStringList(settingsJson.get("conditionOrder")),
            sampleMap = parseSampleMap(settingsJson.get("sampleMap")),
            volcanoAxis = parseVolcanoAxis(settingsJson.get("volcanoAxis")),
            textAnnotation = parseMap(settingsJson.get("textAnnotation")),
            volcanoPlotTitle = settingsJson.get("volcanoPlotTitle")?.asString ?: "Volcano Plot",
            visible = parseMap(settingsJson.get("visible")),
            volcanoPlotGrid = parseStringBooleanMap(settingsJson.get("volcanoPlotGrid")),
            volcanoPlotDimension = parseVolcanoPlotDimension(settingsJson.get("volcanoPlotDimension")),
            volcanoPlotLegendX = settingsJson.get("volcanoPlotLegendX")?.asDouble,
            volcanoPlotLegendY = settingsJson.get("volcanoPlotLegendY")?.asDouble,
            defaultColorList = parseStringList(settingsJson.get("defaultColorList")).ifEmpty { 
                listOf("#fd7f6f", "#7eb0d5", "#b2e061", "#bd7ebe", "#ffb55a", "#ffee65", "#beb9db", "#fdcce5", "#8bd3c7")
            },
            scatterPlotMarkerSize = settingsJson.get("scatterPlotMarkerSize")?.asDouble ?: 8.0,
            plotFontFamily = settingsJson.get("plotFontFamily")?.asString ?: "Arial",
            volcanoConditionLabels = parseVolcanoConditionLabels(settingsJson.get("volcanoConditionLabels")),
            volcanoTraceOrder = parseStringList(settingsJson.get("volcanoTraceOrder")),
            customVolcanoTextCol = settingsJson.get("customVolcanoTextCol")?.asString ?: "",
            markerSizeMap = parseMap(settingsJson.get("markerSizeMap")),
            barChartConditionBracket = parseBarChartConditionBracket(settingsJson.get("barChartConditionBracket")),
            chartYAxisLimits = parseChartYAxisLimits(settingsJson.get("chartYAxisLimits")),
            metabolomicsColumnMap = parseMetabolomicsColumnMap(settingsJson.get("metabolomicsColumnMap")),
            encrypted = settingsJson.get("encrypted")?.asBoolean ?: false,
            dataAnalysisContact = settingsJson.get("dataAnalysisContact")?.asString ?: ""
        )
    }

    private fun parseRawForm(jsonObject: JsonObject): CurtainRawForm {
        val rawFormElement = jsonObject.get("rawForm") ?: return CurtainRawForm()
        if (!rawFormElement.isJsonObject) return CurtainRawForm()
        val rawFormJson = rawFormElement.asJsonObject
        return CurtainRawForm(
            primaryIDs = rawFormJson.get("_primaryIDs")?.asString ?: "",
            samples = parseStringList(rawFormJson.get("_samples")),
            log2 = rawFormJson.get("_log2")?.asBoolean ?: false
        )
    }

    private fun parseDifferentialForm(jsonObject: JsonObject): CurtainDifferentialForm {
        val diffFormElement = jsonObject.get("differentialForm") ?: return CurtainDifferentialForm()
        if (!diffFormElement.isJsonObject) return CurtainDifferentialForm()
        val diffFormJson = diffFormElement.asJsonObject
        return CurtainDifferentialForm(
            primaryIDs = diffFormJson.get("_primaryIDs")?.asString ?: "",
            geneNames = diffFormJson.get("_geneNames")?.asString ?: "",
            foldChange = diffFormJson.get("_foldChange")?.asString ?: "",
            transformFC = diffFormJson.get("_transformFC")?.asBoolean ?: false,
            significant = diffFormJson.get("_significant")?.asString ?: "",
            transformSignificant = diffFormJson.get("_transformSignificant")?.asBoolean ?: false,
            comparison = diffFormJson.get("_comparison")?.asString ?: "",
            comparisonSelect = parseStringList(diffFormJson.get("_comparisonSelect")),
            reverseFoldChange = diffFormJson.get("_reverseFoldChange")?.asBoolean ?: false
        )
    }

    private fun parseExtraData(jsonObject: JsonObject): ExtraData? {
        val extraDataElement = jsonObject.get("extraData") ?: return null
        val extraDataJson = when {
            extraDataElement.isJsonObject -> extraDataElement.asJsonObject
            extraDataElement.isJsonPrimitive && extraDataElement.asJsonPrimitive.isString -> {
                JsonParser.parseString(extraDataElement.asString).asJsonObject
            }
            else -> return null
        }
        return ExtraData(
            uniprot = parseUniprotExtraData(extraDataJson.get("uniprot")),
            data = parseDataMapContainer(extraDataJson.get("data"))
        )
    }

    private fun parseUniprotExtraData(element: com.google.gson.JsonElement?): UniprotExtraData? {
        element ?: return null
        if (!element.isJsonObject) return null
        val uniprotJson = element.asJsonObject

        val dbRaw = parseAnyValueLimited(uniprotJson.get("db"))
        val dataMapRaw = parseAnyValueLimited(uniprotJson.get("dataMap"))
        val accMapRaw = parseAnyValueLimited(uniprotJson.get("accMap"))

        val db = unwrapMapData(dbRaw)
        val dataMap = unwrapMapData(dataMapRaw)
        val accMap = unwrapMapData(accMapRaw)

        val geneNameToAccRaw = parseAnyValueLimited(uniprotJson.get("geneNameToAcc"))
        val geneNameToAcc = unwrapMapData(geneNameToAccRaw)

        return UniprotExtraData(
            results = parseMap(uniprotJson.get("results"), 0),
            dataMap = dataMap,
            db = db,
            organism = uniprotJson.get("organism")?.asString,
            accMap = accMap,
            geneNameToAcc = geneNameToAcc
        )
    }

    private fun unwrapMapData(data: Any): Any {
        if (data is Map<*, *>) {
            val dataType = data["dataType"] as? String
            val value = data["value"]
            if (dataType == "Map" && value is List<*>) {
                val result = mutableMapOf<String, Any>()
                value.forEach { pair ->
                    if (pair is List<*> && pair.size >= 2) {
                        val key = pair[0]?.toString()
                        val pairValue = pair[1]
                        if (key != null && pairValue != null) {
                            result[key] = pairValue
                        }
                    }
                }
                return result
            }
        }
        return data
    }

    private fun parseDataMapContainer(element: com.google.gson.JsonElement?): DataMapContainer? {
        element ?: return null
        if (!element.isJsonObject) return null
        val dataJson = element.asJsonObject

        val genesMapRaw = parseAnyValueLimited(dataJson.get("genesMap"))
        val primaryIDsMapRaw = parseAnyValueLimited(dataJson.get("primaryIDsMap"))

        val genesMap = unwrapMapData(genesMapRaw)
        val primaryIDsMap = unwrapMapData(primaryIDsMapRaw)

        return DataMapContainer(
            dataMap = parseAnyValueLimited(dataJson.get("dataMap")),
            genesMap = genesMap,
            primaryIDsMap = primaryIDsMap,
            allGenes = parseStringList(dataJson.get("allGenes"))
        )
    }

    private fun parseProject(element: com.google.gson.JsonElement?): Project {
        element ?: return Project()
        if (!element.isJsonObject) return Project()
        val projectJson = element.asJsonObject
        return Project(
            title = projectJson.get("title")?.asString ?: "",
            projectDescription = projectJson.get("projectDescription")?.asString ?: "",
            organisms = parseNameItemList(projectJson.get("organisms")),
            organismParts = parseNameItemList(projectJson.get("organismParts")),
            cellTypes = parseNameItemList(projectJson.get("cellTypes")),
            diseases = parseNameItemList(projectJson.get("diseases")),
            sampleProcessingProtocol = projectJson.get("sampleProcessingProtocol")?.asString ?: "",
            dataProcessingProtocol = projectJson.get("dataProcessingProtocol")?.asString ?: "",
            accession = projectJson.get("accession")?.asString ?: "",
            sampleAnnotations = parseMap(projectJson.get("sampleAnnotations"))
        )
    }

    private fun parseNameItemList(element: com.google.gson.JsonElement?): List<NameItem> {
        element ?: return listOf(NameItem())
        if (!element.isJsonArray) return listOf(NameItem())
        return element.asJsonArray.map { 
            if (it.isJsonObject) {
                val obj = it.asJsonObject
                NameItem(
                    name = obj.get("name")?.asString ?: "",
                    cvLabel = obj.get("cvLabel")?.asString
                )
            } else {
                NameItem()
            }
        }
    }

    private fun parseVolcanoAxis(element: com.google.gson.JsonElement?): VolcanoAxis {
        element ?: return VolcanoAxis()
        if (!element.isJsonObject) return VolcanoAxis()
        val axisJson = element.asJsonObject
        return VolcanoAxis(
            minX = axisJson.get("minX")?.takeIf { !it.isJsonNull }?.asDouble,
            maxX = axisJson.get("maxX")?.takeIf { !it.isJsonNull }?.asDouble,
            minY = axisJson.get("minY")?.takeIf { !it.isJsonNull }?.asDouble,
            maxY = axisJson.get("maxY")?.takeIf { !it.isJsonNull }?.asDouble,
            x = axisJson.get("x")?.takeIf { !it.isJsonNull }?.asString ?: "Log2FC",
            y = axisJson.get("y")?.takeIf { !it.isJsonNull }?.asString ?: "-log10(p-value)",
            dtickX = axisJson.get("dtickX")?.takeIf { !it.isJsonNull }?.asDouble,
            dtickY = axisJson.get("dtickY")?.takeIf { !it.isJsonNull }?.asDouble,
            ticklenX = axisJson.get("ticklenX")?.takeIf { !it.isJsonNull }?.asInt ?: 5,
            ticklenY = axisJson.get("ticklenY")?.takeIf { !it.isJsonNull }?.asInt ?: 5
        )
    }

    private fun parseVolcanoPlotDimension(element: com.google.gson.JsonElement?): VolcanoPlotDimension {
        element ?: return VolcanoPlotDimension()
        if (!element.isJsonObject) return VolcanoPlotDimension()
        val dimensionJson = element.asJsonObject
        return VolcanoPlotDimension(
            width = dimensionJson.get("width")?.takeIf { !it.isJsonNull }?.asInt ?: 800,
            height = dimensionJson.get("height")?.takeIf { !it.isJsonNull }?.asInt ?: 600,
            margin = parseVolcanoPlotMargin(dimensionJson.get("margin"))
        )
    }

    private fun parseVolcanoPlotMargin(element: com.google.gson.JsonElement?): VolcanoPlotMargin {
        element ?: return VolcanoPlotMargin()
        if (!element.isJsonObject) return VolcanoPlotMargin()
        val marginJson = element.asJsonObject
        return VolcanoPlotMargin(
            left = marginJson.get("l")?.takeIf { !it.isJsonNull }?.asInt,
            right = marginJson.get("r")?.takeIf { !it.isJsonNull }?.asInt,
            bottom = marginJson.get("b")?.takeIf { !it.isJsonNull }?.asInt,
            top = marginJson.get("t")?.takeIf { !it.isJsonNull }?.asInt
        )
    }

    private fun parseVolcanoConditionLabels(element: com.google.gson.JsonElement?): VolcanoConditionLabels {
        element ?: return VolcanoConditionLabels()
        if (!element.isJsonObject) return VolcanoConditionLabels()
        val labelsJson = element.asJsonObject
        return VolcanoConditionLabels(
            leftCondition = labelsJson.get("leftCondition")?.asString ?: "",
            rightCondition = labelsJson.get("rightCondition")?.asString ?: "",
            enabled = labelsJson.get("enabled")?.asBoolean ?: true,
            fontColor = labelsJson.get("fontColor")?.asString ?: "#000000",
            fontSize = labelsJson.get("fontSize")?.asInt ?: 14,
            leftX = labelsJson.get("leftX")?.asDouble ?: 0.25,
            rightX = labelsJson.get("rightX")?.asDouble ?: 0.75,
            yPosition = labelsJson.get("yPosition")?.asDouble ?: -0.1
        )
    }

    private fun parseBarChartConditionBracket(element: com.google.gson.JsonElement?): BarChartConditionBracket {
        element ?: return BarChartConditionBracket()
        if (!element.isJsonObject) return BarChartConditionBracket()
        val json = element.asJsonObject
        return BarChartConditionBracket(
            showBracket = json.get("showBracket")?.asBoolean ?: false,
            bracketHeight = json.get("bracketHeight")?.asDouble ?: 0.05,
            bracketColor = json.get("bracketColor")?.asString ?: "#000000",
            bracketWidth = json.get("bracketWidth")?.asInt ?: 2
        )
    }
    
    private fun parseMetabolomicsColumnMap(element: com.google.gson.JsonElement?): MetabolomicsColumnMap {
        element ?: return MetabolomicsColumnMap()
        if (!element.isJsonObject) return MetabolomicsColumnMap()
        val json = element.asJsonObject
        return MetabolomicsColumnMap(
            polarity = json.get("polarity")?.asString,
            formula = json.get("formula")?.asString,
            abbreviation = json.get("abbreviation")?.asString,
            smiles = json.get("smiles")?.asString
        )
    }
    
    private fun parseChartYAxisLimits(element: com.google.gson.JsonElement?): Map<String, ChartYAxisLimits> {
        element ?: return emptyMap()
        if (!element.isJsonObject) return emptyMap()
        return element.asJsonObject.entrySet().associate { 
            it.key to parseChartYAxisLimit(it.value)
        }
    }
    
    private fun parseChartYAxisLimit(element: com.google.gson.JsonElement?): ChartYAxisLimits {
        if (element == null || !element.isJsonObject) return ChartYAxisLimits()
        val json = element.asJsonObject
        return ChartYAxisLimits(
            min = json.get("min")?.takeIf { !it.isJsonNull }?.asDouble,
            max = json.get("max")?.takeIf { !it.isJsonNull }?.asDouble
        )
    }

    private fun parseStringList(element: com.google.gson.JsonElement?): List<String> {
        element ?: return emptyList()
        if (!element.isJsonArray) return emptyList()
        return element.asJsonArray.mapNotNull { if (it.isJsonNull) null else it.asString }
    }

    private fun parseStringMap(element: com.google.gson.JsonElement?): Map<String, String> {
        element ?: return emptyMap()
        if (!element.isJsonObject) return emptyMap()
        return element.asJsonObject.entrySet().associate { it.key to (it.value.takeIf { !it.isJsonNull }?.asString ?: "") }
    }

    private fun parseStringBooleanMap(element: com.google.gson.JsonElement?): Map<String, Boolean> {
        element ?: return emptyMap()
        if (!element.isJsonObject) return emptyMap()
        return element.asJsonObject.entrySet().associate { it.key to (it.value.asBoolean) }
    }

    private fun parseMap(element: com.google.gson.JsonElement?, depth: Int = 0): Map<String, Any> {
        element ?: return emptyMap()
        if (!element.isJsonObject) return emptyMap()
        if (depth > 3) return emptyMap()
        return element.asJsonObject.entrySet().associate { it.key to parseAnyValue(it.value, depth + 1) }
    }

    private fun parseSampleOrder(element: com.google.gson.JsonElement?): Map<String, List<String>> {
        element ?: return emptyMap()
        if (!element.isJsonObject) return emptyMap()
        return element.asJsonObject.entrySet().associate { it.key to parseStringList(it.value) }
    }

    private fun parseSampleMap(element: com.google.gson.JsonElement?): Map<String, Map<String, String>> {
        element ?: return emptyMap()
        if (!element.isJsonObject) return emptyMap()
        return element.asJsonObject.entrySet().associate { entry ->
            entry.key to parseStringMap(entry.value)
        }
    }

    private fun parseSelections(element: com.google.gson.JsonElement?): Map<String, List<Any>>? {
        element ?: return null
        if (!element.isJsonObject) return null
        return element.asJsonObject.entrySet().associate { it.key to parseAnyList(it.value) }
    }

    private fun parseSelectedMap(element: com.google.gson.JsonElement?): Map<String, Map<String, Boolean>>? {
        element ?: return null
        if (!element.isJsonObject) return null
        return element.asJsonObject.entrySet().associate { it.key to parseStringBooleanMap(it.value) }
    }

    private fun parseAnyList(element: com.google.gson.JsonElement?, depth: Int = 0): List<Any> {
        element ?: return emptyList()
        if (!element.isJsonArray) return emptyList()
        if (depth > 3) return emptyList()
        return element.asJsonArray.map { parseAnyValue(it, depth + 1) }
    }

    private fun parseAnyValue(element: com.google.gson.JsonElement?, depth: Int = 0): Any {
        element ?: return ""
        if (depth > 3) return ""
        return when {
            element.isJsonPrimitive -> {
                when {
                    element.asJsonPrimitive.isBoolean -> element.asBoolean
                    element.asJsonPrimitive.isNumber -> element.asDouble
                    element.asJsonPrimitive.isString -> element.asString
                    else -> element.toString()
                }
            }
            element.isJsonObject -> parseMap(element, depth)
            element.isJsonArray -> parseAnyList(element, depth)
            element.isJsonNull -> ""
            else -> element.toString()
        }
    }

    private fun parseAnyValueLimited(element: com.google.gson.JsonElement?): Any {
        element ?: return emptyMap<String, Any>()
        if (!element.isJsonObject) return emptyMap<String, Any>()

        val obj = element.asJsonObject
        val dataType = obj.get("dataType")?.asString
        val value = obj.get("value")

        if (dataType == "Map" && value != null && value.isJsonArray) {
            val result = mutableMapOf<String, Any>()
            value.asJsonArray.forEach { pair ->
                if (pair.isJsonArray) {
                    val pairArray = pair.asJsonArray
                    if (pairArray.size() >= 2) {
                        val key = pairArray[0].asString
                        result[key] = parseAnyValue(pairArray[1], 1)
                    }
                }
            }
            return result
        }

        return emptyMap<String, Any>()
    }

}
