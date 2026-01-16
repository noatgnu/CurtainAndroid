package info.proteo.curtain.domain.model

import java.util.UUID

data class PlotAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val text: String,
    val x: Double,
    val y: Double,
    val xref: String? = "x",
    val yref: String? = "y",
    val showarrow: Boolean = true,
    val arrowhead: Int? = 2,
    val arrowsize: Double? = 1.0,
    val arrowwidth: Double? = 2.0,
    val arrowcolor: String? = "#000000",
    val ax: Double? = -20.0,
    val ay: Double? = -20.0,
    val xanchor: String? = "auto",
    val yanchor: String? = "auto",
    val font: AnnotationFont? = null
) {
    fun toDictionary(): Map<String, Any> {
        val dict = mutableMapOf<String, Any>(
            "text" to text,
            "x" to x,
            "y" to y,
            "showarrow" to showarrow
        )

        xref?.let { dict["xref"] = it }
        yref?.let { dict["yref"] = it }
        arrowhead?.let { dict["arrowhead"] = it }
        arrowsize?.let { dict["arrowsize"] = it }
        arrowwidth?.let { dict["arrowwidth"] = it }
        arrowcolor?.let { dict["arrowcolor"] = it }
        ax?.let { dict["ax"] = it }
        ay?.let { dict["ay"] = it }
        xanchor?.let { dict["xanchor"] = it }
        yanchor?.let { dict["yanchor"] = it }
        font?.let { dict["font"] = it.toDictionary() }

        return dict
    }
}

data class AnnotationFont(
    val family: String = "Arial",
    val size: Double? = 12.0,
    val color: String? = "#000000"
) {
    fun toDictionary(): Map<String, Any> {
        val dict = mutableMapOf<String, Any>("family" to family)
        size?.let { dict["size"] = it }
        color?.let { dict["color"] = it }
        return dict
    }
}

data class AnnotationEditCandidate(
    val key: String,
    val title: String,
    val currentText: String,
    val arrowPosition: Pair<Double, Double>,
    val textPosition: Pair<Double, Double>,
    val distance: Double
)

enum class AnnotationEditAction {
    EDIT_TEXT,
    MOVE_TEXT,
    MOVE_TEXT_INTERACTIVE
}
