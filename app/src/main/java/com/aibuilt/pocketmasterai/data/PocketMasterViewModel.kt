package com.aibuilt.pocketmasterai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aibuilt.pocketmasterai.data.agent.AiMessage
import com.aibuilt.pocketmasterai.data.agent.AiProvider
import com.aibuilt.pocketmasterai.data.agent.AiProviderRepository
import com.aibuilt.pocketmasterai.data.agent.AiResponseEvent
import com.aibuilt.pocketmasterai.data.agent.AgentToolDefinitions
import com.aibuilt.pocketmasterai.data.agent.AgentToolExecutor
import com.aibuilt.pocketmasterai.data.agent.GeminiProvider
import com.aibuilt.pocketmasterai.data.agent.OpenAiCompatProvider
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterDevice
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterProtocol
import com.aibuilt.pocketmasterai.model.ToneSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PocketMasterViewModel(private val container: AppContainer) : ViewModel() {

    private val device: PocketMasterDevice = container.deviceState
    private val toolExecutor = AgentToolExecutor(container.deviceState, container)
    private val aiProviderRepository = container.aiProviderRepository

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

    private val _chatMessages = MutableStateFlow<List<UiChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<UiChatMessage>> = _chatMessages.asStateFlow()

    private val _isAgentBusy = MutableStateFlow(false)
    val isAgentBusy: StateFlow<Boolean> = _isAgentBusy.asStateFlow()

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
            _chatMessages.update { it + UiChatMessage(role = "user", content = text, type = ChatMessageType.Text) }
            processAgentMessage(text)
        }
    }

    private suspend fun processAgentMessage(text: String) {
        _isAgentBusy.value = true
        _connectionStatus.value = "Agent processing..."

        val providerName = aiProviderRepository.getSelectedProvider().first()
        val model = aiProviderRepository.getSelectedModel().first() ?: "gemini-2.0-flash"
        val apiKey = when (providerName) {
            "openai" -> aiProviderRepository.getOpenAiApiKey().first() ?: ""
            else -> aiProviderRepository.getGeminiApiKey().first() ?: ""
        }
        val oauth = aiProviderRepository.getOauthToken().first()

        val provider: AiProvider? = when {
            providerName == "openai" -> {
                val baseUrl = aiProviderRepository.getOpenAiBaseUrl().first() ?: "https://api.deepseek.com/v1"
                if (apiKey.isNotBlank()) OpenAiCompatProvider(apiKey, baseUrl, model) else null
            }
            apiKey.isNotBlank() -> GeminiProvider(apiKey, oauth, model)
            else -> {
                addSystemMessage("No AI provider configured. Open Settings to select Google Gemini or an OpenAI-compatible endpoint (DeepSeek 4.1 Flash).")
                _isAgentBusy.value = false
                return
            }
        }

        provider?.generate(
            messages = listOf(AiMessage("user", text)),
            tools = AgentToolDefinitions.TOOLS,
            onEvent = { event ->
                when (event) {
                    is AiResponseEvent.TextChunk -> addAgentMessage(event.text)
                    is AiResponseEvent.ToolCall -> handleToolCall(event.toolName, event.args)
                    is AiResponseEvent.ToolResult -> addAgentMessage("Tool result: ${event.result}")
                    is AiResponseEvent.Done -> _isAgentBusy.value = false
                    is AiResponseEvent.Error -> {
                        addAgentMessage("Error: ${event.message}")
                        _isAgentBusy.value = false
                    }
                }
            }
        )
    }

    private fun addAgentMessage(text: String) {
        viewModelScope.launch {
            _chatMessages.update { it + UiChatMessage(role = "agent", content = text, type = ChatMessageType.Text) }
        }
    }

    private fun addSystemMessage(text: String) {
        viewModelScope.launch {
            _chatMessages.update { it + UiChatMessage(role = "system", content = text, type = ChatMessageType.Text) }
        }
    }

    private suspend fun handleToolCall(toolName: String, args: Map<String, Any>) {
        val result = toolExecutor.execute(toolName, args)
        _isAgentBusy.value = false
        addAgentMessage("Tool $toolName executed: ${result.message()}")
    }

    private fun AgentToolExecutor.Result.message(): String {
        return when (this) {
            is AgentToolExecutor.Result.Success -> "Success: $message"
            is AgentToolExecutor.Result.Info -> "Info: $message"
            is AgentToolExecutor.Result.Error -> "Error: $message"
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

    fun captureToneSnapshot(snapshot: ToneSnapshot) {
        viewModelScope.launch {
            container.chatHistoryRepository.saveSnapshot(snapshot.toString())
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

data class UiChatMessage(
    val role: String,
    val content: String,
    val type: ChatMessageType
)

enum class ChatMessageType { Text, ToolCall, ToolResult }
