package com.aibuilt.pocketmasterai.data.hardware

/**
 * Byte-exact port of pocket-master-mcp protocol.py.
 * SysEx framing, opcodes, 7-bit/nibble packing, CRC-8/SMBus, set/query commands, edit-buffer dump decoder.
 */
object PocketMasterProtocol {

    const val SYSEX_ID = 0x7E

    // System Exclusive (Sysex) message frame: F0 <data> F7
    const val SYSEX_START = 0xF0
    const val SYSEX_END = 0xF7

    // Device ID (all commands target device id 0x53 = 'S' for Sonicake Pocket Master)
    const val DEVICE_ID = 0x53

    // Opcodes (from protocol.py - exact values as in device.py)
    const val OP_EDIT_BUFFER_DUMP = 0x00
    const val OP_EDIT_BUFFER_SAVE = 0x01
    const val OP_EDIT_BUFFER_LOAD = 0x02
    const val OP_EDIT_BUFFER_ERASE = 0x03
    const val OP_EDIT_BUFFER_NAME = 0x04
    const val OP_FACTORY_DUMP = 0x10
    const val OP_FACTORY_SAVE = 0x11
    const val OP_FACTORY_LOAD = 0x12
    const val OP_FACTORY_ERASE = 0x13
    const val OP_FACTORY_NAME = 0x14
    const val OP_FACTORY_RESET = 0x15
    const val OP_SET_PARAMETER = 0x20
    const val OP_SET_BLOCK_STATE = 0x21
    const val OP_SET_BLOCK_MODEL = 0x22
    const val OP_SET_BLOCK_ENABLE = 0x23
    const val OP_CHAIN_REORDER = 0x24
    const val OP_CHAIN_SET_ENABLE = 0x25
    const val OP_GET_DEVICE_STATE = 0x26
    const val OP_SET_GLOBAL_LEVELS = 0x27
    const val OP_SET_PARAMETER_AMP = 0x28
    const val OP_EDIT_BUFFER_DUP = 0x29
    const val OP_SET_CHANNEL = 0x30
    const val OP_SET_MIDI_CHANNEL = 0x31
    const val OP_SET_OUTPUT_MODE = 0x32
    const val OP_SET_TONE_MODE = 0x33
    const val OP_SET_MIDI_THRU = 0x34
    const val OP_SET_USB_MIDI = 0x35
    const val OP_SET_MIDI_IN_CH = 0x36
    const val OP_SET_MIDI_OUT_CH = 0x37
    const val OP_SET_MIDI_OM = 0x38
    const val OP_SET_MIDI_OM_CH = 0x39
    const val OP_SET_DISPLAY_BRIGHT = 0x3A
    const val OP_SET_DISPLAY_MODE = 0x3B
    const val OP_SET_AUTO_OFF = 0x3C
    const val OP_SET_TAP_TEMPO = 0x3D
    const val OP_SET_FOOTSWITCH = 0x3E
    const val OP_SET_FIRMWARE_UPDATE = 0x3F
    const val OP_FACTORY_LOAD_USER = 0x40
    const val OP_FACTORY_LOAD_USER_NAME = 0x41
    const val OP_FACTORY_SAVE_USER = 0x42

    // Block indices in the 9-slot chain
    const val BLOCK_NR = 0
    const val BLOCK_FX1 = 1
    const val BLOCK_DRV = 2
    const val BLOCK_AMP = 3
    const val BLOCK_IR = 4
    const val BLOCK_EQ = 5
    const val BLOCK_FX2 = 6
    const val BLOCK_DLY = 7
    const val BLOCK_RVB = 8
    const val NUM_BLOCKS = 9

    // Fixed chain validation order (DRV -> AMP -> IR -> EQ must stay contiguous in that order)
    fun validateChainOrder(chain: IntArray): Boolean {
        if (chain.size != NUM_BLOCKS) return false
        var drvPos = -1
        var ampPos = -1
        var irPos = -1
        var eqPos = -1
        for (i in chain.indices) {
            when (chain[i]) {
                BLOCK_DRV -> drvPos = i
                BLOCK_AMP -> ampPos = i
                BLOCK_IR -> irPos = i
                BLOCK_EQ -> eqPos = i
            }
        }
        if (drvPos < 0 || ampPos < 0 || irPos < 0 || eqPos < 0) return false
        return (drvPos + 1 == ampPos) && (ampPos + 1 == irPos) && (irPos + 1 == eqPos)
    }

    // Nibble expansion: 7 data bytes -> 14 nibble bytes (from protocol.py encode_nibble)
    fun encodeNibbles(data: ByteArray): ByteArray {
        require(data.size == 7) { "Nibble encoder expects 7 data bytes, got ${data.size}" }
        val out = ByteArray(14)
        for (i in 0 until 7) {
            out[i * 2] = (data[i] ushr 4).toByte()
            out[i * 2 + 1] = (data[i] and 0x0F).toByte()
        }
        return out
    }

    // Nibble decode: 14 nibble bytes -> 7 data bytes
    fun decodeNibbles(data: ByteArray): ByteArray {
        require(data.size == 14) { "Nibble decoder expects 14 nibble bytes, got ${data.size}" }
        val out = ByteArray(7)
        for (i in 0 until 7) {
            out[i] = ((data[i * 2].toInt() and 0x0F) shl 4) or (data[i * 2 + 1].toInt() and 0x0F)
        }
        return out
    }

    // CRC-8/SMBus over given bytes (poly 0x07, init 0x00)
    fun crc8Smbus(data: ByteArray): Byte {
        var crc = 0x00
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF).toByte()
            repeat(8) {
                val msbSet = (crc and 0x80) != 0
                crc = (crc shl 1) and 0xFF
                if (msbSet) crc = (crc xor 0x07).toByte()
            }
        }
        return crc
    }

    // Build a complete SysEx message: F0 SYSEX_ID DEVICE_ID OP ...payload F7
    fun buildSysex(payload: ByteArray): ByteArray {
        val frame = ByteArray(payload.size + 3)
        frame[0] = SYSEX_START
        frame[1] = SYSEX_ID.toByte()
        frame[2] = DEVICE_ID.toByte()
        payload.copyInto(frame, 3)
        frame[frame.size - 1] = SYSEX_END
        return frame
    }

    fun buildCommand(op: Byte, payload: ByteArray): ByteArray {
        val cmd = ByteArray(payload.size + 1)
        cmd[0] = op
        payload.copyInto(cmd, 1)
        return buildSysex(cmd)
    }

    fun editBufferDumpRequest(): ByteArray = buildCommand(OP_EDIT_BUFFER_DUMP, ByteArray(0))

    fun setEditBufferName(name: String): ByteArray {
        val nameBytes = ByteArray(6)
        val codePoints = name.codePoints().take(6).toList()
        for (i in 0 until 6) {
            nameBytes[i] = if (i < codePoints.size) codePoints[i].toInt().toByte() else 0x20.toByte()
        }
        return buildCommand(OP_EDIT_BUFFER_NAME, nameBytes)
    }

    fun setParameter(block: Int, paramId: Int, value: Int): ByteArray {
        val p = ByteArray(5)
        p[0] = block.toByte()
        p[1] = ((paramId ushr 8) and 0xFF).toByte()
        p[2] = (paramId and 0xFF).toByte()
        p[3] = ((value ushr 8) and 0xFF).toByte()
        p[4] = (value and 0xFF).toByte()
        return buildCommand(OP_SET_PARAMETER, p)
    }

    fun setBlockState(block: Int, bypassed: Boolean): ByteArray {
        val p = ByteArray(2)
        p[0] = block.toByte()
        p[1] = if (bypassed) 0x01 else 0x00
        return buildCommand(OP_SET_BLOCK_STATE, p)
    }

    fun setBlockModel(block: Int, modelId: Int): ByteArray {
        val p = ByteArray(3)
        p[0] = block.toByte()
        p[1] = ((modelId ushr 8) and 0xFF).toByte()
        p[2] = (modelId and 0xFF).toByte()
        return buildCommand(OP_SET_BLOCK_MODEL, p)
    }

    fun reorderChain(chain: IntArray): ByteArray {
        require(chain.size == NUM_BLOCKS)
        val p = chain.map { it.toByte() }.toByteArray()
        return buildCommand(OP_CHAIN_REORDER, p)
    }

    fun setGlobalLevels(dryLevel: Int, wetLevel: Int, usbLevel: Int): ByteArray {
        val p = ByteArray(3)
        p[0] = (dryLevel and 0x7F).toByte()
        p[1] = (wetLevel and 0x7F).toByte()
        p[2] = (usbLevel and 0x7F).toByte()
        return buildCommand(OP_SET_GLOBAL_LEVELS, p)
    }

    fun decodeEditBufferDump(frame: ByteArray): EditBufferDump? {
        if (frame.size < 3) return null
        var op = 0xFF
        var dataStart = -1
        for (i in 1 until frame.size) {
            if (frame[i].toInt() and 0xFF == SYSEX_ID) {
                dataStart = i + 1
                continue
            }
            if (frame[i].toInt() and 0xFF == DEVICE_ID) {
                if (dataStart == -1) dataStart = i + 1
                continue
            }
            if (frame[i].toInt() and 0xFF == SYSEX_END && dataStart >= 0) {
                op = frame[dataStart]
                break
            }
        }
        if (op != OP_EDIT_BUFFER_DUMP) return null
        val payload = frame.copyOfRange(dataStart + 1, frame.size - 1)
        if (payload.size < 14) return null
        val data = decodeNibbles(payload.copyOfRange(0, 14))
        val nameBytes = payload.copyOfRange(14, 20)
        val name = String(nameBytes).replace(Regex("[\u0000-\u001F\u007F-\u009F]"), "")
        return EditBufferDump(
            isValid = data.size == 7,
            block0FirstBytes = data.copyOf(),
            name = name
        )
    }
}

data class EditBufferDump(
    val isValid: Boolean,
    val block0FirstBytes: ByteArray,
    val name: String
)
