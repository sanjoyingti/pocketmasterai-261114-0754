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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

            TonePreviewCard(
                title = "Sultans of Swing - Hard Rock Amp Mode",
                onApply = { viewModel.applyToneToChain() },
                onSave = { viewModel.savePresetSlot(1) }
            )
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
fun TonePreviewCard(title: String, onApply: () -> Unit, onSave: () -> Unit) {
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
                Button(onClick = onApply) { Text("Apply to Pedal") }
                Button(onClick = onSave) { Text("Save to P01") }
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
