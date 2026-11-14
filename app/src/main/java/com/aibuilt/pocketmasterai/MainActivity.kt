package com.aibuilt.pocketmasterai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.media.midi.MidiDevice
import android.media.midi.MidiManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aibuilt.pocketmasterai.ui.chain.ChainScreen
import com.aibuilt.pocketmasterai.ui.presets.PresetsScreen
import com.aibuilt.pocketmasterai.ui.settings.SettingsScreen
import com.aibuilt.pocketmasterai.ui.agent.AgentScreen

class MainActivity : ComponentActivity() {

    private val usbManager by lazy {
        getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private val midiManager by lazy {
        getSystemService(Context.MIDI_SERVICE) as MidiManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = remember { AppContainer(applicationContext) }
            val viewModel = ViewModelProvider(
                this,
                PocketMasterViewModel.Factory(container)
            )[PocketMasterViewModel::class]
            PocketMasterApp(container = container, viewModel = viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        val viewModel = ViewModelProvider(
            this,
            PocketMasterViewModel.Factory(AppContainer(applicationContext))
        )[PocketMasterViewModel::class]
        viewModel.requestDevice()
    }
}

@Composable
fun PocketMasterApp(container: AppContainer, viewModel: PocketMasterViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(Unit) {
        container.loadDeviceMap(assetManager)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
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
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
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
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Chain : Screen("chain", "Pedal Chain", Icons.Default.Dock)
    object Presets : Screen("presets", "Presets", Icons.Default.Casino)
    object Agent : Screen("agent", "AI Agent", Icons.Default.Chat)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    companion object {
        fun fromRoute(route: String): Screen? = entries.find { it.route == route }
    }
}
