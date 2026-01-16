package info.proteo.curtain.presentation.utils

import androidx.compose.ui.graphics.Color

fun parseColor(colorString: String): Color? {
    return try {
        Color(android.graphics.Color.parseColor(colorString))
    } catch (e: Exception) {
        null
    }
}
