package info.proteo.curtain.presentation.utils

import android.graphics.PointF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import info.proteo.curtain.domain.model.AnnotationEditCandidate
import info.proteo.curtain.domain.model.VolcanoAxis
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class JSCoordinateResult(
    val screenX: Double,
    val screenY: Double,
    val ax: Double,
    val ay: Double
)

object AnnotationCoordinateCalculator {

    fun findAnnotationsNearPoint(
        tapPoint: Offset,
        maxDistance: Double,
        textAnnotations: Map<String, Any>,
        volcanoAxis: VolcanoAxis,
        viewSize: Size,
        plotMargin: info.proteo.curtain.domain.model.VolcanoPlotMargin,
        jsDimensions: Map<String, Any>? = null
    ): List<AnnotationEditCandidate> {
        android.util.Log.d("AnnotationCoordCalc", "=== findAnnotationsNearPoint ===")
        android.util.Log.d("AnnotationCoordCalc", "tapPoint: $tapPoint, maxDistance: $maxDistance")
        android.util.Log.d("AnnotationCoordCalc", "viewSize: $viewSize")
        android.util.Log.d("AnnotationCoordCalc", "textAnnotations count: ${textAnnotations.size}")

        val candidates = mutableListOf<AnnotationEditCandidate>()

        val plotWidth = viewSize.width.toDouble()
        val plotHeight = viewSize.height.toDouble()

        val marginLeft = (plotMargin.left ?: 70).toDouble()
        val marginRight = (plotMargin.right ?: 40).toDouble()
        val marginTop = (plotMargin.top ?: 60).toDouble()
        val marginBottom = (plotMargin.bottom ?: 120).toDouble()

        val plotAreaWidth = plotWidth - marginLeft - marginRight
        val plotAreaHeight = plotHeight - marginTop - marginBottom

        val xMin = volcanoAxis.minX ?: -3.0
        val xMax = volcanoAxis.maxX ?: 3.0
        val yMin = volcanoAxis.minY ?: 0.0
        val yMax = volcanoAxis.maxY ?: 5.0

        android.util.Log.d("AnnotationCoordCalc", "Plot area: ${plotAreaWidth}x${plotAreaHeight}, margins: L=$marginLeft R=$marginRight T=$marginTop B=$marginBottom")
        android.util.Log.d("AnnotationCoordCalc", "Axis ranges: X=$xMin to $xMax, Y=$yMin to $yMax")

        var checkedCount = 0
        for ((key, value) in textAnnotations) {
            checkedCount++
            val annotationData = value as? Map<String, Any> ?: continue
            val dataSection = annotationData["data"] as? Map<String, Any> ?: continue
            val title = annotationData["title"] as? String ?: continue
            val arrowX = dataSection["x"] as? Double ?: continue
            val arrowY = dataSection["y"] as? Double ?: continue
            val text = dataSection["text"] as? String ?: continue

            val viewArrowX = marginLeft + ((arrowX - xMin) / (xMax - xMin)) * plotAreaWidth
            val viewArrowY = plotHeight - marginBottom - ((arrowY - yMin) / (yMax - yMin)) * plotAreaHeight

            val ax = dataSection["ax"] as? Double ?: -20.0
            val ay = dataSection["ay"] as? Double ?: -20.0

            val viewTextX = viewArrowX + ax
            val viewTextY = viewArrowY + ay

            val distance = sqrt(
                (tapPoint.x.toDouble() - viewTextX).pow(2) +
                (tapPoint.y.toDouble() - viewTextY).pow(2)
            )

            if (checkedCount <= 3) {
                android.util.Log.d("AnnotationCoordCalc", "Annotation[$checkedCount] '$title': arrow=($arrowX,$arrowY) viewArrow=($viewArrowX,$viewArrowY) offset=($ax,$ay) viewText=($viewTextX,$viewTextY) distance=$distance")
            }

            if (distance <= maxDistance) {
                android.util.Log.d("AnnotationCoordCalc", "✓ Found candidate: '$title' at distance $distance")
                val candidate = AnnotationEditCandidate(
                    key = key,
                    title = title,
                    currentText = text,
                    arrowPosition = Pair(arrowX, arrowY),
                    textPosition = Pair(viewTextX, viewTextY),
                    distance = distance
                )
                candidates.add(candidate)
            }
        }

        android.util.Log.d("AnnotationCoordCalc", "Checked $checkedCount annotations, found ${candidates.size} candidates")
        return candidates.sortedBy { it.distance }
    }

    fun getArrowPosition(
        candidate: AnnotationEditCandidate,
        viewSize: Size,
        volcanoAxis: VolcanoAxis,
        jsCoordinates: List<Map<String, Any>>? = null,
        jsDimensions: Map<String, Any>? = null
    ): Offset? {
        jsCoordinates?.forEach { coord ->
            val plotX = coord["plotX"] as? Double
            val plotY = coord["plotY"] as? Double
            val screenX = coord["screenX"] as? Double
            val screenY = coord["screenY"] as? Double

            if (plotX != null && plotY != null && screenX != null && screenY != null) {
                if (abs(plotX - candidate.arrowPosition.first) < 0.0001 &&
                    abs(plotY - candidate.arrowPosition.second) < 0.0001) {
                    return Offset(screenX.toFloat(), screenY.toFloat())
                }
            }

            val id = coord["id"] as? String
            if (id != null && (id == candidate.key || id == candidate.title)) {
                if (screenX != null && screenY != null) {
                    return Offset(screenX.toFloat(), screenY.toFloat())
                }
            }
        }

        val plotWidth = viewSize.width.toDouble()
        val plotHeight = viewSize.height.toDouble()

        val marginLeft: Double
        val marginRight: Double
        val marginTop: Double
        val marginBottom: Double

        if (jsDimensions != null) {
            val plotLeft = jsDimensions["plotLeft"] as? Double
            val plotRight = jsDimensions["plotRight"] as? Double
            val plotTop = jsDimensions["plotTop"] as? Double
            val plotBottom = jsDimensions["plotBottom"] as? Double

            if (plotLeft != null && plotRight != null && plotTop != null && plotBottom != null) {
                marginLeft = plotLeft
                marginRight = plotWidth - plotRight
                marginTop = plotTop
                marginBottom = plotHeight - plotBottom
            } else {
                marginLeft = 70.0
                marginRight = 40.0
                marginTop = 60.0
                marginBottom = 120.0
            }
        } else {
            marginLeft = 70.0
            marginRight = 40.0
            marginTop = 60.0
            marginBottom = 120.0
        }

        val plotAreaWidth = plotWidth - marginLeft - marginRight
        val plotAreaHeight = plotHeight - marginTop - marginBottom

        val xMin = volcanoAxis.minX ?: -3.0
        val xMax = volcanoAxis.maxX ?: 3.0
        val yMin = volcanoAxis.minY ?: 0.0
        val yMax = volcanoAxis.maxY ?: 5.0

        val arrowX = candidate.arrowPosition.first
        val arrowY = candidate.arrowPosition.second

        val viewArrowX = marginLeft + ((arrowX - xMin) / (xMax - xMin)) * plotAreaWidth
        val viewArrowY = plotHeight - marginBottom - ((arrowY - yMin) / (yMax - yMin)) * plotAreaHeight

        return Offset(viewArrowX.toFloat(), viewArrowY.toFloat())
    }

    fun getCurrentTextPosition(
        candidate: AnnotationEditCandidate,
        arrowPosition: Offset,
        annotationData: Map<String, Any>
    ): Offset {
        val dataSection = annotationData["data"] as? Map<String, Any>
        val ax = dataSection?.get("ax") as? Double ?: -20.0
        val ay = dataSection?.get("ay") as? Double ?: -20.0

        return Offset(
            (arrowPosition.x + ax).toFloat(),
            (arrowPosition.y + ay).toFloat()
        )
    }

    fun findJavaScriptCoordinates(
        plotX: Double,
        plotY: Double,
        jsCoordinates: List<Map<String, Any>>?
    ): JSCoordinateResult? {
        jsCoordinates?.forEach { coord ->
            val coordPlotX = coord["plotX"] as? Double
            val coordPlotY = coord["plotY"] as? Double
            val screenX = coord["screenX"] as? Double
            val screenY = coord["screenY"] as? Double
            val ax = coord["ax"] as? Double
            val ay = coord["ay"] as? Double

            if (coordPlotX != null && coordPlotY != null &&
                screenX != null && screenY != null &&
                ax != null && ay != null) {
                if (abs(coordPlotX - plotX) < 0.0001 &&
                    abs(coordPlotY - plotY) < 0.0001) {
                    return JSCoordinateResult(screenX, screenY, ax, ay)
                }
            }
        }
        return null
    }
}
