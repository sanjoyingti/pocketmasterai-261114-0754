package com.aibuilt.pocketmasterai.data

import kotlinx.coroutines.flow.StateFlow

interface PocketMasterDevice {
    companion object {
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
    }

    val isConnected: StateFlow<Boolean>
    val chain: StateFlow<IntArray>
    val bypass: StateFlow<ByteArray>
    val models: StateFlow<ShortArray>
    val name: StateFlow<String>
    val globalLevels: StateFlow<IntArray>

    suspend fun connect(): Boolean
    suspend fun disconnect()
    suspend fun sendBytes(data: ByteArray): Int
    suspend fun receiveBytes(buffer: ByteArray): Int
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
