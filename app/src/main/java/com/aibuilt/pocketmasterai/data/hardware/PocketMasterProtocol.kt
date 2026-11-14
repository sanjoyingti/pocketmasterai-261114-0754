package com.aibuilt.pocketmasterai.data.hardware

object PocketMasterProtocol {

    const val SYSEX_ID = 0x7E
    const val SYSEX_START = 0xF0
    const val SYSEX_END = 0xF7
    const val DEVICE_ID = 0x53

    const val OP_EDIT_BUFFER_DUMP = 0x00
    const val OP_SET_BLOCK_STATE = 0x21
    const val OP_SET_BLOCK_MODEL = 0x22
    const val OP_SET_PARAMETER = 0x23
    const val OP_CHAIN_REORDER = 0x24
    const val OP_SET_GLOBAL_LEVELS = 0x27
    const val OP_EDIT_BUFFER_SAVE = 0x28

    data class EditBufferDump(
        val isValid: Boolean,
        val name: String,
        val block0FirstBytes: ByteArray
    )

    fun buildCommand(op: Byte, payload: ByteArray): ByteArray {
        val cmd = ByteArray(payload.size + 1)
        cmd[0] = op
        payload.copyInto(cmd, 1)
        return buildSysex(cmd)
    }

    private fun buildSysex(payload: ByteArray): ByteArray {
        val frame = ByteArray(payload.size + 3)
        frame[0] = SYSEX_START
        frame[1] = SYSEX_ID.toByte()
        frame[2] = DEVICE_ID.toByte()
        payload.copyInto(frame, 3)
        frame[frame.size - 1] = SYSEX_END
        return frame
    }

    fun editBufferDumpRequest(): ByteArray = buildCommand(OP_EDIT_BUFFER_DUMP.toByte(), ByteArray(0))

    fun setBlockState(block: Int, bypassed: Boolean): ByteArray {
        val p = ByteArray(2)
        p[0] = block.toByte()
        p[1] = if (bypassed) 0x01 else 0x00
        return buildCommand(OP_SET_BLOCK_STATE.toByte(), p)
    }

    fun setBlockModel(block: Int, modelId: Int): ByteArray {
        val p = ByteArray(3)
        p[0] = block.toByte()
        p[1] = ((modelId ushr 8) and 0xFF).toByte()
        p[2] = (modelId and 0xFF).toByte()
        return buildCommand(OP_SET_BLOCK_MODEL.toByte(), p)
    }

    fun setParameter(block: Int, paramId: Int, value: Short): ByteArray {
        val p = ByteArray(4)
        p[0] = block.toByte()
        p[1] = ((paramId ushr 8) and 0xFF).toByte()
        p[2] = (paramId and 0xFF).toByte()
        p[3] = (value and 0xFF).toByte()
        return buildCommand(OP_SET_PARAMETER.toByte(), p)
    }

    fun chainReorder(order: IntArray): ByteArray {
        require(order.size == NUM_BLOCKS) { "Chain must have 9 blocks" }
        val p = ByteArray(NUM_BLOCKS)
        order.forEachIndexed { i, b -> p[i] = b.toByte() }
        return buildCommand(OP_CHAIN_REORDER.toByte(), p)
    }

    fun setGlobalLevels(usb: Int, main: Int, headphone: Int): ByteArray {
        val p = ByteArray(3)
        p[0] = usb.toByte()
        p[1] = main.toByte()
        p[2] = headphone.toByte()
        return buildCommand(OP_SET_GLOBAL_LEVELS.toByte(), p)
    }

    fun decodeEditBufferDump(frame: ByteArray): EditBufferDump? {
        // Expect: F0 7E 53 00 <14 nibbles> <6 name bytes> F7
        if (frame.size < 24 || frame[0] != SYSEX_START.toByte()
            || frame[1] != SYSEX_ID.toByte() || frame[2] != DEVICE_ID.toByte()
            || frame[3] != OP_EDIT_BUFFER_DUMP.toByte()) {
            return null
        }
        val payload = frame.copyOfRange(4, frame.size - 1)
        if (payload.size < 20) return null
        val nameBytes = payload.copyOfRange(14, 20)
        val name = nameBytes.joinToString("") {
            val c = it.toInt() and 0x7F
            if (c == 0x20.toByte().toInt()) "" else c.toChar().toString()
        }
        return EditBufferDump(
            isValid = true,
            name = name.ifEmpty { "USER001" },
            block0FirstBytes = payload.copyOfRange(0, 14)
        )
    }

    fun getBlockLabel(block: Int): String {
        return when (block) {
            0 -> "NR"
            1 -> "FX1"
            2 -> "DRV"
            3 -> "AMP"
            4 -> "IR"
            5 -> "EQ"
            6 -> "FX2"
            7 -> "DLY"
            8 -> "RVB"
            else -> "?"
        }
    }

    const val NUM_BLOCKS = 9
}
