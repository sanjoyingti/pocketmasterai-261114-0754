package com.aibuilt.pocketmasterai.ui.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.aibuilt.pocketmasterai.data.AppContainer
import com.aibuilt.pocketmasterai.ui.chain.ChainScreen
import com.aibuilt.pocketmasterai.ui.presets.PresetsScreen
import com.aibuilt.pocketmasterai.ui.settings.SettingsScreen
import com.aibuilt.pocketmasterai.ui.agent.AgentScreen
import com.google.android.material.icons.Icons
import com.google.android.material.icons.filled.Casino
import com.google.android.material.icons.filled.Chat
import com.google.android.material.icons.filled.Dock
import com.google.android.material.icons.filled.Settings

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
fun PocketMasterApp(container: AppContainer) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Chain.route
            ) {
                composable(Screen.Chain.route) {
                    ChainScreen(container = container)
                }
                composable(Screen.Presets.route) {
                    PresetsScreen(container = container)
                }
                composable(Screen.Agent.route) {
                    AgentScreen(container = container)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(container = container)
                }
            }
        }

        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
