package info.proteo.curtain.presentation.ui.curtain

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import info.proteo.curtain.presentation.utils.DeviceUtils

@Composable
fun AdaptiveCurtainListScreen(
    navController: NavHostController
) {
    val isTablet = DeviceUtils.isTablet()

    if (isTablet) {
        TwoPaneCurtainListScreen(
            navController = navController,
            onNavigateToQRScanner = {
                navController.navigate("qr_scanner")
            }
        )
    } else {
        CurtainListScreen(navController = navController)
    }
}
