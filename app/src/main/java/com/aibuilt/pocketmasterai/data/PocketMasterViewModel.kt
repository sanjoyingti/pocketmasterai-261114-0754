package com.aibuilt.pocketmasterai.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PocketMasterViewModel(private val container: PocketMasterContainer) : ViewModel() {

    private val device = container.device

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _bypass = MutableStateFlow(device.bypass)
    val bypass: StateFlow<ByteArray> = _bypass.asStateFlow()

    private val _models = MutableStateFlow(device.models)
    val models: StateFlow<ShortArray> = _models.asStateFlow()

    private val _name = MutableStateFlow(device.name)
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
        viewModelScope.launch {
            device.chain.collect { chain ->
                if (chain.any { it != 0 }) {
                    // first dump received; device is reachable
                }
            }
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
                        device.sendBytes(com.aibuilt.pocketmasterai.data.hardware.PocketMasterProtocol.editBufferDumpRequest())
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
            device.sendBytes(com.aibuilt.pocketmasterai.data.hardware.PocketMasterProtocol.setBlockState(block, newBypass == 1))
        }
    }

    fun setModel(block: Int, modelId: Int) {
        viewModelScope.launch {
            device.sendBytes(com.aibuilt.pocketmasterai.data.hardware.PocketMasterProtocol.setBlockModel(block, modelId))
        }
    }

    companion object {
        class Factory(private val container: PocketMasterContainer) : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PocketMasterViewModel(container) as T
            }
        }
    }
}
