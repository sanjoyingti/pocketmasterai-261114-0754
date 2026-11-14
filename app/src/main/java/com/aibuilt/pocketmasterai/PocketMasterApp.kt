package com.aibuilt.pocketmasterai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aibuilt.pocketmasterai.ui.chain.ChainScreen
import com.aibuilt.pocketmasterai.ui.presets.PresetsScreen
import com.aibuilt.pocketmasterai.ui.settings.SettingsScreen
import com.aibuilt.pocketmasterai.ui.agent.AgentScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Chain : Screen("chain", "Pedal Chain", Icons.Default.Dock)
    object Presets : Screen("presets", "Presets", Icons.Default.Casino)
    object Agent : Screen("agent", "AI Agent", Icons.Default.Chat)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    companion object {
        fun fromRoute(route: String): Screen? = entries.find { it.route == route }
    }
}

@Composable
fun PocketMasterApp(container: AppContainer, viewModel: PocketMasterViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Chain.route
            ) {
                composable(Screen.Chain.route) {
                    ChainScreen(container = container, viewModel = viewModel)
                }
                composable(Screen.Presets.route) {
                    PresetsScreen(container = container, viewModel = viewModel)
                }
                composable(Screen.Agent.route) {
                    AgentScreen(container = container, viewModel = viewModel)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(container = container, viewModel = viewModel)
                }
            }
        }

        NavigationBar(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = NavigationBarItemDefaults.tonalElevation
        ) {
            Screen.entries.forEach { screen ->
                val selected = currentRoute == screen.route
                NavigationBarItem(
                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                    label = { Text(screen.title) },
                    selected = selected,
                    onClick = { navController.navigate(screen.route) {
                        popUpTo(Screen.Chain.route) { inclusive = false }
                    } },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
