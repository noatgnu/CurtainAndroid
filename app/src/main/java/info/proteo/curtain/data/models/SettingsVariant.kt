package info.proteo.curtain.data.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable
import java.util.Date

/**
 * Data models for local settings variants that can be saved and restored.
 * Based on the frontend save state system, this includes all user-customizable
 * settings that are not derived from data files.
 */

@JsonClass(generateAdapter = true)
data class SettingsVariant(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "createdAt") val createdAt: Long = System.currentTimeMillis(),
    @Json(name = "lastModified") val lastModified: Long = System.currentTimeMillis(),
    @Json(name = "lastUsed") val lastUsed: Long? = null,
    @Json(name = "usageCount") val usageCount: Int = 0,
    @Json(name = "isDefault") val isDefault: Boolean = false,
    @Json(name = "isFavorite") val isFavorite: Boolean = false,
    @Json(name = "tags") val tags: List<String> = emptyList(),
    @Json(name = "version") val version: String = "1.0",
    
    // Visual and Analysis Settings
    @Json(name = "visualSettings") val visualSettings: VisualSettings,
    @Json(name = "analysisSettings") val analysisSettings: AnalysisSettings,
    @Json(name = "searchSettings") val searchSettings: SearchSettings,
    @Json(name = "conditionSettings") val conditionSettings: ConditionSettings,
    @Json(name = "plotSettings") val plotSettings: PlotSettings,
    @Json(name = "appPreferences") val appPreferences: AppPreferences
) : Serializable

@JsonClass(generateAdapter = true)
data class VisualSettings(
    // Color Management
    @Json(name = "colorPalette") val colorPalette: String = "pastel",
    @Json(name = "customColorMap") val customColorMap: Map<String, String> = emptyMap(),
    @Json(name = "customColors") val customColors: Map<String, String> = emptyMap(),
    @Json(name = "colorPalettes") val colorPalettes: List<String> = emptyList(),
    @Json(name = "customTheme") val customTheme: String? = null,
    @Json(name = "backgroundColorGrey") val backgroundColorGrey: Boolean = false,
    @Json(name = "defaultColorList") val defaultColorList: List<String> = emptyList(),
    
    // Plot Appearance
    @Json(name = "plotFontFamily") val plotFontFamily: String = "Arial",
    @Json(name = "scatterPlotMarkerSize") val scatterPlotMarkerSize: Double = 10.0,
    @Json(name = "volcanoPlotGrid") val volcanoPlotGrid: Map<String, Boolean> = mapOf("x" to true, "y" to true),
    @Json(name = "volcanoPlotYaxisPosition") val volcanoPlotYaxisPosition: List<String> = listOf("middle"),
    
    // Dimensions and Layout
    @Json(name = "volcanoPlotDimension") val volcanoPlotDimension: PlotDimension? = null,
    @Json(name = "columnSize") val columnSize: ColumnSize? = null,
    @Json(name = "volcanoPlotLegendX") val volcanoPlotLegendX: Double? = null,
    @Json(name = "volcanoPlotLegendY") val volcanoPlotLegendY: Double? = null
) : Serializable

@JsonClass(generateAdapter = true)
data class AnalysisSettings(
    // Statistical Thresholds
    @Json(name = "pCutoff") val pCutoff: Double = 0.05,
    @Json(name = "log2FCCutoff") val log2FCCutoff: Double = 0.6,
    
    // Data Processing
    @Json(name = "enableImputation") val enableImputation: Boolean = false,
    @Json(name = "viewPeptideCount") val viewPeptideCount: Boolean = false,
    @Json(name = "fetchUniprot") val fetchUniprot: Boolean = true,
    
    // Comparison Settings
    @Json(name = "currentComparison") val currentComparison: String = "",
    @Json(name = "selectedComparison") val selectedComparison: List<String> = emptyList(),
    @Json(name = "comparisonSettings") val comparisonSettings: Map<String, Any> = emptyMap(),
    @Json(name = "statisticalMethods") val statisticalMethods: List<String> = emptyList(),
    
    // Visibility Settings
    @Json(name = "sampleVisible") val sampleVisible: Map<String, Boolean> = emptyMap(),
    @Json(name = "legendStatus") val legendStatus: Map<String, Boolean> = emptyMap()
) : Serializable

@JsonClass(generateAdapter = true)
data class SearchSettings(
    // Search Lists
    @Json(name = "searchLists") val searchLists: List<SavedSearchList> = emptyList(),
    @Json(name = "savedSearches") val savedSearches: List<SavedSearchList> = emptyList(),
    @Json(name = "customLists") val customLists: List<SavedSearchList> = emptyList(),
    @Json(name = "activeFilters") val activeFilters: List<String> = emptyList(),
    @Json(name = "searchHistory") val searchHistory: List<String> = emptyList(),
    
    // Filter Settings
    @Json(name = "useSearchFilter") val useSearchFilter: Boolean = false,
    @Json(name = "showOnlySelected") val showOnlySelected: Boolean = false,
    @Json(name = "includeEmptyLists") val includeEmptyLists: Boolean = true
) : Serializable

@JsonClass(generateAdapter = true)
data class ConditionSettings(
    // Condition Management
    @Json(name = "conditionOrder") val conditionOrder: List<String> = emptyList(),
    @Json(name = "conditionColors") val conditionColors: Map<String, String> = emptyMap(),
    @Json(name = "conditionVisible") val conditionVisible: Map<String, Boolean> = emptyMap(),
    @Json(name = "sampleOrder") val sampleOrder: Map<String, List<String>> = emptyMap(),
    
    // Grouping and Classification
    @Json(name = "customConditionMap") val customConditionMap: Map<String, String> = emptyMap()
) : Serializable

@JsonClass(generateAdapter = true)
data class PlotSettings(
    // Volcano Plot
    @Json(name = "volcanoAxis") val volcanoAxis: VolcanoAxisSettings? = null,
    @Json(name = "volcanoPlotTitle") val volcanoPlotTitle: String = "",
    @Json(name = "volcanoAdditionalShapes") val volcanoAdditionalShapes: List<Map<String, Any>> = emptyList(),
    @Json(name = "customVolcanoTextCol") val customVolcanoTextCol: String? = null,
    @Json(name = "fdrCurveText") val fdrCurveText: String = "",
    @Json(name = "fdrCurveTextEnable") val fdrCurveTextEnable: Boolean = false,
    @Json(name = "textAnnotation") val textAnnotation: Map<String, Any> = emptyMap(),
    
    // Chart-specific Settings
    @Json(name = "chartType") val chartType: String = "INDIVIDUAL_BAR",
    @Json(name = "chartConfigs") val chartConfigs: Map<String, Any> = emptyMap(),
    @Json(name = "defaultLayouts") val defaultLayouts: Map<String, Any> = emptyMap(),
    @Json(name = "violinPointPos") val violinPointPos: Int = -2,
    
    // Network and String DB Colors
    @Json(name = "stringDBColorMap") val stringDBColorMap: Map<String, String> = emptyMap(),
    @Json(name = "interactomeAtlasColorMap") val interactomeAtlasColorMap: Map<String, String> = emptyMap(),
    @Json(name = "networkInteractionSettings") val networkInteractionSettings: Map<String, String> = emptyMap(),
    @Json(name = "proteomicsDBColor") val proteomicsDBColor: String = "#ff7f0e",
    
    // Rank Plot Settings
    @Json(name = "rankPlotColorMap") val rankPlotColorMap: Map<String, String> = emptyMap(),
    @Json(name = "rankPlotAnnotation") val rankPlotAnnotation: Map<String, String> = emptyMap()
) : Serializable

@JsonClass(generateAdapter = true)
data class AppPreferences(
    // UI Preferences
    @Json(name = "theme") val theme: String = "system", // light, dark, system
    @Json(name = "language") val language: String = "en",
    @Json(name = "autoSave") val autoSave: Boolean = true,
    @Json(name = "confirmBeforeReset") val confirmBeforeReset: Boolean = true,
    
    // Performance Settings
    @Json(name = "maxProteinsPerPage") val maxProteinsPerPage: Int = 20,
    @Json(name = "enableAnimations") val enableAnimations: Boolean = true,
    @Json(name = "cacheSize") val cacheSize: Long = 100L * 1024 * 1024, // 100MB
    
    // Export/Import Settings
    @Json(name = "defaultExportFormat") val defaultExportFormat: String = "json",
    @Json(name = "includeMetadataInExport") val includeMetadataInExport: Boolean = true
) : Serializable

// Supporting Data Classes

@JsonClass(generateAdapter = true)
data class SavedSearchList(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "color") val color: String,
    @Json(name = "proteinIds") val proteinIds: List<String>,
    @Json(name = "searchTerms") val searchTerms: List<String> = emptyList(),
    @Json(name = "searchType") val searchType: String = "PRIMARY_ID",
    @Json(name = "description") val description: String? = null,
    @Json(name = "createdAt") val createdAt: Long = System.currentTimeMillis(),
    @Json(name = "isBuiltIn") val isBuiltIn: Boolean = false
) : Serializable

@JsonClass(generateAdapter = true)
data class VolcanoAxisSettings(
    @Json(name = "minX") val minX: Double? = null,
    @Json(name = "maxX") val maxX: Double? = null,
    @Json(name = "minY") val minY: Double? = null,
    @Json(name = "maxY") val maxY: Double? = null,
    @Json(name = "x") val x: String = "Log2FC",
    @Json(name = "y") val y: String = "-log10(p-value)",
    @Json(name = "dtickX") val dtickX: Double? = null,
    @Json(name = "dtickY") val dtickY: Double? = null,
    @Json(name = "ticklenX") val ticklenX: Int = 5,
    @Json(name = "ticklenY") val ticklenY: Int = 5
) : Serializable

@JsonClass(generateAdapter = true)
data class PlotDimension(
    @Json(name = "width") val width: Int = 800,
    @Json(name = "height") val height: Int = 1000,
    @Json(name = "margin") val margin: PlotMargin = PlotMargin()
) : Serializable

@JsonClass(generateAdapter = true)
data class PlotMargin(
    @Json(name = "l") val left: Int? = null,
    @Json(name = "r") val right: Int? = null,
    @Json(name = "b") val bottom: Int? = null,
    @Json(name = "t") val top: Int? = null
) : Serializable

@JsonClass(generateAdapter = true)
data class ColumnSize(
    @Json(name = "barChart") val barChart: Int = 0,
    @Json(name = "averageBarChart") val averageBarChart: Int = 0,
    @Json(name = "violinPlot") val violinPlot: Int = 0,
    @Json(name = "profilePlot") val profilePlot: Int = 0
) : Serializable

// Utility Data Classes

@JsonClass(generateAdapter = true)
data class SettingsVariantMetadata(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "createdAt") val createdAt: Long,
    @Json(name = "lastModified") val lastModified: Long,
    @Json(name = "isDefault") val isDefault: Boolean,
    @Json(name = "tags") val tags: List<String>,
    @Json(name = "size") val size: Long = 0L, // File size in bytes
    @Json(name = "version") val version: String,
    @Json(name = "checksum") val checksum: String? = null
) : Serializable

enum class SettingsCategory {
    VISUAL,
    ANALYSIS,
    SEARCH,
    CONDITIONS,
    PLOTS,
    PREFERENCES,
    ALL
}

data class SettingsImportResult(
    val success: Boolean,
    val settingsVariant: SettingsVariant? = null,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val imported: List<SettingsCategory> = emptyList(),
    val skipped: List<SettingsCategory> = emptyList()
)