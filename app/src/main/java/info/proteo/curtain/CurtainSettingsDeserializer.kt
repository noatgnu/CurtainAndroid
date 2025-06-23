package info.proteo.curtain

import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.json.JSONArray
import org.json.JSONObject
import java.lang.Exception
import java.util.Locale

/**
 * Deserializer for CurtainSettings that handles optional fields and provides
 * methods to convert JSON strings to CurtainSettings objects.
 */
class CurtainSettingsDeserializer {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /**
     * Deserializes a JSON string into a CurtainSettings object.
     * Handles optional fields by using the default values defined in the data class.
     *
     * @param jsonString The JSON string to deserialize
     * @return A CurtainSettings object, or null if deserialization failed
     */
    fun deserializeCurtainSettings(jsonString: String): CurtainSettings {
        val json = JSONObject(jsonString)

        return CurtainSettings(
            fetchUniprot = json.optBoolean("fetchUniprot", true),
            inputDataCols = parseMapAny(json, "inputDataCols"),
            probabilityFilterMap = parseMapAny(json, "probabilityFilterMap"),
            barchartColorMap = parseMapAny(json, "barchartColorMap"),
            pCutoff = json.optDouble("pCutoff", 0.05),
            log2FCCutoff = json.optDouble("log2FCCutoff", 0.6),
            description = json.optString("description", ""),
            uniprot = json.optBoolean("uniprot", true),
            colorMap = parseMapString(json, "colorMap"),
            academic = json.optBoolean("academic", true),
            backGroundColorGrey = json.optBoolean("backGroundColorGrey", false),
            currentComparison = json.optString("currentComparison", ""),
            version = json.optDouble("version", 2.0),
            currentId = json.optString("currentID", ""),
            fdrCurveText = json.optString("fdrCurveText", ""),
            fdrCurveTextEnable = json.optBoolean("fdrCurveTextEnable", false),
            prideAccession = json.optString("prideAccession", ""),
            project = parseProject(json.optJSONObject("project")),
            sampleOrder = parseMapStringList(json, "sampleOrder"),
            sampleVisible = parseMapBoolean(json, "sampleVisible"),
            conditionOrder = parseStringList(json, "conditionOrder"),
            volcanoAxis = parseVolcanoAxis(json.optJSONObject("volcanoAxis")),
            textAnnotation = parseMapAny(json, "textAnnotation"),
            volcanoPlotTitle = json.optString("volcanoPlotTitle", ""),
            visible = parseMapAny(json, "visible"),
            defaultColorList = parseStringList(json, "defaultColorList", defaultColors()),
            scatterPlotMarkerSize = json.optDouble("scatterPlotMarkerSize", 10.0),
            rankPlotColorMap = parseMapAny(json, "rankPlotColorMap"),
            rankPlotAnnotation = parseMapAny(json, "rankPlotAnnotation"),
            legendStatus = parseMapAny(json, "legendStatus"),
            stringDBColorMap = parseMapString(json, "stringDBColorMap", defaultStringDBColors()),
            interactomeAtlasColorMap = parseMapString(json, "interactomeAtlasColorMap", defaultInteractomeColors()),
            proteomicsDBColor = json.optString("proteomicsDBColor", "#ff7f0e"),
            networkInteractionSettings = parseMapString(json, "networkInteractionSettings", defaultNetworkInteractionSettings()),
            plotFontFamily = json.optString("plotFontFamily", "Arial"),
            volcanoPlotGrid = parseVolcanoPlotGrid(json.optJSONObject("volcanoPlotGrid")),
            volcanoPlotYaxisPosition = parseStringList(json, "volcanoPlotYaxisPosition", listOf("middle")),
            volcanoPlotDimension = parseVolcanoPlotDimension(json.optJSONObject("volcanoPlotDimension")),
            volcanoAdditionalShapes = parseList(json, "volcanoAdditionalShapes"),
            volcanoPlotLegendX = if (json.has("volcanoPlotLegendX")) json.optDouble("volcanoPlotLegendX") else null,
            volcanoPlotLegendY = if (json.has("volcanoPlotLegendY")) json.optDouble("volcanoPlotLegendY") else null,
            sampleMap = parseMapMapString(json, "sampleMap"),
            customVolcanoTextCol = if (json.has("customVolcanoTextCol")) json.optString("customVolcanoTextCol") else null,
            dataAnalysisContact = if (json.has("dataAnalysisContact")) json.optString("dataAnalysisContact") else null,
            selectedComparison = parseStringList(json, "selectedComparison"),
            networkInteractionData = parseList(json, "networkInteractionData"),
            enrichrGeneRankMap = parseMapAny(json, "enrichrGeneRankMap"),
            enrichrRunList = parseStringList(json, "enrichrRunList"),
            encrypted = json.optBoolean("encrypted", false),
            columnSize = parseColumnSize(json.optJSONObject("columnSize")),
            violinPointPos = if (json.has("violinPointPos")) json.optInt("violinPointPos", -2) else -2,
            extraData = parseExtraDataList(json.optJSONArray("extraData")),
            imputationMap = parseMapAny(json, "imputationMap"),
            enableImputation = json.optBoolean("enableImputation", false),
            viewPeptideCount = json.optBoolean("viewPeptideCount", false),
            peptideCountData = parseMapAny(json, "peptideCountData"),
            viewPeptideCountData = json.optBoolean("viewPeptideCountData", false)
        )
    }

// Helper function to parse various nested objects

    private fun parseProject(json: JSONObject?): Project {
        if (json == null) return Project()

        return Project(
            title = json.optString("title", ""),
            projectDescription = json.optString("projectDescription", ""),
            organisms = parseNameItemList(json.optJSONArray("organisms")),
            organismParts = parseNameItemList(json.optJSONArray("organismParts")),
            cellTypes = parseNameItemList(json.optJSONArray("cellTypes")),
            diseases = parseNameItemList(json.optJSONArray("diseases")),
            sampleProcessingProtocol = json.optString("sampleProcessingProtocol", ""),
            dataProcessingProtocol = json.optString("dataProcessingProtocol", ""),
            identifiedPTMStrings = parseNameItemList(json.optJSONArray("identifiedPTMStrings")),
            instruments = parseInstrumentList(json.optJSONArray("instruments")),
            msMethods = parseNameItemList(json.optJSONArray("msMethods")),
            projectTags = parseNameItemList(json.optJSONArray("projectTags")),
            quantificationMethods = parseNameItemList(json.optJSONArray("quantificationMethods")),
            species = parseNameItemList(json.optJSONArray("species")),
            sampleAnnotations = parseMapAny(json, "sampleAnnotations"),
            links = parseLinks(json.optJSONObject("_links")),
            affiliations = parseNameItemList(json.optJSONArray("affiliations")),
            hasLink = json.optBoolean("hasLink", false),
            authors = parseList(json, "authors"),
            accession = json.optString("accession", ""),
            softwares = parseNameItemList(json.optJSONArray("softwares")),
            publicationDate = parseMapAny(json, "publicationDate")
        )
    }

    private fun parseLinks(json: JSONObject?): Links {
        if (json == null) return Links()

        return Links(
            datasetFtpUrl = parseLink(json.optJSONObject("datasetFtpUrl")),
            files = parseLink(json.optJSONObject("files")),
            self = parseLink(json.optJSONObject("self"))
        )
    }

    private fun parseLink(json: JSONObject?): Link {
        if (json == null) return Link()
        return Link(json.optString("href", ""))
    }

    private fun parseNameItemList(jsonArray: JSONArray?): List<NameItem> {
        if (jsonArray == null) return listOf(NameItem())

        val result = mutableListOf<NameItem>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i)
            result.add(NameItem(
                name = item.optString("name", ""),
                cvLabel = if (item.has("cvLabel")) item.optString("cvLabel") else null
            ))
        }
        return result
    }

    private fun parseInstrumentList(jsonArray: JSONArray?): List<InstrumentItem> {
        if (jsonArray == null) return listOf(InstrumentItem())

        val result = mutableListOf<InstrumentItem>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i)
            result.add(InstrumentItem(
                cvLabel = item.optString("cvLabel", "MS"),
                name = item.optString("name", "")
            ))
        }
        return result
    }

    private fun parseVolcanoAxis(json: JSONObject?): VolcanoAxis {
        if (json == null) return VolcanoAxis()

        return VolcanoAxis(
            minX = if (json.has("minX") && !json.isNull("minX")) json.optDouble("minX") else null,
            maxX = if (json.has("maxX") && !json.isNull("maxX")) json.optDouble("maxX") else null,
            minY = if (json.has("minY") && !json.isNull("minY")) json.optDouble("minY") else null,
            maxY = if (json.has("maxY") && !json.isNull("maxY")) json.optDouble("maxY") else null,
            x = json.optString("x", "Log2FC"),
            y = json.optString("y", "-log10(p-value)"),
            dtickX = if (json.has("dtickX") && !json.isNull("dtickX")) json.optDouble("dtickX") else null,
            dtickY = if (json.has("dtickY") && !json.isNull("dtickY")) json.optDouble("dtickY") else null,
            ticklenX = json.optInt("ticklenX", 5),
            ticklenY = json.optInt("ticklenY", 5)
        )
    }

    private fun parseVolcanoPlotDimension(json: JSONObject?): VolcanoPlotDimension {
        if (json == null) return VolcanoPlotDimension()

        return VolcanoPlotDimension(
            width = json.optInt("width", 800),
            height = json.optInt("height", 1000),
            margin = parseVolcanoPlotMargin(json.optJSONObject("margin"))
        )
    }

    private fun parseVolcanoPlotMargin(json: JSONObject?): VolcanoPlotMargin {
        if (json == null) return VolcanoPlotMargin()

        return VolcanoPlotMargin(
            left = if (json.has("l") && !json.isNull("l")) json.optInt("l") else null,
            right = if (json.has("r") && !json.isNull("r")) json.optInt("r") else null,
            bottom = if (json.has("b") && !json.isNull("b")) json.optInt("b") else null,
            top = if (json.has("t") && !json.isNull("t")) json.optInt("t") else null
        )
    }

    private fun parseColumnSize(json: JSONObject?): ColumnSize? {
        if (json == null) return null

        return ColumnSize(
            barChart = json.optInt("barChart", 0),
            averageBarChart = json.optInt("averageBarChart", 0),
            violinPlot = json.optInt("violinPlot", 0),
            profilePlot = json.optInt("profilePlot", 0)
        )
    }

    private fun parseExtraDataList(jsonArray: JSONArray?): List<ExtraDataItem>? {
        if (jsonArray == null) return null

        val result = mutableListOf<ExtraDataItem>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i)
            result.add(ExtraDataItem(
                name = item.optString("name", ""),
                content = item.optString("content", ""),
                type = item.optString("type", "")
            ))
        }
        return result
    }

    // Helper functions to parse Map and List structures
    private fun parseMapAny(json: JSONObject, key: String): Map<String, Any> {
        if (!json.has(key)) return mapOf()
        val obj = json.optJSONObject(key) ?: return mapOf()

        val result = mutableMapOf<String, Any>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val mapKey = keys.next()
            result[mapKey] = parseJsonValue(obj.opt(mapKey))
        }
        return result
    }

    private fun parseMapString(json: JSONObject, key: String, defaultMap: Map<String, String> = mapOf()): Map<String, String> {
        if (!json.has(key)) return defaultMap
        val obj = json.optJSONObject(key) ?: return defaultMap

        val result = mutableMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val mapKey = keys.next()
            val value = obj.opt(mapKey)
            if (value is String) {
                result[mapKey] = value
            }
        }
        return result
    }

    private fun parseMapBoolean(json: JSONObject, key: String): Map<String, Boolean> {
        if (!json.has(key)) return mapOf()
        val obj = json.optJSONObject(key) ?: return mapOf()

        val result = mutableMapOf<String, Boolean>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val mapKey = keys.next()
            val value = obj.opt(mapKey)
            if (value is Boolean) {
                result[mapKey] = value
            } else if (value != null) {
                // Try to coerce other types to boolean
                result[mapKey] = value.toString().lowercase(Locale.getDefault()) == "true"
            }
        }
        return result
    }

    private fun parseMapStringList(json: JSONObject, key: String): Map<String, List<String>> {
        if (!json.has(key)) return mapOf()
        val obj = json.optJSONObject(key) ?: return mapOf()

        val result = mutableMapOf<String, List<String>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val mapKey = keys.next()
            val array = obj.optJSONArray(mapKey)
            if (array != null) {
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.optString(i, ""))
                }
                result[mapKey] = list
            }
        }
        return result
    }

    private fun parseMapMapString(json: JSONObject, key: String): Map<String, Map<String, String>> {
        if (!json.has(key)) return mapOf()
        val obj = json.optJSONObject(key) ?: return mapOf()

        val result = mutableMapOf<String, Map<String, String>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val mapKey = keys.next()
            val innerObj = obj.optJSONObject(mapKey)
            if (innerObj != null) {
                val innerMap = mutableMapOf<String, String>()
                val innerKeys = innerObj.keys()
                while (innerKeys.hasNext()) {
                    val innerMapKey = innerKeys.next()
                    innerMap[innerMapKey] = innerObj.optString(innerMapKey, "")
                }
                result[mapKey] = innerMap
            }
        }
        return result
    }

    private fun parseStringList(json: JSONObject, key: String, defaultList: List<String> = listOf()): List<String> {
        if (!json.has(key)) return defaultList
        val array = json.optJSONArray(key) ?: return defaultList

        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            result.add(array.optString(i, ""))
        }
        return result
    }

    private fun parseList(json: JSONObject, key: String): List<Any> {
        if (!json.has(key)) return listOf()
        val array = json.optJSONArray(key) ?: return listOf()

        val result = mutableListOf<Any>()
        for (i in 0 until array.length()) {
            result.add(parseJsonValue(array.opt(i)))
        }
        return result
    }

    private fun parseVolcanoPlotGrid(json: JSONObject?): Map<String, Boolean> {
        if (json == null) return mapOf("x" to true, "y" to true)

        val result = mutableMapOf<String, Boolean>()
        result["x"] = json.optBoolean("x", true)
        result["y"] = json.optBoolean("y", true)
        return result
    }

    private fun parseJsonValue(value: Any?): Any {
        return when (value) {
            is JSONObject -> {
                val map = mutableMapOf<String, Any>()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = parseJsonValue(value.opt(key))
                }
                map
            }
            is JSONArray -> {
                val list = mutableListOf<Any>()
                for (i in 0 until value.length()) {
                    list.add(parseJsonValue(value.opt(i)))
                }
                list
            }
            null -> ""
            else -> value
        }
    }

    // Default values functions
    private fun defaultColors(): List<String> {
        return listOf(
            "#fd7f6f", "#7eb0d5", "#b2e061", "#bd7ebe", "#ffb55a",
            "#ffee65", "#beb9db", "#fdcce5", "#8bd3c7"
        )
    }

    private fun defaultStringDBColors(): Map<String, String> {
        return mapOf(
            "Increase" to "#8d0606",
            "Decrease" to "#4f78a4",
            "In dataset" to "#ce8080",
            "Not in dataset" to "#676666"
        )
    }

    private fun defaultInteractomeColors(): Map<String, String> {
        return mapOf(
            "Increase" to "#a12323",
            "Decrease" to "#16458c",
            "HI-Union" to "rgba(82,110,194,0.96)",
            "Literature" to "rgba(181,151,222,0.96)",
            "HI-Union and Literature" to "rgba(222,178,151,0.96)",
            "Not found" to "rgba(25,128,128,0.96)",
            "No change" to "rgba(47,39,40,0.96)"
        )
    }

    private fun defaultNetworkInteractionSettings(): Map<String, String> {
        return mapOf(
            "Increase" to "rgba(220,169,0,0.96)",
            "Decrease" to "rgba(220,0,59,0.96)",
            "StringDB" to "rgb(206,128,128)",
            "No change" to "rgba(47,39,40,0.96)",
            "Not significant" to "rgba(255,255,255,0.96)",
            "Significant" to "rgba(252,107,220,0.96)",
            "InteractomeAtlas" to "rgb(73,73,101)"
        )
    }
}

fun manualDeserializeSettings(map: Map<String, Any?>): CurtainSettings {
    val result = CurtainSettings(
        fetchUniprot = map["fetchUniprot"] as? Boolean ?: true,
        inputDataCols = map["inputDataCols"] as? Map<String, Any> ?: mapOf(),
        probabilityFilterMap = map["probabilityFilterMap"] as? Map<String, Any> ?: mapOf(),
        barchartColorMap = map["barchartColorMap"] as? Map<String, Any> ?: mapOf(),
        pCutoff = map["pCutoff"] as? Double ?: 0.05,
        log2FCCutoff = map["log2FCCutoff"] as? Double ?: 0.6,
        description = map["description"] as? String ?: "",
        uniprot = map["uniprot"] as? Boolean ?: true,
        colorMap = map["colorMap"] as? Map<String, String> ?: mapOf(),
        academic = map["academic"] as? Boolean ?: true,
        backGroundColorGrey = map["backGroundColorGrey"] as? Boolean ?: false,
        currentComparison = map["currentComparison"] as? String ?: "",
        version = map["version"] as? Double ?: 2.0,
        currentId = map["currentID"] as? String ?: "",
        fdrCurveText = map["fdrCurveText"] as? String ?: "",
        fdrCurveTextEnable = map["fdrCurveTextEnable"] as? Boolean ?: false,
        prideAccession = map["prideAccession"] as? String ?: "",
        project = parseProject(map["project"] as? Map<String, Any?>),
        sampleOrder = map["sampleOrder"] as? Map<String, List<String>> ?: mapOf(),
        sampleVisible = map["sampleVisible"] as? Map<String, Boolean> ?: mapOf(),
        conditionOrder = map["conditionOrder"] as? List<String> ?: listOf(),
        volcanoAxis = parseVolcanoAxis(map["volcanoAxis"] as? Map<String, Any?>),
        textAnnotation = map["textAnnotation"] as? Map<String, Any> ?: mapOf(),
        volcanoPlotTitle = map["volcanoPlotTitle"] as? String ?: "",
        visible = map["visible"] as? Map<String, Any> ?: mapOf(),
        defaultColorList = map["defaultColorList"] as? List<String> ?: listOf(
            "#fd7f6f", "#7eb0d5", "#b2e061", "#bd7ebe", "#ffb55a",
            "#ffee65", "#beb9db", "#fdcce5", "#8bd3c7"
        ),
        scatterPlotMarkerSize = map["scatterPlotMarkerSize"] as? Double ?: 10.0,
        rankPlotColorMap = map["rankPlotColorMap"] as? Map<String, Any> ?: mapOf(),
        rankPlotAnnotation = map["rankPlotAnnotation"] as? Map<String, Any> ?: mapOf(),
        legendStatus = map["legendStatus"] as? Map<String, Any> ?: mapOf(),
        stringDBColorMap = map["stringDBColorMap"] as? Map<String, String> ?: mapOf(
            "Increase" to "#8d0606",
            "Decrease" to "#4f78a4",
            "In dataset" to "#ce8080",
            "Not in dataset" to "#676666"
        ),
        interactomeAtlasColorMap = map["interactomeAtlasColorMap"] as? Map<String, String> ?: mapOf(
            "Increase" to "#a12323",
            "Decrease" to "#16458c",
            "HI-Union" to "rgba(82,110,194,0.96)",
            "Literature" to "rgba(181,151,222,0.96)",
            "HI-Union and Literature" to "rgba(222,178,151,0.96)",
            "Not found" to "rgba(25,128,128,0.96)",
            "No change" to "rgba(47,39,40,0.96)"
        ),
        proteomicsDBColor = map["proteomicsDBColor"] as? String ?: "#ff7f0e",
        networkInteractionSettings = map["networkInteractionSettings"] as? Map<String, String> ?: mapOf(
            "Increase" to "rgba(220,169,0,0.96)",
            "Decrease" to "rgba(220,0,59,0.96)",
            "StringDB" to "rgb(206,128,128)",
            "No change" to "rgba(47,39,40,0.96)",
            "Not significant" to "rgba(255,255,255,0.96)",
            "Significant" to "rgba(252,107,220,0.96)",
            "InteractomeAtlas" to "rgb(73,73,101)"
        ),
        plotFontFamily = map["plotFontFamily"] as? String ?: "Arial",
        volcanoPlotGrid = map["volcanoPlotGrid"] as? Map<String, Boolean> ?: mapOf("x" to true, "y" to true),
        volcanoPlotYaxisPosition = map["volcanoPlotYaxisPosition"] as? List<String> ?: listOf("middle"),
        volcanoPlotDimension = parseVolcanoPlotDimension(map["volcanoPlotDimension"] as? Map<String, Any?>),
        volcanoAdditionalShapes = map["volcanoAdditionalShapes"] as? List<Any> ?: listOf(),
        volcanoPlotLegendX = map["volcanoPlotLegendX"] as? Double,
        volcanoPlotLegendY = map["volcanoPlotLegendY"] as? Double,
        sampleMap = map["sampleMap"] as? Map<String, Map<String, String>> ?: mapOf(),
        customVolcanoTextCol = map["customVolcanoTextCol"] as? String,
        dataAnalysisContact = map["dataAnalysisContact"] as? String,
        selectedComparison = map["selectedComparison"] as? List<String>,
        networkInteractionData = map["networkInteractionData"] as? List<Any>,
        enrichrGeneRankMap = map["enrichrGeneRankMap"] as? Map<String, Any>,
        enrichrRunList = map["enrichrRunList"] as? List<String>,
        encrypted = map["encrypted"] as? Boolean ?: false,
        columnSize = parseColumnSize(map["columnSize"] as? Map<String, Any?>),
        violinPointPos = map["violinPointPos"] as? Int ?: -2,
        extraData = parseExtraData(map["extraData"] as? List<Map<String, Any?>>),
        imputationMap = map["imputationMap"] as? Map<String, Any> ?: mapOf(),
        enableImputation = map["enableImputation"] as? Boolean ?: false,
        viewPeptideCount = map["viewPeptideCount"] as? Boolean ?: false,
        peptideCountData = map["peptideCountData"] as? Map<String, Any> ?: mapOf(),
        viewPeptideCountData = map["viewPeptideCountData"] as? Boolean ?: false
    )
    return result
}

// Helper functions to parse nested structures
private fun parseProject(map: Map<String, Any?>?): Project {
    if (map == null) return Project()

    return Project(
        title = map["title"] as? String ?: "",
        projectDescription = map["projectDescription"] as? String ?: "",
        organisms = parseNameItemList(map["organisms"] as? List<Map<String, Any?>>),
        organismParts = parseNameItemList(map["organismParts"] as? List<Map<String, Any?>>),
        cellTypes = parseNameItemList(map["cellTypes"] as? List<Map<String, Any?>>),
        diseases = parseNameItemList(map["diseases"] as? List<Map<String, Any?>>),
        sampleProcessingProtocol = map["sampleProcessingProtocol"] as? String ?: "",
        dataProcessingProtocol = map["dataProcessingProtocol"] as? String ?: "",
        identifiedPTMStrings = parseNameItemList(map["identifiedPTMStrings"] as? List<Map<String, Any?>>),
        instruments = parseInstrumentList(map["instruments"] as? List<Map<String, Any?>>),
        msMethods = parseNameItemList(map["msMethods"] as? List<Map<String, Any?>>),
        projectTags = parseNameItemList(map["projectTags"] as? List<Map<String, Any?>>),
        quantificationMethods = parseNameItemList(map["quantificationMethods"] as? List<Map<String, Any?>>),
        species = parseNameItemList(map["species"] as? List<Map<String, Any?>>),
        sampleAnnotations = map["sampleAnnotations"] as? Map<String, Any> ?: mapOf(),
        links = parseLinks(map["_links"] as? Map<String, Any?>),
        affiliations = parseNameItemList(map["affiliations"] as? List<Map<String, Any?>>),
        hasLink = map["hasLink"] as? Boolean ?: false,
        authors = map["authors"] as? List<Any> ?: listOf(),
        accession = map["accession"] as? String ?: "",
        softwares = parseNameItemList(map["softwares"] as? List<Map<String, Any?>>),
        publicationDate = map["publicationDate"] as? Map<String, Any> ?: mapOf()
    )
}

private fun parseNameItemList(list: List<Map<String, Any?>>?): List<NameItem> {
    if (list == null) return listOf(NameItem())

    return list.map { item ->
        NameItem(
            name = item["name"] as? String ?: "",
            cvLabel = item["cvLabel"] as? String
        )
    }
}

private fun parseInstrumentList(list: List<Map<String, Any?>>?): List<InstrumentItem> {
    if (list == null) return listOf(InstrumentItem())

    return list.map { item ->
        InstrumentItem(
            cvLabel = item["cvLabel"] as? String ?: "MS",
            name = item["name"] as? String ?: ""
        )
    }
}

private fun parseLinks(map: Map<String, Any?>?): Links {
    if (map == null) return Links()

    return Links(
        datasetFtpUrl = parseLink(map["datasetFtpUrl"] as? Map<String, Any?>),
        files = parseLink(map["files"] as? Map<String, Any?>),
        self = parseLink(map["self"] as? Map<String, Any?>)
    )
}

private fun parseLink(map: Map<String, Any?>?): Link {
    if (map == null) return Link()
    return Link(href = map["href"] as? String ?: "")
}

private fun parseVolcanoAxis(map: Map<String, Any?>?): VolcanoAxis {
    Log.d("CurtainSettingsDeserializer", "Parsing VolcanoAxis: $map")
    if (map == null) return VolcanoAxis()


    val result = VolcanoAxis(
        minX = when (val value = map["minX"]) {
            is Number -> value.toDouble()
            else -> null
        },
        maxX = when (val value = map["maxX"]) {
            is Number -> value.toDouble()
            else -> null
        },
        minY = when (val value = map["minY"]) {
            is Number -> value.toDouble()
            else -> null
        },
        maxY = when (val value = map["maxY"]) {
            is Number -> value.toDouble()
            else -> null
        },
        x = map["x"] as? String ?: "Log2FC",
        y = map["y"] as? String ?: "-log10(p-value)",
        dtickX = when (val value = map["dtickX"]) {
            is Number -> value.toDouble()
            else -> null
        },
        dtickY = when (val value = map["dtickY"]) {
            is Number -> value.toDouble()
            else -> null
        },
        ticklenX = (map["ticklenX"] as? Number)?.toInt() ?: 5,
        ticklenY = (map["ticklenY"] as? Number)?.toInt() ?: 5
    )
    Log.d("CurtainSettingsDeserializer", "Parsed VolcanoAxis: $result")

    return result
}

private fun parseVolcanoPlotDimension(map: Map<String, Any?>?): VolcanoPlotDimension {
    if (map == null) return VolcanoPlotDimension()

    return VolcanoPlotDimension(
        width = map["width"] as? Int ?: 800,
        height = map["height"] as? Int ?: 1000,
        margin = parseVolcanoPlotMargin(map["margin"] as? Map<String, Any?>)
    )
}

private fun parseVolcanoPlotMargin(map: Map<String, Any?>?): VolcanoPlotMargin {
    if (map == null) return VolcanoPlotMargin()

    return VolcanoPlotMargin(
        left = map["l"] as? Int,
        right = map["r"] as? Int,
        bottom = map["b"] as? Int,
        top = map["t"] as? Int
    )
}

private fun parseColumnSize(map: Map<String, Any?>?): ColumnSize? {
    if (map == null) return null

    return ColumnSize(
        barChart = map["barChart"] as? Int ?: 0,
        averageBarChart = map["averageBarChart"] as? Int ?: 0,
        violinPlot = map["violinPlot"] as? Int ?: 0,
        profilePlot = map["profilePlot"] as? Int ?: 0
    )
}

private fun parseExtraData(list: List<Map<String, Any?>>?): List<ExtraDataItem>? {
    if (list == null) return null

    return list.map { item ->
        ExtraDataItem(
            name = item["name"] as? String ?: "",
            content = item["content"] as? String ?: "",
            type = item["type"] as? String ?: ""
        )
    }
}