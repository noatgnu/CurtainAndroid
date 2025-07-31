package info.proteo.curtain.data.models

import java.io.Serializable

data class VolcanoPointDetails(
    val proteinId: String,
    val geneName: String,
    val foldChange: Double,
    val significance: Double,
    val x: Double = foldChange,
    val y: Double = significance,
    val comparison: String = "",
    val traceGroup: String? = null,
    val traceGroupColor: String? = null
) : Serializable {
    
    /**
     * Calculate Euclidean distance to another point
     */
    fun distanceTo(other: VolcanoPointDetails): Double {
        val dx = x - other.x
        val dy = y - other.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Get display name for the point (gene name if available, otherwise protein ID)
     */
    val displayName: String
        get() = if (geneName.isNotEmpty() && geneName != proteinId) {
            "$geneName ($proteinId)"
        } else {
            proteinId
        }
}

data class VolcanoPointSelection(
    val selectedPoint: VolcanoPointDetails,
    val nearbyPoints: List<VolcanoPointDetails> = emptyList(),
    val clickX: Double = 0.0,
    val clickY: Double = 0.0
) : Serializable