package com.aibuilt.pocketmasterai.data.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Offline simulator of the Sonicake Pocket Master (QME-10), ported from tests/fake_device.py.
 * Handles command queue, responds to edit-buffer dump requests, applies commands, and can
 * randomly drop packets to exercise retry logic.
 */
class FakePocketMasterDevice(
    private val dropPacketChance: Float = 0.0f
) : PocketMasterDevice {

    override val isConnected: StateFlow<Boolean> = MutableStateFlow(true)

    private val _chain = MutableStateFlow(IntArray(PocketMasterProtocol.NUM_BLOCKS) { i -> i })
    private val _bypass = MutableStateFlow(ByteArray(PocketMasterProtocol.NUM_BLOCKS) { 0 })
    private val _models = MutableStateFlow(ShortArray(PocketMasterProtocol.NUM_BLOCKS) { 0 })
    private val _params = MutableStateFlow(HashMap<Int, HashMap<Int, Short>>(PocketMasterProtocol.NUM_BLOCKS))
    private val _name = MutableStateFlow("USER001")
    private val _globalLevels = MutableStateFlow(intArrayOf(0, 0, 0))
    private val _dropPacketChance = dropPacketChance

    override val chain: StateFlow<IntArray> = _chain.asStateFlow()
    override val bypass: StateFlow<ByteArray> = _bypass.asStateFlow()
    override val models: StateFlow<shortArray> = _models.asStateFlow()
    override val params: StateFlow<HashMap<Int, HashMap<Int, Short>>> = _params.asStateFlow()
    override val name: StateFlow<String> = _name.asStateFlow()
    override val globalLevels: StateFlow<intArrayOf> = _globalLevels.asStateFlow()

    private val _lastReceivedCommands = MutableStateFlow<List<ByteArray>>(emptyList())
    override val lastReceivedCommands: StateFlow<List<ByteArray>> = _lastReceivedCommands.asStateFlow()

    private var dumpResponded = true

    override suspend fun connect(): Boolean {
        _isConnected.value = true
        return true
    }

    override suspend fun disconnect() {
        _isConnected.value = false
    }

    override suspend fun sendBytes(data: ByteArray): Int {
        var sent = data.size
        if (_dropPacketChance > 0 && Math.random() < _dropPacketChance.toDouble()) {
            sent = 0
        }
        return sent
    }

    override suspend fun receiveBytes(buffer: ByteArray): Int {
        delay(5)
        if (_isConnected.value && dumpResponded) {
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
            payload[0] = PocketMasterProtocol.OP_EDIT_BUFFER_DUMP.toByte()
            out.copyInto(payload, 1)
            buffer[0] = 0xF0
            buffer[1] = PocketMasterProtocol.SYSEX_ID.toByte()
            buffer[2] = PocketMasterProtocol.DEVICE_ID.toByte()
            payload.copyInto(buffer, 3)
            buffer[3 + payload.size] = 0xF7
            return 3 + payload.size + 1
        }
        return 0
    }

    override suspend fun processCommand(cmd: ByteArray): Boolean {
        _lastReceivedCommands.update { it + cmd }
        if (_lastReceivedCommands.value.size > 50) {
            _lastReceivedCommands.value = _lastReceivedCommands.value.drop(25)
        }
        if (cmd.size < 4) return false
        val op = cmd[3].toInt() and 0xFF
        when (op) {
            PocketMasterProtocol.OP_EDIT_BUFFER_DUMP -> dumpResponded = true
            PocketMasterProtocol.OP_EDIT_BUFFER_NAME -> {
                val n = String(cmd.copyOfRange(4, 10)).replace(Regex("[\\u0000-\\u001F\\u007F-\\u009F]"), "")
                _name.value = n
            }
            PocketMasterProtocol.OP_SET_BLOCK_STATE -> {
                val block = cmd[4].toInt() and 0xFF
                _bypass.value[block] = cmd[5].toByte()
            }
            PocketMasterProtocol.OP_SET_BLOCK_MODEL -> {
                val block = cmd[4].toInt() and 0xFF
                val mid = ((cmd[5].toInt() and 0xFF) shl 8) or (cmd[6].toInt() and 0xFF)
                _models.value[block] = mid.toShort()
            }
            PocketMasterProtocol.OP_SET_PARAMETER -> {
                val block = cmd[4].toInt() and 0xFF
                val pid = ((cmd[5].toInt() and 0xFF) shl 8) or (cmd[6].toInt() and 0xFF)
                val valHi = cmd[7].toInt() and 0xFF
                val valLo = cmd[8].toInt() and 0xFF
                val value = (valHi shl 8) or valLo
                _params.getOrPut(block) { HashMap() }[pid] = value.toShort()
            }
            PocketMasterProtocol.OP_CHAIN_REORDER -> {
                val newChain = IntArray(PocketMasterProtocol.NUM_BLOCKS)
                cmd.copyOfRange(4, 13).forEachIndexed { i, b -> newChain[i] = b.toInt() and 0xFF }
                _chain.value = newChain
            }
            PocketMasterProtocol.OP_SET_GLOBAL_LEVELS -> {
                _globalLevels.value = intArrayOf(
                    cmd[4].toInt() and 0xFF,
                    cmd[5].toInt() and 0xFF,
                    cmd[6].toInt() and 0xFF
                )
            }
        }
        return true
    }

    fun simulateDisconnect() {
        _isConnected.value = false
        dumpResponded = true
    }

    fun simulateReconnect() {
        _isConnected.value = true
    }
}
