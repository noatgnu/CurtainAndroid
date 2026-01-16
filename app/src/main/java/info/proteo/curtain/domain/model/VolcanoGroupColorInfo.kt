package info.proteo.curtain.domain.model

import androidx.compose.ui.graphics.Color

data class VolcanoGroupColorInfo(
    val name: String,
    val type: VolcanoColorType,
    var hexColor: String,
    var alpha: Float = 1.0f
) {
    val color: Color
        get() = hexColor.toComposeColor() ?: Color.Gray

    val displayColor: Color
        get() = color.copy(alpha = alpha)

    val argbString: String
        get() {
            val alphaInt = (alpha * 255).toInt()
            val colorValue = android.graphics.Color.parseColor(hexColor)
            val red = android.graphics.Color.red(colorValue)
            val green = android.graphics.Color.green(colorValue)
            val blue = android.graphics.Color.blue(colorValue)
            return String.format("#%02X%02X%02X%02X", alphaInt, red, green, blue)
        }

    fun updateFromHex(hex: String) {
        this.hexColor = hex
    }

    fun updateFromARGB(argb: String) {
        if (argb.startsWith("#") && argb.length == 9) {
            val alphaHex = argb.substring(1, 3)
            val colorHex = "#" + argb.substring(3)

            val alphaValue = alphaHex.toIntOrNull(16)
            if (alphaValue != null) {
                this.alpha = alphaValue / 255.0f
            }
            this.hexColor = colorHex
        }
    }
}

enum class VolcanoColorType(val displayName: String, val description: String, val icon: String) {
    VOLCANO_PLOT_COLORS(
        "Volcano Plot Colors",
        "Colors for search/selection groups and significance categories displayed on volcano plots",
        "chart.xyaxis.line"
    )
}

private fun String.toComposeColor(): Color? {
    return try {
        val colorInt = android.graphics.Color.parseColor(this)
        Color(colorInt)
    } catch (e: Exception) {
        null
    }
}
