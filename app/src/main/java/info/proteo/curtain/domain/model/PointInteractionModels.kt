package info.proteo.curtain.domain.model

data class VolcanoPointClickData(
    val clickedProtein: ProteinPoint,
    val nearbyProteins: List<NearbyProtein>,
    val clickPosition: android.graphics.PointF,
    val plotCoordinates: PlotCoordinates
)

data class NearbyProtein(
    val protein: ProteinPoint,
    val distance: Double,
    val deltaX: Double,
    val deltaY: Double
)

data class PlotCoordinates(
    val x: Double,
    val y: Double
)

data class ProteinPoint(
    val id: String,
    val primaryID: String,
    val geneNames: String?,
    val proteinName: String?,
    val log2FC: Double,
    val pValue: Double,
    val negLog10PValue: Double,
    val color: String,
    val isSignificant: Boolean
)

object DistanceCalculator {
    fun calculateEuclideanDistance(
        point1: PlotCoordinates,
        point2: PlotCoordinates
    ): Double {
        val deltaX = point1.x - point2.x
        val deltaY = point1.y - point2.y
        return kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
    }

    fun findNearbyProteins(
        centerProtein: ProteinPoint,
        allProteins: List<ProteinPoint>,
        distanceCutoff: Double = 1.0
    ): List<NearbyProtein> {
        val centerCoords = PlotCoordinates(
            x = centerProtein.log2FC,
            y = centerProtein.negLog10PValue
        )

        val nearbyProteins = mutableListOf<NearbyProtein>()

        for (protein in allProteins) {
            if (protein.id == centerProtein.id) {
                continue
            }

            val proteinCoords = PlotCoordinates(
                x = protein.log2FC,
                y = protein.negLog10PValue
            )

            val distance = calculateEuclideanDistance(centerCoords, proteinCoords)
            val deltaX = proteinCoords.x - centerCoords.x
            val deltaY = proteinCoords.y - centerCoords.y

            if (distance <= distanceCutoff) {
                nearbyProteins.add(
                    NearbyProtein(
                        protein = protein,
                        distance = distance,
                        deltaX = deltaX,
                        deltaY = deltaY
                    )
                )
            }
        }

        return nearbyProteins.sortedBy { it.distance }
    }
}
