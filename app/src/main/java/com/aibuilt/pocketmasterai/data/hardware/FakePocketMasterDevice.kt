package com.aibuilt.pocketmasterai.data.hardware

import com.aibuilt.pocketmasterai.data.PocketMasterDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePocketMasterDevice : PocketMasterDevice {

    private val _isConnected = MutableStateFlow(false)
    private val _deviceState = MutableStateFlow(PocketMasterDeviceState())
    private var dumpResponded = true

    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    override val chain: StateFlow<IntArray> = _deviceState.map { it.chain }.asStateFlow()
    override val bypass: StateFlow<ByteArray> = _deviceState.map { it.bypass }.asStateFlow()
    override val models: StateFlow<ShortArray> = _deviceState.map { it.models }.asStateFlow()
    override val name: StateFlow<String> = _deviceState.map { it.name }.asStateFlow()
    override val globalLevels: StateFlow<IntArray> = _deviceState.map { it.globalLevels }.asStateFlow()
    override val deviceState: StateFlow<PocketMasterDeviceState> = _deviceState.asStateFlow()

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
        when (op) {
            0x00 -> dumpResponded = true
            0x21 -> {
                val block = data[4].toInt() and 0xFF
                _deviceState.value = _deviceState.value.copy(bypass = _deviceState.value.bypass.apply {
                    if (block in 0..8) this[block] = data[5].toInt().toByte()
                })
            }
            0x22 -> {
                val block = data[4].toInt() and 0xFF
                val model = ((data[5].toInt() and 0xFF) shl 8) or (data[6].toInt() and 0xFF)
                _deviceState.value = _deviceState.value.copy(models = _deviceState.value.models.apply {
                    if (block in 0..8) this[block] = model.toShort()
                })
            }
            0x23 -> {
                val block = data[4].toInt() and 0xFF
                val paramId = ((data[5].toInt() and 0xFF) shl 8) or (data[6].toInt() and 0xFF)
                val value = data[7].toInt().toShort()
                _deviceState.value = _deviceState.value.copy(
                    parameters = _deviceState.value.parameters + (block to (_deviceState.value.parameters[block] ?: emptyMap()).plus(paramId to value))
                )
            }
            0x24 -> {
                val order = IntArray(9) { data[4 + it].toInt() and 0xFF }
                _deviceState.value = _deviceState.value.copy(chain = order)
            }
            0x27 -> {
                _deviceState.value = _deviceState.value.copy(
                    globalLevels = intArrayOf(data[4].toInt(), data[5].toInt(), data[6].toInt())
                )
            }
            0x28 -> dumpResponded = true
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
        buffer[0] = 0xF0.toByte()
        buffer[1] = 0x7E.toByte()
        buffer[2] = 0x53.toByte()
        buffer[3] = 0x00.toByte()
        payload.copyInto(buffer, 4)
        buffer[3 + payload.size] = 0xF7.toByte()
        return 3 + payload.size + 1
    }
}
