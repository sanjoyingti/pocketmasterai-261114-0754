package com.aibuilt.pocketmasterai.model

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

    fun toByteArray(): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        stream.write(name.toByteArray())
        stream.write(0)
        stream.write(chain.size)
        chain.forEach { stream.write(it.toInt() and 0xFF) }
        stream.write(bypass.size)
        bypass.forEach { stream.write(it.toInt() and 0xFF) }
        stream.write(models.size)
        models.forEach { stream.write(it.toInt() and 0xFF) }
        stream.write(parameters.size)
        parameters.forEach { (block, params) ->
            stream.write(block)
            stream.write(params.size)
            params.forEach { (param, value) ->
                stream.write(param)
                stream.write(value.toInt() and 0xFF)
            }
        }
        return stream.toByteArray()
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
