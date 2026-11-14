package com.aibuilt.pocketmasterai.ui.presets

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aibuilt.pocketmasterai.data.AppContainer
import com.aibuilt.pocketmasterai.data.PocketMasterViewModel

@Composable
fun PresetsScreen(
    container: AppContainer,
    viewModel: PocketMasterViewModel
) {
    val name by viewModel.name.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Presets - P01 to P50", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Current tone name: $name")
        Spacer(modifier = Modifier.height(8.dp))
        if (isConnected) {
            Button(onClick = { viewModel.saveCurrentPreset() }) {
                Text("Save to Hardware")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items((1..50).map { String.format("P%02d", it) }) { slotName ->
                PresetCard(
                    name = slotName,
                    onClick = { viewModel.loadPreset(slotName.removePrefix("P").toInt()) }
                )
            }
        }
    }
}

@Composable
fun PresetCard(name: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(name, style = MaterialTheme.typography.titleSmall)
        }
    }
}
