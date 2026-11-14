package com.aibuilt.pocketmasterai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterDevice
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterProtocol
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PocketMasterViewModel(private val container: AppContainer) : ViewModel() {

    private val device: PocketMasterDevice = container.deviceState

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _bypass = MutableStateFlow(device.bypass.value)
    val bypass: StateFlow<ByteArray> = _bypass.asStateFlow()

    private val _models = MutableStateFlow(device.models.value)
    val models: StateFlow<ShortArray> = _models.asStateFlow()

    private val _name = MutableStateFlow(device.name.value)
    val name: StateFlow<String> = _name.asStateFlow()

    init {
        observeDevice()
    }

    private fun observeDevice() {
        viewModelScope.launch {
            device.isConnected.collect { connected ->
                _isConnected.value = connected
                _connectionStatus.value = if (connected) "Connected" else "Disconnected"
            }
        }
        viewModelScope.launch {
            device.bypass.collect { _bypass.value = it }
        }
        viewModelScope.launch {
            device.models.collect { _models.value = it }
        }
        viewModelScope.launch {
            device.name.collect { _name.value = it }
        }
        dumpState()
    }

    private var dumpJob: kotlinx.coroutines.Job? = null

    private fun dumpState() {
        dumpJob?.cancel()
        dumpJob = viewModelScope.launch {
            while (true) {
                delay(2000L)
                if (_isConnected.value) {
                    try {
                        device.sendBytes(PocketMasterProtocol.editBufferDumpRequest())
                    } catch (e: Exception) {
                        _connectionStatus.value = "Send failed: ${e.message}"
                        _isConnected.value = false
                    }
                }
            }
        }
    }

    fun requestDevice() {
        viewModelScope.launch {
            val success = device.connect()
            _connectionStatus.value = if (success) "Connected" else "Scanning for device..."
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            device.disconnect()
        }
    }

    fun toggleBypass(block: Int) {
        viewModelScope.launch {
            val current = device.bypass.value[block].toInt()
            val newBypass = (1 - current).toByte()
            device.sendBytes(PocketMasterProtocol.setBlockState(block, newBypass == 1.toByte()))
        }
    }

    fun setModel(block: Int, modelId: Int) {
        viewModelScope.launch {
            device.sendBytes(PocketMasterProtocol.setBlockModel(block, modelId))
        }
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
            _connectionStatus.value = "Agent processing: $text"
        }
    }

    fun undoTone() {
        viewModelScope.launch {
            _connectionStatus.value = "Reverting tone to last snapshot..."
        }
    }

    fun applyToneToChain() {
        viewModelScope.launch {
            _connectionStatus.value = "Applying tone to chain..."
        }
    }

    fun savePresetSlot(slot: Int) {
        viewModelScope.launch {
            _connectionStatus.value = "Saving preset to P${slot.toString().padStart(2, '0')}..."
        }
    }

    fun loadPreset(slot: Int) {
        viewModelScope.launch {
            _connectionStatus.value = "Loading P${slot.toString().padStart(2, '0')}..."
        }
    }

    fun saveCurrentPreset() {
        viewModelScope.launch {
            _connectionStatus.value = "Saving preset..."
        }
    }

    companion object {
        class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PocketMasterViewModel(container) as T
            }
        }
    }
}

=== FILE: app/src/main/java/com/aibuilt/pocketmasterai/ui/agent/AgentScreen.kt ===
package com.aibuilt.pocketmasterai.ui.agent

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aibuilt.pocketmasterai.data.AppContainer
import com.aibuilt.pocketmasterai.data.PocketMasterViewModel
import kotlinx.coroutines.launch

@Composable
fun AgentScreen(
    container: AppContainer,
    viewModel: PocketMasterViewModel
) {
    val isConnected by viewModel.isConnected.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Text("AI Tone Agent", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            if (!isConnected) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text("Connect hardware first", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text("Ask for a tone, e.g. \"Find the tone for the Comfortably Numb solo\". The agent will research, map to the device, and apply it.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Conversation", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            MessageCard(role = "system", content = "Connected to Sonicake Pocket Master simulator")
            Spacer(modifier = Modifier.height(8.dp))

            MessageCard(role = "user", content = "Set all settings to match Sultans of Swing")
            Spacer(modifier = Modifier.height(8.dp))

            ToolStatusPill(text = "Searching rig specs for 'Sultans of Swing'...")
            ToolStatusPill(text = "Mapping pedals to Sonicake Pocket Master blocks")
            ToolStatusPill(text = "Applying chain: NR -> COMP -> DRV -> AMP -> IR -> EQ -> MOD -> DLY -> RVB")
            Spacer(modifier = Modifier.height(16.dp))

            Text("Undo / Revert Tone", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            RevertToneCard(onUndo = { viewModel.undoTone() })
            Spacer(modifier = Modifier.height(16.dp))

            TonePreviewCard(title = "Sultans of Swing - Hard Rock Amp Mode")
            Spacer(modifier = Modifier.height(16.dp))

            MessageInput(
                onSend = { text ->
                    scope.launch {
                        viewModel.sendMessage(text)
                    }
                }
            )
        }
    }
}

@Composable
fun MessageCard(role: String, content: String) {
    val isUser = role == "user"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(if (isUser) "You" else "Agent", style = MaterialTheme.typography.labelSmall, color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ToolStatusPill(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun RevertToneCard(onUndo: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Undo, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Undo / Revert Tone", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
fun TonePreviewCard(title: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { }) { Text("Apply to Pedal") }
                Button(onClick = { }) { Text("Save to P01") }
            }
        }
    }
}

@Composable
fun MessageInput(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Describe the tone you want...") },
            singleLine = true
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSend(text)
                    text = ""
                }
            },
            colors = OutlinedButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(imageVector = Icons.Default.Send, contentDescription = null)
        }
    }
}

=== FILE: app/src/main/java/com/aibuilt/pocketmasterai/ui/chain/ChainScreen.kt ===
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aibuilt.pocketmasterai.data.AppContainer
import com.aibuilt.pocketmasterai.data.PocketMasterViewModel
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterProtocol

@Composable
fun ChainScreen(
    container: AppContainer,
    viewModel: PocketMasterViewModel
) {
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
            Text(text = "Connecting...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
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
            Text("MIDI: Connected", style = MaterialTheme.typography.bodySmall)
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
                checked = bypass.any { it.toInt() == 1 },
                onCheckedChange = { allBypass ->
                    for (i in 0..8) {
                        viewModel.toggleBypass(i)
                    }
                }
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
                bypassed = bypass[block].toInt() == 1,
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
                    text = PocketMasterProtocol.getBlockLabel(block),
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
    icon: ImageVector,
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
