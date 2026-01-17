package info.proteo.curtain

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import info.proteo.curtain.domain.preferences.ThemePreference
import info.proteo.curtain.domain.service.DeepLinkHandler
import info.proteo.curtain.domain.service.DeepLinkResult
import info.proteo.curtain.presentation.navigation.CurtainNavGraph
import info.proteo.curtain.presentation.ui.theme.CurtainTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var deepLinkHandler: DeepLinkHandler

    private var deepLinkResult by mutableStateOf<DeepLinkResult?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntent(intent)

        setContent {
            val themePreference = remember { ThemePreference(applicationContext) }
            val themeMode by themePreference.themeMode.collectAsState(initial = ThemePreference.THEME_SYSTEM)
            val systemInDarkTheme = isSystemInDarkTheme()

            val darkTheme = when (themeMode) {
                ThemePreference.THEME_LIGHT -> false
                ThemePreference.THEME_DARK -> true
                else -> systemInDarkTheme
            }

            val navController = rememberNavController()

            LaunchedEffect(deepLinkResult) {
                deepLinkResult?.let { result ->
                    when (result) {
                        is DeepLinkResult.CurtainDataset -> {
                            // navController.navigate("curtain_details/${result.linkId}")
                        }
                        is DeepLinkResult.DOIReference -> {

                        }
                        is DeepLinkResult.ParsedQRData -> {

                        }
                    }
                    deepLinkResult = null
                }
            }

            CurtainTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CurtainNavGraph(navController = navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        lifecycleScope.launch {
            val result = deepLinkHandler.handleIntent(intent)
            if (result != null) {
                deepLinkResult = result
            }
        }
    }
}
