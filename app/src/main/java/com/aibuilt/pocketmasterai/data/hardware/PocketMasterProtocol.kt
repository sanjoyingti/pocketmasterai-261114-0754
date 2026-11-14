package com.aibuilt.pocketmasterai.data.hardware

object PocketMasterProtocol {

    const val SYSEX_ID = 0x7E
    const val SYSEX_START = 0xF0
    const val SYSEX_END = 0xF7
    const val DEVICE_ID = 0x53

    const val OP_EDIT_BUFFER_DUMP = 0x00
    const val OP_SET_BLOCK_STATE = 0x21
    const val OP_SET_BLOCK_MODEL = 0x22
    const val OP_CHAIN_REORDER = 0x24
    const val OP_SET_GLOBAL_LEVELS = 0x27

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
}
