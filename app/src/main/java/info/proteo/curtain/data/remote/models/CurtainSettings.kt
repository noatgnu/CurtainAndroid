package info.proteo.curtain

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Settings data model that represents the Curtain settings based on the JSON.
 */
@JsonClass(generateAdapter = true)
data class CurtainSettings(
    @Json(name = "fetchUniprot") val fetchUniprot: Boolean = true,
    @Json(name = "inputDataCols") val inputDataCols: Map<String, Any> = mapOf(),
    @Json(name = "probabilityFilterMap") val probabilityFilterMap: Map<String, Any> = mapOf(),
    @Json(name = "barchartColorMap") val barchartColorMap: Map<String, Any> = mapOf(),
    @Json(name = "pCutoff") val pCutoff: Double = 0.05,
    @Json(name = "log2FCCutoff") val log2FCCutoff: Double = 0.6,
    @Json(name = "description") val description: String = "",
    @Json(name = "uniprot") val uniprot: Boolean = true,
    @Json(name = "colorMap") val colorMap: Map<String, String> = mapOf(),
    @Json(name = "academic") val academic: Boolean = true,
    @Json(name = "backGroundColorGrey") val backGroundColorGrey: Boolean = false,
    @Json(name = "currentComparison") val currentComparison: String = "",
    @Json(name = "version") val version: Double = 2.0,
    @Json(name = "currentID") val currentId: String = "",
    @Json(name = "fdrCurveText") val fdrCurveText: String = "",
    @Json(name = "fdrCurveTextEnable") val fdrCurveTextEnable: Boolean = false,
    @Json(name = "prideAccession") val prideAccession: String = "",
    @Json(name = "project") val project: Project = Project(),
    @Json(name = "sampleOrder") val sampleOrder: Map<String, List<String>> = mapOf(),
    @Json(name = "sampleVisible") val sampleVisible: Map<String, Boolean> = mapOf(),
    @Json(name = "conditionOrder") val conditionOrder: List<String> = listOf(),
    @Json(name = "volcanoAxis") val volcanoAxis: VolcanoAxis = VolcanoAxis(),
    @Json(name = "textAnnotation") val textAnnotation: Map<String, Any> = mapOf(),
    @Json(name = "volcanoPlotTitle") val volcanoPlotTitle: String = "",
    @Json(name = "visible") val visible: Map<String, Any> = mapOf(),
    @Json(name = "defaultColorList") val defaultColorList: List<String> = listOf(
        "#fd7f6f", "#7eb0d5", "#b2e061", "#bd7ebe", "#ffb55a",
        "#ffee65", "#beb9db", "#fdcce5", "#8bd3c7"
    ),
    @Json(name = "scatterPlotMarkerSize") val scatterPlotMarkerSize: Double = 10.0,
    @Json(name = "rankPlotColorMap") val rankPlotColorMap: Map<String, Any> = mapOf(),
    @Json(name = "rankPlotAnnotation") val rankPlotAnnotation: Map<String, Any> = mapOf(),
    @Json(name = "legendStatus") val legendStatus: Map<String, Any> = mapOf(),
    @Json(name = "stringDBColorMap") val stringDBColorMap: Map<String, String> = mapOf(
        "Increase" to "#8d0606",
        "Decrease" to "#4f78a4",
        "In dataset" to "#ce8080",
        "Not in dataset" to "#676666"
    ),
    @Json(name = "interactomeAtlasColorMap") val interactomeAtlasColorMap: Map<String, String> = mapOf(
        "Increase" to "#a12323",
        "Decrease" to "#16458c",
        "HI-Union" to "rgba(82,110,194,0.96)",
        "Literature" to "rgba(181,151,222,0.96)",
        "HI-Union and Literature" to "rgba(222,178,151,0.96)",
        "Not found" to "rgba(25,128,128,0.96)",
        "No change" to "rgba(47,39,40,0.96)"
    ),
    @Json(name = "proteomicsDBColor") val proteomicsDBColor: String = "#ff7f0e",
    @Json(name = "networkInteractionSettings") val networkInteractionSettings: Map<String, String> = mapOf(
        "Increase" to "rgba(220,169,0,0.96)",
        "Decrease" to "rgba(220,0,59,0.96)",
        "StringDB" to "rgb(206,128,128)",
        "No change" to "rgba(47,39,40,0.96)",
        "Not significant" to "rgba(255,255,255,0.96)",
        "Significant" to "rgba(252,107,220,0.96)",
        "InteractomeAtlas" to "rgb(73,73,101)"
    ),
    @Json(name = "plotFontFamily") val plotFontFamily: String = "Arial",

    // Additional fields from the example JSON
    @Json(name = "volcanoPlotGrid") val volcanoPlotGrid: Map<String, Boolean> = mapOf("x" to true, "y" to true),
    @Json(name = "volcanoPlotYaxisPosition") val volcanoPlotYaxisPosition: List<String> = listOf("middle"),
    @Json(name = "volcanoPlotDimension") val volcanoPlotDimension: VolcanoPlotDimension? = VolcanoPlotDimension(),
    @Json(name = "volcanoAdditionalShapes") val volcanoAdditionalShapes: List<Any> = listOf(),
    @Json(name = "volcanoPlotLegendX") val volcanoPlotLegendX: Double? = null,
    @Json(name = "volcanoPlotLegendY") val volcanoPlotLegendY: Double? = null,
    @Json(name = "sampleMap") val sampleMap: Map<String, Map<String, String>> = mapOf(),
    @Json(name = "customVolcanoTextCol") val customVolcanoTextCol: String? = null,

    // Missing fields found in JS but not in Kotlin
    @Json(name = "dataAnalysisContact") val dataAnalysisContact: String? = null,
    @Json(name = "selectedComparison") val selectedComparison: List<String>? = null,
    @Json(name = "networkInteractionData") val networkInteractionData: List<Any>? = null,
    @Json(name = "enrichrGeneRankMap") val enrichrGeneRankMap: Map<String, Any>? = null,
    @Json(name = "enrichrRunList") val enrichrRunList: List<String>? = null,
    @Json(name = "encrypted") val encrypted: Boolean? = false,
    @Json(name = "columnSize") val columnSize: ColumnSize? = null,
    @Json(name = "violinPointPos") val violinPointPos: Int? = -2,
    @Json(name = "extraData") val extraData: List<ExtraDataItem>? = null,
    @Json(name = "imputationMap") val imputationMap: Map<String, Any> = mapOf(),
    @Json(name = "enableImputation") val enableImputation: Boolean = false,
    @Json(name = "viewPeptideCount") val viewPeptideCount: Boolean? = false,
    @Json(name = "peptideCountData") val peptideCountData: Map<String, Any> = mapOf(),
    @Json(name = "viewPeptideCountData") val viewPeptideCountData: Boolean = false,
)

/**
 * Data class representing an extra data item
 */
@JsonClass(generateAdapter = true)
data class ExtraDataItem(
    @Json(name = "name") val name: String = "",
    @Json(name = "content") val content: String = "",
    @Json(name = "type") val type: String = ""
)
/**
 * Data class representing the volcano axis settings
 */
@JsonClass(generateAdapter = true)
data class VolcanoAxis(
    @Json(name = "minX") val minX: Double? = null,
    @Json(name = "maxX") val maxX: Double? = null,
    @Json(name = "minY") val minY: Double? = null,
    @Json(name = "maxY") val maxY: Double? = null,
    // Additional fields from JavaScript implementation
    @Json(name = "x") val x: String = "Log2FC",
    @Json(name = "y") val y: String = "-log10(p-value)",
    @Json(name = "dtickX") val dtickX: Double? = null,
    @Json(name = "dtickY") val dtickY: Double? = null,
    @Json(name = "ticklenX") val ticklenX: Int = 5,
    @Json(name = "ticklenY") val ticklenY: Int = 5
)

/**
 * Data class representing a project
 */
@JsonClass(generateAdapter = true)
data class Project(
    @Json(name = "title") val title: String = "",
    @Json(name = "projectDescription") val projectDescription: String = "",
    @Json(name = "organisms") val organisms: List<NameItem> = listOf(NameItem()),
    @Json(name = "organismParts") val organismParts: List<NameItem> = listOf(NameItem()),
    @Json(name = "cellTypes") val cellTypes: List<NameItem> = listOf(NameItem()),
    @Json(name = "diseases") val diseases: List<NameItem> = listOf(NameItem()),
    @Json(name = "sampleProcessingProtocol") val sampleProcessingProtocol: String = "",
    @Json(name = "dataProcessingProtocol") val dataProcessingProtocol: String = "",
    @Json(name = "identifiedPTMStrings") val identifiedPTMStrings: List<NameItem> = listOf(NameItem()),
    @Json(name = "instruments") val instruments: List<InstrumentItem> = listOf(InstrumentItem()),
    @Json(name = "msMethods") val msMethods: List<NameItem> = listOf(NameItem()),
    @Json(name = "projectTags") val projectTags: List<NameItem> = listOf(NameItem()),
    @Json(name = "quantificationMethods") val quantificationMethods: List<NameItem> = listOf(NameItem()),
    @Json(name = "species") val species: List<NameItem> = listOf(NameItem()),
    @Json(name = "sampleAnnotations") val sampleAnnotations: Map<String, Any> = mapOf(),
    @Json(name = "_links") val links: Links = Links(),
    @Json(name = "affiliations") val affiliations: List<NameItem> = listOf(NameItem()),
    @Json(name = "hasLink") val hasLink: Boolean = false,
    @Json(name = "authors") val authors: List<Any> = listOf(),
    @Json(name = "accession") val accession: String = "",
    @Json(name = "softwares") val softwares: List<NameItem> = listOf(NameItem()),
    @Json(name = "publicationDate") val publicationDate: Map<String, Any> = mapOf()
)

/**
 * Data class representing a simple name-item pair
 */
@JsonClass(generateAdapter = true)
data class NameItem(
    @Json(name = "name") val name: String = "",
    @Json(name = "cvLabel") val cvLabel: String? = null  // Used in some items like instruments
)

/**
 * Data class representing an instrument item
 */
@JsonClass(generateAdapter = true)
data class InstrumentItem(
    @Json(name = "cvLabel") val cvLabel: String = "MS",
    @Json(name = "name") val name: String = ""
)

/**
 * Data class representing the links in a project
 */
@JsonClass(generateAdapter = true)
data class Links(
    @Json(name = "datasetFtpUrl") val datasetFtpUrl: Link = Link(),
    @Json(name = "files") val files: Link = Link(),
    @Json(name = "self") val self: Link = Link()
)

/**
 * Data class representing a single link
 */
@JsonClass(generateAdapter = true)
data class Link(
    @Json(name = "href") val href: String = ""
)


/**
 * Data class representing differential form settings for comparative analysis
 */
@JsonClass(generateAdapter = true)
data class DifferentialForm(
    @Json(name = "_primaryIDs") val primaryIDs: String = "",
    @Json(name = "_geneNames") val geneNames: String = "",
    @Json(name = "_foldChange") val foldChange: String = "",
    @Json(name = "_transformFC") val transformFC: Boolean = false,
    @Json(name = "_significant") val significant: String = "",
    @Json(name = "_transformSignificant") val transformSignificant: Boolean = false,
    @Json(name = "_comparison") val comparison: String = "",
    @Json(name = "_comparisonSelect") val comparisonSelect: List<String> = listOf(),
    @Json(name = "_reverseFoldChange") val reverseFoldChange: Boolean = false
)

/**
 * Data class representing a raw form for data parsing configuration
 */
@JsonClass(generateAdapter = true)
data class RawForm(
    @Json(name = "_primaryIDs") val primaryIDs: String = "",
    @Json(name = "_samples") val samples: List<String> = listOf(),
    @Json(name = "_log2") val log2: Boolean = false
)

@JsonClass(generateAdapter = true)
data class VolcanoPlotDimension(
    @Json(name = "width") val width: Int = 800,
    @Json(name = "height") val height: Int = 1000,
    @Json(name = "margin") val margin: VolcanoPlotMargin = VolcanoPlotMargin()
)

@JsonClass(generateAdapter = true)
data class VolcanoPlotMargin(
    @Json(name = "l") val left: Int? = null,
    @Json(name = "r") val right: Int? = null,
    @Json(name = "b") val bottom: Int? = null,
    @Json(name = "t") val top: Int? = null
)

@JsonClass(generateAdapter = true)
data class ColumnSize(
    @Json(name = "barChart") val barChart: Int = 0,
    @Json(name = "averageBarChart") val averageBarChart: Int = 0,
    @Json(name = "violinPlot") val violinPlot: Int = 0,
    @Json(name = "profilePlot") val profilePlot: Int = 0
)