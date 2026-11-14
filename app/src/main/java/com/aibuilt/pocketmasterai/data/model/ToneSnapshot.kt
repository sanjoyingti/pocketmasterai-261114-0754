package com.aibuilt.pocketmasterai.data.model

import com.aibuilt.pocketmasterai.data.hardware.PocketMasterProtocol

data class ToneSnapshot(
    val chain: IntArray,
    val bypass: ByteArray,
    val models: ShortArray,
    val parameters: Map<Int, Map<Int, Short>>,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromDeviceState(
            chain: IntArray,
            bypass: ByteArray,
            models: ShortArray,
            name: String,
            parameters: Map<Int, Map<Int, Short>>
        ): ToneSnapshot = ToneSnapshot(chain, bypass, models, parameters, name)

        fun emptyFor(blockCount: Int = PocketMasterProtocol.NUM_BLOCKS): ToneSnapshot {
            return ToneSnapshot(
                chain = IntArray(blockCount) { it },
                bypass = ByteArray(blockCount) { 0 },
                models = ShortArray(blockCount) { 0 },
                parameters = emptyMap(),
                name = "UNNAMED"
            )
        }
    }

    override fun toString(): String = buildString {
        append("ToneSnapshot[name=$name, chain=")
        append(chain.joinToString("-"))
        append(", bypass=")
        append(bypass.joinToString("-"))
        append(", models=")
        append(models.joinToString("-"))
        append(", params=${parameters.size} blocks")
        append(']')
    }
}
