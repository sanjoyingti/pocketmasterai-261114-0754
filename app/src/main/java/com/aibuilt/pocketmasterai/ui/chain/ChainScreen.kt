package com.aibuilt.pocketmasterai.ui.chain

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aibuilt.pocketmasterai.data.AppContainer
import com.aibuilt.pocketmasterai.data.PocketMasterViewModel

@Composable
fun ChainScreen(
    container: AppContainer,
    viewModel: PocketMasterViewModel,
) {
    val state by viewModel.connectionStatus.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val bypass by viewModel.bypass.collectAsState()
    val models by viewModel.models.collectAsState()
    val name by viewModel.name.collectAsState()

    if (!isConnected) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text(text = "Connecting to Sonicake Pocket Master...", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.requestDevice() }) {
                Text("Find Hardware")
            }
            Text(text = state, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
        SnackbarHost(hostState = remember { SnackbarHostState() })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Pocket Master - $name", style = MaterialTheme.typography.headlineSmall)
            Text("MIDI Channel: $state", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { viewModel.disconnect() },
                colors = OutlinedButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Disconnect")
            }
            Text(
                text = "All Bypass",
                style = MaterialTheme.typography.labelMedium
            )
            Switch(
                checked = bypass.any { it == 1.toByte() },
                onCheckedChange = {}
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Signal Chain", style = MaterialTheme.typography.titleMedium)

        ChainRow(
            blocks = listOf(0, 1, 2),
            models = models,
            bypass = bypass,
            onToggle = { block -> viewModel.toggleBypass(block) },
            onSetModel = { block, model -> viewModel.setModel(block, model) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        ChainRow(
            blocks = listOf(3, 4, 5),
            models = models,
            bypass = bypass,
            onToggle = { block -> viewModel.toggleBypass(block) },
            onSetModel = { block, model -> viewModel.setModel(block, model) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        ChainRow(
            blocks = listOf(6, 7, 8),
            models = models,
            bypass = bypass,
            onToggle = { block -> viewModel.toggleBypass(block) },
            onSetModel = { block, model -> viewModel.setModel(block, model) }
        )
    }
}

@Composable
fun ChainRow(
    blocks: List<Int>,
    models: ShortArray,
    bypass: ByteArray,
    onToggle: (Int) -> Unit,
    onSetModel: (Int, Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        blocks.forEach { block ->
            PedalBlock(
                block = block,
                model = models[block].toInt(),
                bypassed = bypass[block] == 1.toByte(),
                onToggle = { onToggle(block) },
                onSetModel = { model -> onSetModel(block, model) }
            )
        }
    }
}

@Composable
fun PedalBlock(
    block: Int,
    model: Int,
    bypassed: Boolean,
    onToggle: () -> Unit,
    onSetModel: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .height(110.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (bypassed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getBlockLabel(block),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (bypassed) Color.Gray else MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = !bypassed,
                    onCheckedChange = { onToggle() }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = model.toString(),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(icon = Icons.Default.ChevronLeft, onClick = {
                    onSetModel((model - 1).coerceAtLeast(0))
                })
                IconButton(icon = Icons.Default.ChevronRight, onClick = {
                    onSetModel(model + 1)
                })
            }
        }
    }
}

@Composable
fun IconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
    }
}
