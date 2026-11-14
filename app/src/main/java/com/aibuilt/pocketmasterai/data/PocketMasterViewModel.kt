package com.aibuilt.pocketmasterai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterProtocol
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PocketMasterViewModel(private val container: AppContainer) : ViewModel() {

    private val device = container.deviceState

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

    private val _chatText = MutableStateFlow("")
    val chatText: StateFlow<String> = _chatText.asStateFlow()

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

    fun setChatText(text: String) {
        _chatText.value = text
    }

    fun sendMessage(text: String) {
        viewModelScope.launch {
            _connectionStatus.value = "Agent processing: $text"
        }
    }

    fun saveCurrentPreset() {
        viewModelScope.launch {
            _connectionStatus.value = "Saving preset..."
        }
    }

    fun loadPreset(slotName: String) {
        viewModelScope.launch {
            _connectionStatus.value = "Loading $slotName..."
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

fun PocketMasterViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    PocketMasterViewModel.Factory(container)
