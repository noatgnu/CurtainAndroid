package info.proteo.curtain.domain.service

import info.proteo.curtain.domain.model.CurtainData
import info.proteo.curtain.domain.model.CurtainSettings
import info.proteo.curtain.domain.model.VolcanoAxis
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

@Singleton
class VolcanoPlotDataService @Inject constructor(
    private val proteomicsDataService: ProteomicsDataService
) {

    data class VolcanoProcessResult(
        val jsonData: List<Map<String, Any>>,
        val colorMap: Map<String, String>,
        val updatedVolcanoAxis: VolcanoAxis
    )

    suspend fun processVolcanoData(curtainData: CurtainData, settings: CurtainSettings): VolcanoProcessResult {

        val diffForm = curtainData.differentialForm
        val fcColumn = diffForm.foldChange
        val sigColumn = diffForm.significant
        val idColumn = diffForm.primaryIDs
        val geneColumn = diffForm.geneNames
        val comparisonColumn = diffForm.comparison

        val jsonData = mutableListOf<Map<String, Any>>()
        var minFC = 0.0
        var maxFC = 0.0
        var maxLogP = 0.0
        var firstValidPoint = true

        val db = proteomicsDataService.getDatabaseForLinkId(curtainData.linkId)
        val processedDataEntities = db.proteomicsDataDao().getAllProcessedData()

        val differentialData = processedDataEntities.map { entity ->
            mutableMapOf<String, Any>(
                idColumn to entity.primaryId
            ).apply {
                entity.geneNames?.let { put(geneColumn, it) }
                entity.foldChange?.let { put(fcColumn, it) }
                entity.significant?.let { put(sigColumn, it) }
                put(comparisonColumn, entity.comparison)
            }
        }

        val colorMap = settings.colorMap.toMutableMap()
        val selectOperationNames = extractSelectionNames(curtainData)
        var colorIndex = assignColorsToSelections(selectOperationNames, colorMap, settings)

        var actualFcKey: String? = null
        var actualSigKey: String? = null
        var actualIdKey: String? = null

        fun processRow(rowItem: Any?, mapKey: String?) {
            if (rowItem == null) return

            val row: Map<String, Any>? = when (rowItem) {
                is Map<*, *> -> @Suppress("UNCHECKED_CAST") (rowItem as Map<String, Any>)
                is List<*> -> {
                    if (rowItem.isNotEmpty() && rowItem[0] is Map<*, *>) {
                        @Suppress("UNCHECKED_CAST") (rowItem[0] as Map<String, Any>)
                    } else null
                }
                else -> null
            }

            if (row != null) {
                if (actualFcKey == null) {
                    val keys = row.keys
                    actualFcKey = keys.find { it.trim().equals(fcColumn.trim(), ignoreCase = true) }
                    actualSigKey = keys.find { it.trim().equals(sigColumn.trim(), ignoreCase = true) }
                    actualIdKey = keys.find { it.trim().equals(idColumn.trim(), ignoreCase = true) }
                }

                var id = if (actualIdKey != null) row[actualIdKey]?.toString() else null
                if (id.isNullOrEmpty()) id = mapKey
                
                if (!id.isNullOrEmpty()) {
                    val gene = resolveGeneName(id!!, row, geneColumn, curtainData)
                    val rawFcValue = extractDoubleValue(if (actualFcKey != null) row[actualFcKey] else null)
                    val rawSigValue = extractDoubleValue(if (actualSigKey != null) row[actualSigKey] else null)

                    if (!rawFcValue.isNaN() && !rawFcValue.isInfinite() && !rawSigValue.isNaN() && !rawSigValue.isInfinite()) {
                        
                        val fcValue = if (diffForm.transformFC && rawFcValue > 0) kotlin.math.log2(rawFcValue) else rawFcValue
                        val sigValue = if (diffForm.transformSignificant && rawSigValue > 0) -log10(rawSigValue) else rawSigValue

                        if (firstValidPoint) {
                            minFC = fcValue
                            maxFC = fcValue
                            maxLogP = sigValue
                            firstValidPoint = false
                        } else {
                            minFC = min(minFC, fcValue)
                            maxFC = max(maxFC, fcValue)
                            maxLogP = max(maxLogP, sigValue)
                        }

                        val rawComparisonValue = if (comparisonColumn.isEmpty()) "1" else (row[comparisonColumn]?.toString() ?: "1")
                        val comparisonValue = if (rawComparisonValue.isEmpty()) "1" else rawComparisonValue
                        
                        val selections = mutableListOf<String>()
                        val colors = mutableListOf<String>()
                        var hasUserSelection = false

                        curtainData.selectedMap?.get(id!!)?.forEach { (name, selected) ->
                            if (selected && colorMap.containsKey(name)) {
                                val comparisonMatch = Regex("""\(([^)]*)\)[^(]*$""").find(name)
                                if (comparisonMatch != null) {
                                    if (comparisonMatch.groupValues[1] == comparisonValue) {
                                        selections.add(name)
                                        colors.add(colorMap[name] ?: "#808080")
                                        hasUserSelection = true
                                    }
                                } else {
                                    selections.add(name)
                                    colors.add(colorMap[name] ?: "#808080")
                                    hasUserSelection = true
                                }
                            }
                        }

                        if (!hasUserSelection) {
                            if (settings.backGroundColorGrey) {
                                selections.add("Background")
                                colors.add("#a4a2a2") 
                            } else {
                                val (group, _) = getSignificantGroup(fcValue, sigValue, settings, comparisonValue)
                                selections.add(group)
                                
                                if (!colorMap.containsKey(group)) {
                                    val defaultColors = settings.defaultColorList
                                    if (defaultColors.isNotEmpty()) {
                                        colorMap[group] = defaultColors[colorIndex % defaultColors.size]
                                        colorIndex++
                                    } else {
                                        colorMap[group] = "#cccccc"
                                    }
                                }
                                colors.add(colorMap[group] ?: "#cccccc")
                            }
                        }

                        val dataPoint = mutableMapOf<String, Any>(
                            "x" to fcValue, "y" to sigValue, "id" to id!!,
                            "gene" to gene.replace("\"", "\\\"").replace("'", "\\'"),
                            "comparison" to comparisonValue, "selections" to selections,
                            "colors" to colors, "color" to (colors.firstOrNull() ?: "#808080")
                        )
                        if (settings.customVolcanoTextCol.isNotEmpty()) {
                            row[settings.customVolcanoTextCol]?.let { dataPoint["customText"] = it.toString() }
                        }
                        jsonData.add(dataPoint)
                    }
                }
            }
        }

        differentialData.forEach { row -> processRow(row, null) }
        
        val significantOrSelectedPoints = mutableListOf<Map<String, Any>>()
        val nonSignificantPoints = mutableListOf<Map<String, Any>>()
        
        for (point in jsonData) {
            val colors = point["colors"] as? List<String>
            if (colors?.any { it != "#cccccc" && it != "#808080" } == true) significantOrSelectedPoints.add(point)
            else nonSignificantPoints.add(point)
        }
        
        val finalJsonData = significantOrSelectedPoints + if (nonSignificantPoints.size > 2000) nonSignificantPoints.shuffled().take(2000) else nonSignificantPoints

        val updatedVolcanoAxis = VolcanoAxis(
            minX = settings.volcanoAxis.minX ?: (minFC - 1.0),
            maxX = settings.volcanoAxis.maxX ?: (maxFC + 1.0),
            minY = settings.volcanoAxis.minY ?: 0.0,
            maxY = settings.volcanoAxis.maxY ?: (maxLogP + 1.0)
        )

        return VolcanoProcessResult(finalJsonData, colorMap, updatedVolcanoAxis)
    }

    private fun parseRawProcessedString(rawContent: String, diffForm: info.proteo.curtain.domain.model.CurtainDifferentialForm): List<Map<String, Any>> {
        val lines = rawContent.lines()
        if (lines.isEmpty()) return emptyList()

        val headers = lines[0].split("\t").map { it.trim() }
        val data = mutableListOf<Map<String, Any>>()

        val fcIndex = headers.indexOfFirst { it.equals(diffForm.foldChange.trim(), ignoreCase = true) }
        val sigIndex = headers.indexOfFirst { it.equals(diffForm.significant.trim(), ignoreCase = true) }
        val idIndex = headers.indexOfFirst { it.equals(diffForm.primaryIDs.trim(), ignoreCase = true) }
        val geneIndex = headers.indexOfFirst { it.equals(diffForm.geneNames.trim(), ignoreCase = true) }
        val compIndex = headers.indexOfFirst { it.equals(diffForm.comparison.trim(), ignoreCase = true) }

        if (fcIndex == -1 || sigIndex == -1 || idIndex == -1) return emptyList()

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            
            val cols = line.split("\t")
            val maxIndex = max(max(fcIndex, sigIndex), idIndex)
            if (cols.size <= maxIndex) continue

            val rowMap = mutableMapOf<String, Any>()
            
            for (j in cols.indices) {
                if (j < headers.size) rowMap[headers[j]] = cols[j]
            }

            val rawFcStr = cols[fcIndex]
            var fcValue = rawFcStr.toDoubleOrNull() ?: 0.0
            if (diffForm.transformFC) {
                fcValue = if (fcValue > 0) kotlin.math.log2(fcValue) else 0.0
            }
            if (diffForm.reverseFoldChange) {
                fcValue = -fcValue
            }
            rowMap[diffForm.foldChange] = fcValue

            val rawSigStr = cols[sigIndex]
            var sigValue = rawSigStr.toDoubleOrNull() ?: 0.0
            if (diffForm.transformSignificant) {
                sigValue = if (sigValue > 0) -log10(sigValue) else 0.0
            }
            rowMap[diffForm.significant] = sigValue

            rowMap[diffForm.primaryIDs] = cols[idIndex]
            if (geneIndex != -1 && cols.size > geneIndex) rowMap[diffForm.geneNames] = cols[geneIndex]
            if (compIndex != -1 && cols.size > compIndex) rowMap[diffForm.comparison] = cols[compIndex]

            data.add(rowMap)
        }
        return data
    }

    private fun getUniprotFromPrimary(id: String, curtainData: CurtainData): Map<String, Any>? {
        val uniprotDB = curtainData.extraData?.uniprot?.db as? Map<String, Any>
        val dataMap = curtainData.extraData?.uniprot?.dataMap as? Map<String, Any>
        val accMap = curtainData.extraData?.uniprot?.accMap as? Map<String, Any>

        if (uniprotDB == null) return null

        if (uniprotDB.containsKey(id)) {
            return uniprotDB[id] as? Map<String, Any>
        }

        if (accMap != null && accMap.containsKey(id)) {
            val alternatives = accMap[id] as? List<*>
            if (alternatives != null) {
                for (alt in alternatives) {
                    if (dataMap != null && dataMap.containsKey(alt)) {
                        val canonicalEntry = dataMap[alt] as? String
                        if (canonicalEntry != null && uniprotDB.containsKey(canonicalEntry)) {
                            return uniprotDB[canonicalEntry] as? Map<String, Any>
                        }
                    }
                }
            }
        }

        return null
    }

    private fun resolveGeneName(id: String, row: Map<String, Any>, geneColumn: String, curtainData: CurtainData): String {
        var gene = id

        if (curtainData.fetchUniprot) {
            val uniprotRecord = getUniprotFromPrimary(id, curtainData)
            if (uniprotRecord != null) {
                val geneNames = uniprotRecord["Gene Names"] as? String
                if (!geneNames.isNullOrEmpty()) {
                    val firstGeneName = geneNames.split(" ", ";", "\\\\")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .firstOrNull()
                    if (!firstGeneName.isNullOrEmpty()) {
                        return firstGeneName
                    }
                }
            }
        }

        if (geneColumn.isNotEmpty()) {
            (row[geneColumn]?.toString())?.takeIf { it.isNotEmpty() }?.let {
                gene = it
            }
        }

        return gene
    }

    private fun extractDoubleValue(value: Any?): Double {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: Double.NaN
            else -> Double.NaN
        }
    }

    private fun extractSelectionNames(curtainData: CurtainData): Set<String> {
        val names = mutableSetOf<String>()
        curtainData.selectedMap?.forEach { (_, selections) ->
            selections.forEach { (name, selected) -> if (selected) names.add(name) }
        }
        return names
    }

    private fun assignColorsToSelections(selectOperationNames: Set<String>, colorMap: MutableMap<String, String>, settings: CurtainSettings): Int {
        val defaultColorList = settings.defaultColorList
        val currentColors = colorMap.values.filter { defaultColorList.contains(it) }
        var currentPosition = if (currentColors.size < defaultColorList.size) currentColors.size else 0
        var breakColor = false
        var shouldRepeat = false
        for (s in selectOperationNames.sorted()) {
            if (!colorMap.containsKey(s)) {
                while (true) {
                    if (breakColor) {
                        colorMap[s] = defaultColorList[currentPosition]
                        break
                    }
                    if (currentColors.contains(defaultColorList[currentPosition])) {
                        currentPosition++
                        if (shouldRepeat) {
                            colorMap[s] = defaultColorList[currentPosition]
                            currentPosition = 0
                            breakColor = true
                            break
                        }
                    } else if (currentPosition >= defaultColorList.size) {
                        currentPosition = 0
                        colorMap[s] = defaultColorList[currentPosition]
                        shouldRepeat = true
                        break
                    } else {
                        colorMap[s] = defaultColorList[currentPosition]
                        break
                    }
                }
                currentPosition++
                if (currentPosition == defaultColorList.size) currentPosition = 0
            }
        }
        return currentPosition
    }

    private fun getSignificantGroup(fcValue: Double, sigValue: Double, settings: CurtainSettings, comparison: String): Pair<String, String> {
        val ylog = -log10(settings.pCutoff)
        val groups = mutableListOf<String>()
        var position = ""
        if (sigValue < ylog) {
            groups.add("P-value > ${settings.pCutoff}")
            position = "P-value > "
        } else {
            groups.add("P-value <= ${settings.pCutoff}")
            position = "P-value <= "
        }
        if (abs(fcValue) > settings.log2FCCutoff) {
            groups.add("FC > ${settings.log2FCCutoff}")
            position += "FC > "
        } else {
            groups.add("FC <= ${settings.log2FCCutoff}")
            position += "FC <= "
        }
        return Pair("${groups.joinToString(";")} ($comparison)", position)
    }

    private fun convertDataMapToDict(dataMap: Any): Map<String, Any> {
        return when (dataMap) {
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = dataMap as Map<String, Any>
                (map["value"] as? List<*>)?.let { return convertArrayToDict(it) }
                map
            }
            is List<*> -> convertArrayToDict(dataMap)
            else -> emptyMap()
        }
    }

    private fun convertArrayToDict(arrayData: List<*>): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        arrayData.forEach { pair ->
            if (pair is List<*> && pair.size >= 2) {
                val key = pair[0]?.toString()
                val value = pair[1]
                if (key != null && value != null) result[key] = value
            }
        }
        return result
    }
}