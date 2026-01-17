package info.proteo.curtain.presentation.utils

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

object DeviceUtils {
    @Composable
    fun isTablet(): Boolean {
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp.dp
        return screenWidthDp >= 600.dp
    }

    fun isTablet(windowSizeClass: WindowSizeClass): Boolean {
        return windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium ||
                windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    }

    @Composable
    fun getScreenWidthDp(): Int {
        val configuration = LocalConfiguration.current
        return configuration.screenWidthDp
    }

    @Composable
    fun getScreenHeightDp(): Int {
        val configuration = LocalConfiguration.current
        return configuration.screenHeightDp
    }

    @Composable
    fun getDialogWidthFraction(windowSizeClass: WindowSizeClass): Float? {
        return when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 0.95f
            else -> null
        }
    }

    fun getDialogMaxWidthDp(windowSizeClass: WindowSizeClass): androidx.compose.ui.unit.Dp? {
        return when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Medium -> 600.dp
            WindowWidthSizeClass.Expanded -> 700.dp
            else -> null
        }
    }
}
