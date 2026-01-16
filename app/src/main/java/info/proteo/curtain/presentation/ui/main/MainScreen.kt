package info.proteo.curtain.presentation.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import info.proteo.curtain.presentation.ui.curtain.CurtainListScreen
import info.proteo.curtain.presentation.ui.filter.DataFilterListScreen
import info.proteo.curtain.presentation.ui.site.SiteSettingsScreen

/**
 * Main screen with bottom navigation bar.
 * Matches iOS ContentView with three tabs:
 * 1. Datasets - Browse and manage curtain datasets
 * 2. Filters - Browse and apply data filter lists
 * 3. Sites - Manage backend server settings
 *
 * @param navController Navigation controller for navigating to detail screens
 */
@Composable
fun MainScreen(navController: NavHostController) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.ListAlt, contentDescription = "Datasets") },
                    label = { Text("Datasets") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.FilterList, contentDescription = "Filters") },
                    label = { Text("Filters") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Cloud, contentDescription = "Sites") },
                    label = { Text("Sites") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> CurtainListScreen(navController = navController)
                1 -> DataFilterListScreen()
                2 -> SiteSettingsScreen()
            }
        }
    }
}

/**
 * Placeholder screen for tabs not yet implemented.
 *
 * @param tabName Name of the tab
 */
@Composable
private fun PlaceholderScreen(tabName: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "$tabName screen - Coming soon")
    }
}
