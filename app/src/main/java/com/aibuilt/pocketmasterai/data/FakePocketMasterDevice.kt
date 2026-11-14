package com.aibuilt.pocketmasterai.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePocketMasterDevice : PocketMasterDevice {

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _chain = MutableStateFlow(IntArray(NUM_BLOCKS) { it })
    override val chain: StateFlow<IntArray> = _chain.asStateFlow()

    private val _bypass = MutableStateFlow(ByteArray(NUM_BLOCKS) { 0 })
    override val bypass: StateFlow<ByteArray> = _bypass.asStateFlow()

    private val _models = MutableStateFlow(ShortArray(NUM_BLOCKS) { 0 })
    override val models: StateFlow<ShortArray> = _models.asStateFlow()

    private val _name = MutableStateFlow("USER001")
    override val name: StateFlow<String> = _name.asStateFlow()

    private val _globalLevels = MutableStateFlow(intArrayOf(0, 0, 0))
    override val globalLevels: StateFlow<IntArray> = _globalLevels.asStateFlow()

    private var dumpResponded = true

    override suspend fun connect(): Boolean {
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
        val nameBytes = _name.value.toByteArray()
        val out = ByteArray(14 + 6)
        for (i in 0 until 7) {
            out[i] = (((i * 17) + i) ushr 4).toByte()
            out[i + 7] = ((i * 17) + i and 0x0F).toByte()
        }
        for (i in 0 until 6) {
            out[14 + i] = if (i < nameBytes.size) nameBytes[i] else 0x20.toByte()
        }
        val payload = ByteArray(out.size + 4)
        payload[0] = 0x00
        out.copyInto(payload, 1)
        buffer[0] = 0xF0
        buffer[1] = 0x7E
        buffer[2] = 0x53
        payload.copyInto(buffer, 3)
        buffer[3 + payload.size] = 0xF7
        return 3 + payload.size + 1
    }
}
