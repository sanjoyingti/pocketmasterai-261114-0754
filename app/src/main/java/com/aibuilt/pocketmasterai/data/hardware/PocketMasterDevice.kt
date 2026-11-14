package com.aibuilt.pocketmasterai.data.hardware

interface PocketMasterDevice {
    val isConnected: kotlinx.coroutines.flow.StateFlow<Boolean>
    val chain: kotlinx.coroutines.flow.StateFlow<IntArray>
    val bypass: kotlinx.coroutines.flow.StateFlow<ByteArray>
    val models: kotlinx.coroutines.flow.StateFlow<shortArray>
    val params: kotlinx.coroutines.flow.StateFlow<HashMap<Int, HashMap<Int, Short>>>
    val name: kotlinx.coroutines.flow.StateFlow<String>
    val globalLevels: kotlinx.coroutines.flow.StateFlow<intArrayOf>
    val lastReceivedCommands: kotlinx.coroutines.flow.StateFlow<List<ByteArray>>

    suspend fun connect(): Boolean
    suspend fun disconnect()
    suspend fun sendBytes(data: ByteArray): Int
    suspend fun receiveBytes(buffer: ByteArray): Int
    suspend fun processCommand(cmd: ByteArray): Boolean
}
