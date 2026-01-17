package info.proteo.curtain.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import info.proteo.curtain.domain.service.DeepLinkHandler
import info.proteo.curtain.domain.service.DeepLinkResult
import info.proteo.curtain.presentation.ui.chart.ProteinChartNavigationScreen
import info.proteo.curtain.presentation.ui.color.ColorManagementScreen
import info.proteo.curtain.presentation.ui.curtain.CurtainDetailsScreen
import info.proteo.curtain.presentation.ui.doi.DOILoaderScreen
import info.proteo.curtain.presentation.ui.main.MainScreen
import info.proteo.curtain.presentation.ui.qr.QRScannerScreen
import info.proteo.curtain.presentation.ui.settings.BarChartConditionBracketScreen
import info.proteo.curtain.presentation.ui.settings.ColumnSizeScreen
import info.proteo.curtain.presentation.ui.settings.ExtraDataStorageScreen
import info.proteo.curtain.presentation.ui.settings.GlobalYAxisLimitsScreen
import info.proteo.curtain.presentation.ui.settings.MarkerSizeMapScreen
import info.proteo.curtain.presentation.ui.settings.SettingsInfoScreen
import info.proteo.curtain.presentation.ui.settings.SettingsVariantScreen
import info.proteo.curtain.presentation.ui.settings.ViolinPointPositionScreen
import info.proteo.curtain.presentation.ui.settings.VolcanoConditionLabelsScreen
import info.proteo.curtain.presentation.ui.settings.VolcanoTextColumnScreen
import info.proteo.curtain.presentation.ui.settings.VolcanoYAxisPositionScreen
import info.proteo.curtain.presentation.viewmodel.ColorManagementViewModel
import info.proteo.curtain.domain.service.CurtainDataService
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CurtainNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val scope = rememberCoroutineScope()
    val deepLinkHandler: DeepLinkHandler = hiltViewModel<DeepLinkViewModel>().deepLinkHandler

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") {
            MainScreen(navController = navController)
        }

        composable(
            route = "curtain_details/{linkId}",
            arguments = listOf(navArgument("linkId") { type = NavType.StringType })
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val viewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                androidx.hilt.navigation.compose.hiltViewModel(backStackEntry)
            CurtainDetailsScreen(
                linkId = linkId,
                navController = navController,
                viewModel = viewModel
            )
        }

        composable("qr_scanner") {
            QRScannerScreen(
                navController = navController,
                deepLinkHandler = deepLinkHandler,
                onQRCodeScanned = { qrContent ->
                    scope.launch {
                        val result = deepLinkHandler.handleQRCode(qrContent)
                        when (result) {
                            is DeepLinkResult.CurtainDataset -> {
                                navController.navigate("curtain_details/${result.linkId}") {
                                    popUpTo("main") { inclusive = false }
                                }
                            }
                            is DeepLinkResult.DOIReference -> {
                                val route = if (result.sessionId != null) {
                                    "doi_loader/${result.doi}/${result.sessionId}"
                                } else {
                                    "doi_loader/${result.doi}"
                                }
                                navController.navigate(route) {
                                    popUpTo("main") { inclusive = false }
                                }
                            }
                            else -> {

                            }
                        }
                    }
                }
            )
        }

        composable(
            route = "doi_loader/{doi}?sessionId={sessionId}",
            arguments = listOf(
                navArgument("doi") { type = NavType.StringType },
                navArgument("sessionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val doi = backStackEntry.arguments?.getString("doi") ?: ""
            val sessionId = backStackEntry.arguments?.getString("sessionId")
            val context = androidx.compose.ui.platform.LocalContext.current
            val curtainViewModel: info.proteo.curtain.presentation.viewmodel.CurtainViewModel = hiltViewModel()

            DOILoaderScreen(
                doi = doi,
                sessionId = sessionId,
                onSessionLoaded = { sessionData ->
                    scope.launch {
                        try {
                            val curtainDataDir = File(context.filesDir, "CurtainData")
                            curtainDataDir.mkdirs()

                            val result = withContext(Dispatchers.IO) {
                                curtainViewModel.saveDOISession(doi, sessionData, curtainDataDir)
                            }

                            result.onSuccess { curtainEntity ->
                                navController.navigate("curtain_details/${curtainEntity.linkId}") {
                                    popUpTo("main") { inclusive = false }
                                }
                            }.onFailure { e ->
                                android.util.Log.e("DOILoader", "Failed to save session data", e)
                                navController.popBackStack("main", inclusive = false)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("DOILoader", "Failed to save session data", e)
                            navController.popBackStack("main", inclusive = false)
                        }
                    }
                },
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "protein_chart/{linkId}/{proteinId}/{geneName}",
            arguments = listOf(
                navArgument("linkId") { type = NavType.StringType },
                navArgument("proteinId") { type = NavType.StringType },
                navArgument("geneName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val proteinId = backStackEntry.arguments?.getString("proteinId")
            val geneName = backStackEntry.arguments?.getString("geneName")?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            }
            val parentEntry = remember(linkId) {
                navController.previousBackStackEntry
            }
            val curtainDetailsViewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }

            ProteinChartNavigationScreen(
                linkId = linkId,
                proteinId = proteinId,
                geneName = geneName,
                curtainDetailsViewModel = curtainDetailsViewModel,
                onNavigateBack = { navController.navigateUp() }
            )
        }

        composable(
            route = "settings_variants/{linkId}",
            arguments = listOf(navArgument("linkId") { type = NavType.StringType })
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val parentEntry = remember(linkId) {
                navController.previousBackStackEntry
            }
            val viewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }
            SettingsVariantScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable(
            route = "settings_info/{linkId}",
            arguments = listOf(navArgument("linkId") { type = NavType.StringType })
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val parentEntry = remember(linkId) {
                navController.previousBackStackEntry
            }
            val viewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }
            SettingsInfoScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable(
            route = "volcano_condition_labels/{linkId}",
            arguments = listOf(navArgument("linkId") { type = NavType.StringType })
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val parentEntry = remember(linkId) {
                navController.previousBackStackEntry
            }
            val viewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }
            VolcanoConditionLabelsScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable(
            route = "volcano_yaxis_position/{linkId}",
            arguments = listOf(navArgument("linkId") { type = NavType.StringType })
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val parentEntry = remember(linkId) {
                navController.previousBackStackEntry
            }
            val viewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }
            VolcanoYAxisPositionScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable(
            route = "volcano_text_column/{linkId}",
            arguments = listOf(navArgument("linkId") { type = NavType.StringType })
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val parentEntry = remember(linkId) {
                navController.previousBackStackEntry
            }
            val viewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }
            VolcanoTextColumnScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable(
            route = "bar_chart_bracket/{linkId}",
            arguments = listOf(navArgument("linkId") { type = NavType.StringType })
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val parentEntry = remember(linkId) {
                navController.previousBackStackEntry
            }
            val viewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }
            BarChartConditionBracketScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable(
            route = "global_yaxis_limits/{linkId}",
            arguments = listOf(navArgument("linkId") { type = NavType.StringType })
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val parentEntry = remember(linkId) {
                navController.previousBackStackEntry
            }
            val viewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }
            GlobalYAxisLimitsScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable(
            route = "column_size/{linkId}",
            arguments = listOf(navArgument("linkId") { type = NavType.StringType })
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val parentEntry = remember(linkId) {
                navController.previousBackStackEntry
            }
            val viewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }
            ColumnSizeScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable(
            route = "violin_point_position/{linkId}",
            arguments = listOf(navArgument("linkId") { type = NavType.StringType })
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val parentEntry = remember(linkId) {
                navController.previousBackStackEntry
            }
            val viewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }
            ViolinPointPositionScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable(
            route = "marker_size_map/{linkId}",
            arguments = listOf(navArgument("linkId") { type = NavType.StringType })
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val parentEntry = remember(linkId) {
                navController.previousBackStackEntry
            }
            val viewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }
            MarkerSizeMapScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable(
            route = "extra_data_storage/{linkId}",
            arguments = listOf(navArgument("linkId") { type = NavType.StringType })
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val parentEntry = remember(linkId) {
                navController.previousBackStackEntry
            }
            val viewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }
            ExtraDataStorageScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable(
            route = "volcano_color_manager/{linkId}",
            arguments = listOf(navArgument("linkId") { type = NavType.StringType })
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val parentEntry = remember(linkId) {
                navController.previousBackStackEntry
            }
            val viewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }
            info.proteo.curtain.presentation.ui.settings.VolcanoColorManagerScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable(
            route = "sample_order/{linkId}",
            arguments = listOf(navArgument("linkId") { type = NavType.StringType })
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val parentEntry = remember(linkId) {
                navController.previousBackStackEntry
            }
            val viewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }
            info.proteo.curtain.presentation.ui.settings.SampleOrderScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable(
            route = "color_palette/{linkId}",
            arguments = listOf(navArgument("linkId") { type = NavType.StringType })
        ) { backStackEntry ->
            val linkId = backStackEntry.arguments?.getString("linkId") ?: ""
            val parentEntry = remember(linkId) {
                navController.previousBackStackEntry
            }
            val viewModel: info.proteo.curtain.presentation.viewmodel.CurtainDetailsViewModel =
                if (parentEntry != null) {
                    hiltViewModel(parentEntry)
                } else {
                    hiltViewModel()
                }
            info.proteo.curtain.presentation.ui.settings.ColorPaletteScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class DeepLinkViewModel @javax.inject.Inject constructor(
    val deepLinkHandler: DeepLinkHandler
) : androidx.lifecycle.ViewModel()
