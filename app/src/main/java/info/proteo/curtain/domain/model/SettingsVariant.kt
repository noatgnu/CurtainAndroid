package info.proteo.curtain.domain.model

import java.util.UUID

data class SettingsVariant(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val dateCreated: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis(),
    val settings: CurtainSettings,
    val selectedMap: Map<String, Map<String, Boolean>>? = null,
    val selectionsName: List<String>? = null
) {
    val pCutoff: Double get() = settings.pCutoff
    val log2FCCutoff: Double get() = settings.log2FCCutoff
    val academic: Boolean get() = settings.academic

    fun appliedTo(currentSettings: CurtainSettings): CurtainSettings {
        return currentSettings.copy(
            fetchUniprot = settings.fetchUniprot,
            pCutoff = settings.pCutoff,
            log2FCCutoff = settings.log2FCCutoff,
            uniprot = settings.uniprot,
            colorMap = settings.colorMap,
            academic = settings.academic,
            backGroundColorGrey = settings.backGroundColorGrey,
            fdrCurveText = settings.fdrCurveText,
            fdrCurveTextEnable = settings.fdrCurveTextEnable,
            volcanoAxis = settings.volcanoAxis,
            textAnnotation = settings.textAnnotation,
            volcanoPlotTitle = settings.volcanoPlotTitle,
            volcanoPlotGrid = settings.volcanoPlotGrid,
            volcanoPlotDimension = settings.volcanoPlotDimension,
            volcanoAdditionalShapes = settings.volcanoAdditionalShapes,
            volcanoPlotLegendX = settings.volcanoPlotLegendX,
            volcanoPlotLegendY = settings.volcanoPlotLegendY,
            defaultColorList = settings.defaultColorList,
            scatterPlotMarkerSize = settings.scatterPlotMarkerSize,
            plotFontFamily = settings.plotFontFamily,
            stringDBColorMap = settings.stringDBColorMap,
            interactomeAtlasColorMap = settings.interactomeAtlasColorMap,
            proteomicsDBColor = settings.proteomicsDBColor,
            networkInteractionSettings = settings.networkInteractionSettings,
            rankPlotColorMap = settings.rankPlotColorMap,
            rankPlotAnnotation = settings.rankPlotAnnotation,
            legendStatus = settings.legendStatus,
            enableImputation = settings.enableImputation,
            viewPeptideCount = settings.viewPeptideCount,
            volcanoConditionLabels = settings.volcanoConditionLabels,
            volcanoTraceOrder = settings.volcanoTraceOrder,
            volcanoPlotYaxisPosition = settings.volcanoPlotYaxisPosition,
            customVolcanoTextCol = settings.customVolcanoTextCol,
            barChartConditionBracket = settings.barChartConditionBracket,
            columnSize = settings.columnSize,
            chartYAxisLimits = settings.chartYAxisLimits,
            individualYAxisLimits = settings.individualYAxisLimits,
            violinPointPos = settings.violinPointPos,
            enrichrGeneRankMap = settings.enrichrGeneRankMap,
            enrichrRunList = settings.enrichrRunList,
            extraData = settings.extraData,
            markerSizeMap = settings.markerSizeMap
        )
    }

    fun getStoredSelectedMap(): Map<String, Map<String, Boolean>>? = selectedMap
    fun getStoredSelectionsName(): List<String>? = selectionsName
}
