package com.aibuilt.pocketmasterai.data.hardware

import com.aibuilt.pocketmasterai.data.PocketMasterDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePocketMasterDevice : PocketMasterDevice {

    private val _isConnected = MutableStateFlow(false)
    private val _deviceState = MutableStateFlow(PocketMasterDeviceState())

    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    override val chain: StateFlow<IntArray> = _deviceState.map { it.chain }.asStateFlow()
    override val bypass: StateFlow<ByteArray> = _deviceState.map { it.bypass }.asStateFlow()
    override val models: StateFlow<ShortArray> = _deviceState.map { it.models }.asStateFlow()
    override val name: StateFlow<String> = _deviceState.map { it.name }.asStateFlow()
    override val globalLevels: StateFlow<IntArray> = _deviceState.map { it.globalLevels }.asStateFlow()
    override val deviceState: StateFlow<PocketMasterDeviceState> = _deviceState.asStateFlow()

    private var dumpResponded = true

    override suspend fun connect(): Boolean {
        _deviceState.value = PocketMasterDeviceState(
            chain = IntArray(9) { it },
            bypass = ByteArray(9) { 0 },
            models = ShortArray(9) { 0 },
            name = "USER001",
            globalLevels = intArrayOf(0, 0, 0)
        )
        _isConnected.value = true
        dumpResponded = true
        return true
    }

    override suspend fun disconnect() {
        _isConnected.value = false
    }

    override suspend fun sendBytes(data: ByteArray): Int {
        if (!_isConnected.value || data.isEmpty()) return 0
        if (data.size < 4) return 0
        val op = data[3].toInt() and 0xFF
        if (op == 0x00) { // OP_EDIT_BUFFER_DUMP
            dumpResponded = true
        }
        return data.size
    }

    override suspend fun receiveBytes(buffer: ByteArray): Int {
        if (!_isConnected.value || !dumpResponded) return 0
        dumpResponded = false
        val nameBytes = _deviceState.value.name.toByteArray()
        val payload = ByteArray(20)
        for (i in 0 until 7) {
            payload[i] = (((i * 17) + i) ushr 4).toByte()
            payload[i + 7] = ((i * 17) + i and 0x0F).toByte()
        }
        for (i in 0 until 6) {
            payload[14 + i] = if (i < nameBytes.size) nameBytes[i] else 0x20.toByte()
        }
        buffer[0] = 0xF0
        buffer[1] = 0x7E
        buffer[2] = 0x53
        buffer[3] = 0x00
        payload.copyInto(buffer, 4)
        buffer[3 + payload.size] = 0xF7
        return 3 + payload.size + 1
    }
}
