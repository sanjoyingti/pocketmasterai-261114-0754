package com.aibuilt.pocketmasterai.data.hardware

import kotlinx.coroutines.flow.StateFlow

interface PocketMasterDevice {
    val isConnected: StateFlow<Boolean>
    val chain: StateFlow<IntArray>
    val bypass: StateFlow<ByteArray>
    val models: StateFlow<ShortArray>
    val params: StateFlow<HashMap<Int, HashMap<Int, Short>>>
    val name: StateFlow<String>
    val globalLevels: StateFlow<IntArray>
    val lastReceivedCommands: StateFlow<List<ByteArray>>

    suspend fun connect(): Boolean
    suspend fun disconnect()
    suspend fun sendBytes(data: ByteArray): Int
    suspend fun receiveBytes(buffer: ByteArray): Int
    suspend fun processCommand(cmd: ByteArray): Boolean
}
