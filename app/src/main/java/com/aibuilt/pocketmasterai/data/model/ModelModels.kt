package com.aibuilt.pocketmasterai.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceMap(
    @SerialName("effects") val effects: Map<String, List<EffectEntry>>,
    @SerialName("amps") val amps: Map<String, List<EffectEntry>>,
    @SerialName("cabs") val cabs: Map<String, List<EffectEntry>>
)

@Serializable
data class EffectEntry(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("params") val params: Map<String, ParamMeta>
)

@Serializable
data class ParamMeta(
    @SerialName("min") val min: Int,
    @SerialName("max") val max: Int,
    @SerialName("default") val default: Int?
)
