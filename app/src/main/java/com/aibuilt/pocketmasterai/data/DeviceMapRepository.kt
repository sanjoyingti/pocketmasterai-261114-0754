package com.aibuilt.pocketmasterai.data

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class DeviceMapRepository {

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

    @Serializable
    data class DeviceMap(
        @SerialName("effects") val effects: Map<String, List<EffectEntry>>,
        @SerialName("amps") val amps: Map<String, List<EffectEntry>>,
        @SerialName("cabs") val cabs: Map<String, List<EffectEntry>>
    )

    private var _map: DeviceMap? = null

    suspend fun loadFromAssets(assetManager: AssetManager): DeviceMap {
        return withContext(Dispatchers.IO) {
            val json = assetManager.open("device_map.json").use { it.readBytes().decodeToString() }
            Json { ignoreUnknownKeys = true }.decodeFromString(json)
        }
    }

    fun getMap(): DeviceMap? = _map

    fun setMap(map: DeviceMap) { _map = map }

    fun validateModel(effectType: String, modelId: Int): Boolean {
        val list = when (effectType.lowercase()) {
            "amp" -> _map?.amps?.get("amp")
            "cab" -> _map?.cabs?.get("cab")
            else -> _map?.effects?.get(effectType)
        }
        return list?.any { it.id == modelId } == true
    }

    fun validateParameter(effectType: String, paramName: String, value: Int): Boolean {
        val list = when (effectType.lowercase()) {
            "amp" -> _map?.amps?.get("amp")
            "cab" -> _map?.cabs?.get("cab")
            else -> _map?.effects?.get(effectType)
        }
        return list?.any { it.params.containsKey(paramName) } ?: true
    }

    fun clampValue(effectType: String, paramName: String, value: Int): Int {
        val list = when (effectType.lowercase()) {
            "amp" -> _map?.amps?.get("amp")
            "cab" -> _map?.cabs?.get("cab")
            else -> _map?.effects?.get(effectType)
        }
        return list?.firstOrNull()?.params?.get(paramName)?.let {
            value.coerceIn(it.min, it.max)
        } ?: value
    }

    fun listModels(effectType: String): List<EffectEntry> {
        return when (effectType.lowercase()) {
            "amp" -> _map?.amps?.get("amp") ?: emptyList()
            "cab" -> _map?.cabs?.get("cab") ?: emptyList()
            else -> _map?.effects?.get(effectType) ?: emptyList()
        }
    }
}
