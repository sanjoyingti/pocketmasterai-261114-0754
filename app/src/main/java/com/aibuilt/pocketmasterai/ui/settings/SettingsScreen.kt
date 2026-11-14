package com.aibuilt.pocketmasterai.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aibuilt.pocketmasterai.data.AppContainer
import com.aibuilt.pocketmasterai.data.PocketMasterViewModel

@Composable
fun SettingsScreen(
    container: AppContainer,
    viewModel: PocketMasterViewModel
) {
    val isConnected by viewModel.isConnected.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        ConnectionCard(
            isConnected = isConnected,
            onConnect = { viewModel.requestDevice() },
            onDisconnect = { viewModel.disconnect() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Hardware Simulation", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        SimulationCard(isEnabled = container.useSimulator, onToggle = { container.useSimulator = it })

        Spacer(modifier = Modifier.height(16.dp))

        Text("AI Agent", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        AiProviderCard()

        Spacer(modifier = Modifier.height(16.dp))

        Text("Web Search", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        SearchSourceCard(source = "duckduckgo", onChange = {})
    }
}

@Composable
fun ConnectionCard(isConnected: Boolean, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Connection", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isConnected) "Connected to Sonicake Pocket Master" else "Disconnected")
                if (isConnected) {
                    Button(onClick = onDisconnect) { Text("Disconnect") }
                } else {
                    Button(onClick = onConnect) { Text("Connect") }
                }
            }
        }
    }
}

@Composable
fun SimulationCard(isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Use Fake PocketMaster simulator", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
            Text(
                text = "Enable when hardware isn't available for offline testing",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AiProviderCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("AI Provider", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Google Gemini (OAuth) + OpenAI-compatible endpoint (DeepSeek 4.1 Flash) + Gemini API-key fallback", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SearchSourceCard(source: String, onChange: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Web Search Engine", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            SearchSourceChip(text = "DuckDuckGo", selected = source == "duckduckgo", onClick = { onChange("duckduckgo") })
            Spacer(modifier = Modifier.height(4.dp))
            SearchSourceChip(text = "Google", selected = source == "google", onClick = { onChange("google") })
            Text(
                text = "Google requires an API key; DuckDuckGo works without a key.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SearchSourceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
