package com.aibuilt.pocketmasterai.data.model

import com.aibuilt.pocketmasterai.data.PocketMasterDevice
import kotlinx.serialization.Serializable

@Serializable
data class PresetState(
    val chain: IntArray = IntArray(PocketMasterDevice.NUM_BLOCKS) { it },
    val bypass: ByteArray = ByteArray(PocketMasterDevice.NUM_BLOCKS) { 0 },
    val models: ShortArray = ShortArray(PocketMasterDevice.NUM_BLOCKS) { 0 },
    val name: String = "USER001",
    val globalLevels: IntArray = intArrayOf(0, 0, 0),
    val parameters: Map<Int, Map<Int, Short>> = emptyMap()
) {
    companion object {
        fun fromDeviceState(device: PocketMasterDevice): PresetState {
            return PresetState(
                chain = device.chain.value.copyOf(),
                bypass = device.bypass.value.copyOf(),
                models = device.models.value.copyOf(),
                name = device.name.value,
                globalLevels = device.globalLevels.value.copyOf()
            )
        }
    }

    fun toJson(): String = kotlinx.serialization.json.Json.encodeToString(serializer(), this)

    companion object {
        fun serializer() = kotlinx.serialization.json.Json { encodeDefaults = false }
            .let { it -> object : kotlinx.serialization.KSerializer<PresetState> {
                override val descriptor = kotlinx.serialization.builtins.MapSerializer(
                    kotlinx.serialization.builtins.IntArray.serializer(),
                    kotlinx.serialization.builtins.String.serializer()
                ).descriptor
                override fun serialize(encoder: kotlinx.serialization.Encoder, value: PresetState) {
                    kotlinx.serialization.json.Json.encodeToString(value, it)
                        .let { json -> kotlinx.serialization.json.Json.decodeFromString(kotlinx.serialization.json.JsonArray.serializer().serializer(), "[\"$json\"]") }
                }
                override fun deserialize(decoder: kotlinx.serialization.Decoder): PresetState {
                    throw NotImplementedError()
                }
            }}
    }
}
