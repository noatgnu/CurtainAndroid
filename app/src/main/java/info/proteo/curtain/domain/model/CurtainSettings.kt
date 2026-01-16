package info.proteo.curtain.domain.model

import com.google.gson.annotations.SerializedName

data class CurtainSettings(
    val fetchUniprot: Boolean = true,
    val inputDataCols: Map<String, Any> = emptyMap(),
    val probabilityFilterMap: Map<String, Any> = emptyMap(),
    val barchartColorMap: Map<String, Any> = emptyMap(),
    val pCutoff: Double = 0.05,
    val log2FCCutoff: Double = 0.6,
    val description: String = "",
    val uniprot: Boolean = true,
    val colorMap: Map<String, String> = emptyMap(),
    val academic: Boolean = true,
    val backGroundColorGrey: Boolean = false,
    val currentComparison: String = "",
    val version: Double = 2.0,
    @SerializedName("currentID")
    val currentId: String = "",
    val fdrCurveText: String = "",
    val fdrCurveTextEnable: Boolean = false,
    val prideAccession: String = "",
    val project: Project = Project(),
    val sampleOrder: Map<String, List<String>> = emptyMap(),
    val sampleVisible: Map<String, Boolean> = emptyMap(),
    val conditionOrder: List<String> = emptyList(),
    val sampleMap: Map<String, Map<String, String>> = emptyMap(),
    val volcanoAxis: VolcanoAxis = VolcanoAxis(),
    val textAnnotation: Map<String, Any> = emptyMap(),
    val volcanoPlotTitle: String = "",
    val visible: Map<String, Any> = emptyMap(),
    val volcanoPlotGrid: Map<String, Boolean> = mapOf("x" to true, "y" to true),
    val volcanoPlotDimension: VolcanoPlotDimension = VolcanoPlotDimension(),
    val volcanoAdditionalShapes: List<Any> = emptyList(),
    val volcanoPlotLegendX: Double? = null,
    val volcanoPlotLegendY: Double? = null,
    val defaultColorList: List<String> = listOf("#fd7f6f", "#7eb0d5", "#b2e061", "#bd7ebe", "#ffb55a", "#ffee65", "#beb9db", "#fdcce5", "#8bd3c7"),
    val scatterPlotMarkerSize: Double = 10.0,
    val plotFontFamily: String = "Arial",
    val stringDBColorMap: Map<String, String> = mapOf(
        "Increase" to "#8d0606",
        "Decrease" to "#4f78a4",
        "In dataset" to "#ce8080",
        "Not in dataset" to "#676666"
    ),
    val interactomeAtlasColorMap: Map<String, String> = mapOf(
        "Increase" to "#a12323",
        "Decrease" to "#16458c",
        "HI-Union" to "rgba(82,110,194,0.96)",
        "Literature" to "rgba(181,151,222,0.96)",
        "HI-Union and Literature" to "rgba(222,178,151,0.96)",
        "Not found" to "rgba(25,128,128,0.96)",
        "No change" to "rgba(47,39,40,0.96)"
    ),
    val proteomicsDBColor: String = "#ff7f0e",
    val networkInteractionSettings: Map<String, String> = mapOf(
        "Increase" to "rgba(220,169,0,0.96)",
        "Decrease" to "rgba(220,0,59,0.96)",
        "StringDB" to "rgb(206,128,128)",
        "No change" to "rgba(47,39,40,0.96)",
        "Not significant" to "rgba(255,255,255,0.96)",
        "Significant" to "rgba(252,107,220,0.96)",
        "InteractomeAtlas" to "rgb(73,73,101)"
    ),
    val rankPlotColorMap: Map<String, Any> = emptyMap(),
    val rankPlotAnnotation: Map<String, Any> = emptyMap(),
    val legendStatus: Map<String, Any> = emptyMap(),
    val selectedComparison: List<String>? = null,
    val imputationMap: Map<String, Any> = emptyMap(),
    val enableImputation: Boolean = false,
    val viewPeptideCount: Boolean = false,
    val peptideCountData: Map<String, Any> = emptyMap(),
    val volcanoConditionLabels: VolcanoConditionLabels = VolcanoConditionLabels(),
    val volcanoTraceOrder: List<String> = emptyList(),
    val volcanoPlotYaxisPosition: List<String> = listOf("middle"),
    val customVolcanoTextCol: String = "",
    val barChartConditionBracket: BarChartConditionBracket = BarChartConditionBracket(),
    val columnSize: Map<String, Int> = emptyMap(),
    val chartYAxisLimits: Map<String, ChartYAxisLimits> = mapOf(
        "barChart" to ChartYAxisLimits(),
        "averageBarChart" to ChartYAxisLimits(),
        "violinPlot" to ChartYAxisLimits()
    ),
    val individualYAxisLimits: Map<String, Any> = emptyMap(),
    val violinPointPos: Double = -2.0,
    val networkInteractionData: List<Any> = emptyList(),
    val enrichrGeneRankMap: Map<String, Any> = emptyMap(),
    val enrichrRunList: List<String> = emptyList(),
    val extraData: List<ExtraDataItem> = emptyList(),
    val enableMetabolomics: Boolean = false,
    val metabolomicsColumnMap: MetabolomicsColumnMap = MetabolomicsColumnMap(),
    val encrypted: Boolean = false,
    val dataAnalysisContact: String = "",
    val markerSizeMap: Map<String, Any> = emptyMap()
)

data class Project(
    val title: String = "",
    val projectDescription: String = "",
    val organisms: List<NameItem> = listOf(NameItem()),
    val organismParts: List<NameItem> = listOf(NameItem()),
    val cellTypes: List<NameItem> = listOf(NameItem()),
    val diseases: List<NameItem> = listOf(NameItem()),
    val sampleProcessingProtocol: String = "",
    val dataProcessingProtocol: String = "",
    val accession: String = "",
    val sampleAnnotations: Map<String, Any> = emptyMap()
)

data class NameItem(
    val name: String = "",
    val cvLabel: String? = null
)

data class VolcanoAxis(
    val minX: Double? = null,
    val maxX: Double? = null,
    val minY: Double? = null,
    val maxY: Double? = null,
    val x: String = "Log2FC",
    val y: String = "-log10(p-value)",
    val dtickX: Double? = null,
    val dtickY: Double? = null,
    val ticklenX: Int = 5,
    val ticklenY: Int = 5
)

data class VolcanoPlotDimension(
    val width: Int = 800,
    val height: Int = 1000,
    val margin: VolcanoPlotMargin = VolcanoPlotMargin()
)

data class VolcanoPlotMargin(
    @SerializedName("l") val left: Int? = null,
    @SerializedName("r") val right: Int? = null,
    @SerializedName("b") val bottom: Int? = null,
    @SerializedName("t") val top: Int? = null
)

data class VolcanoConditionLabels(
    val enabled: Boolean = false,
    val leftCondition: String = "",
    val rightCondition: String = "",
    val leftX: Double = 0.25,
    val rightX: Double = 0.75,
    val yPosition: Double = -0.1,
    val fontSize: Int = 14,
    val fontColor: String = "#000000"
)

data class BarChartConditionBracket(
    val showBracket: Boolean = false,
    val bracketHeight: Double = 0.05,
    val bracketColor: String = "#000000",
    val bracketWidth: Int = 2
)

data class ChartYAxisLimits(
    val min: Double? = null,
    val max: Double? = null
)

data class MetabolomicsColumnMap(
    val polarity: String? = null,
    val formula: String? = null,
    val abbreviation: String? = null,
    val smiles: String? = null
)

data class ExtraDataItem(
    val name: String = "",
    val content: String = "",
    val type: String = ""
)
