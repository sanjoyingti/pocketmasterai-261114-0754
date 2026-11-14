package com.aibuilt.pocketmasterai.data.hardware

data class PocketMasterDeviceState(
    val chain: IntArray = IntArray(9) { i -> i },
    val bypass: ByteArray = ByteArray(9) { 0 },
    val models: ShortArray = ShortArray(9) { 0 },
    val name: String = "USER001",
    val globalLevels: IntArray = intArrayOf(0, 0, 0),
    val parameters: Map<Int, Map<Int, Short>> = emptyMap()
)
