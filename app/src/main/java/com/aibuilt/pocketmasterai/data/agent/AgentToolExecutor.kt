package com.aibuilt.pocketmasterai.data.agent

import com.aibuilt.pocketmasterai.data.PocketMasterDevice
import com.aibuilt.pocketmasterai.data.PocketMasterDevice.Companion.BLOCK_AMP
import com.aibuilt.pocketmasterai.data.PocketMasterDevice.Companion.BLOCK_DLY
import com.aibuilt.pocketmasterai.data.PocketMasterDevice.Companion.BLOCK_EQ
import com.aibuilt.pocketmasterai.data.PocketMasterDevice.Companion.BLOCK_FX1
import com.aibuilt.pocketmasterai.data.PocketMasterDevice.Companion.BLOCK_FX2
import com.aibuilt.pocketmasterai.data.PocketMasterDevice.Companion.BLOCK_IR
import com.aibuilt.pocketmasterai.data.PocketMasterDevice.Companion.BLOCK_NR
import com.aibuilt.pocketmasterai.data.PocketMasterDevice.Companion.BLOCK_RVB
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterProtocol
import com.aibuilt.pocketmasterai.model.ToneSnapshot
import com.aibuilt.pocketmasterai.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentToolExecutor(
    private val device: PocketMasterDevice,
    private val container: AppContainer
) {

    suspend fun execute(toolName: String, args: Map<String, Any>): Result {
        return withContext(Dispatchers.IO) {
            when (toolName) {
                "get_device_state" -> Result.Success(getDeviceStateMessage())
                "list_presets" -> Result.Success("Lists user slots P01–P50 and factory slots F01–F50. Save/load via the Presets tab.")
                "select_preset" -> {
                    val slot = args["presetSlot"] as? String ?: args["slotName"] as? String ?: "P01"
                    Result.Success("Selecting $slot into edit buffer (save required to persist).")
                }
                "set_block_state" -> {
                    val block = numArg(args, "block")
                    val bypassed = boolArg(args, "bypassed")
                    device.sendBytes(PocketMasterProtocol.setBlockState(block, bypassed))
                    Result.Success("Block ${PocketMasterProtocol.getBlockLabel(block)} bypass=${!bypassed}")
                }
                "set_block_model" -> {
                    val block = numArg(args, "block")
                    val modelId = numArg(args, "modelId")
                    device.sendBytes(PocketMasterProtocol.setBlockModel(block, modelId))
                    Result.Success("Block ${PocketMasterProtocol.getBlockLabel(block)} model=$modelId")
                }
                "set_parameter" -> {
                    val block = numArg(args, "block")
                    val paramId = numArg(args, "paramId")
                    val value = numArg(args, "value")
                    device.sendBytes(PocketMasterProtocol.setParameter(block, paramId, value.toShort()))
                    Result.Success("Block $block param=$paramId value=$value")
                }
                "set_chain_reorder" -> {
                    val order = argArrayInt(args, "order")
                    if (order.size != PocketMasterProtocol.NUM_BLOCKS) {
                        return@withContext Result.Error("Order must contain exactly ${PocketMasterProtocol.NUM_BLOCKS} block numbers.")
                    }
                    device.sendBytes(PocketMasterProtocol.chainReorder(order))
                    Result.Success("Chain reordered: ${order.joinToString(" -> ")}")
                }
                "set_global_levels" -> {
                    val usb = numArg(args, "usbVolume", 0)
                    val main = numArg(args, "mainVolume", 0)
                    val hp = numArg(args, "headphoneVolume", 0)
                    device.sendBytes(PocketMasterProtocol.setGlobalLevels(usb, main, hp))
                    Result.Success("Levels: USB=$usb, Main=$main, Headphone=$hp")
                }
                "save_preset" -> {
                    val slot = numArg(args, "slot", 1).coerceIn(1, 50)
                    val nameArg = args["slotName"] as? String
                    val slotByte = (slot - 1).toByte()
                    device.sendBytes(PocketMasterProtocol.saveEditBuffer(slotByte))
                    Result.Success("Saved ${nameArg ?: "current preset"} to P${slot.toString().padStart(2, '0')}")
                }
                "web_search" -> {
                    val query = args["query"] as? String ?: args["model"] as? String
                    if (query.isNullOrBlank()) return@withContext Result.Error("web_search requires 'query'.")
                    container.webSearchRepository.search(query)
                    Result.Success("Searched web for: $query")
                }
                "fetch_page" -> {
                    val url = args["url"] as? String ?: args["model"] as? String
                    if (url.isNullOrBlank()) return@withContext Result.Error("fetch_page requires 'url'.")
                    container.webSearchRepository.fetchPage(url)
                    Result.Success("Fetched: $url")
                }
                "tone_snapshot" -> {
                    val snap = ToneSnapshot(
                        chain = device.chain.value.copyOf(),
                        bypass = device.bypass.value.copyOf(),
                        models = device.models.value.copyOf(),
                        parameters = device.deviceState.value.parameters,
                        name = device.name.value
                    )
                    container.chatHistoryRepository.saveSnapshot(snap.toString())
                    Result.Success("Snapshot saved (undo available in AI Agent tab)")
                }
                else -> Result.Error("Unknown tool: $toolName. Available: get_device_state, list_presets, select_preset, set_block_state, set_block_model, set_parameter, set_chain_reorder, set_global_levels, save_preset, web_search, fetch_page, tone_snapshot")
            }
        }
    }

    private fun getDeviceStateMessage(): String {
        val name = device.name.value
        val chain = device.chain.value.joinToString(" -> ") { PocketMasterProtocol.getBlockLabel(it) }
        val blockInfo = device.chain.value.mapIndexed { i, block ->
            val label = PocketMasterProtocol.getBlockLabel(block)
            val model = device.models.value[block].toInt()
            val bypassed = device.bypass.value[block].toInt() == 1
            "$label (model $model, bypass=${!bypassed})"
        }.joinToString(", ")
        val levels = device.globalLevels.value
        return """
            Name: $name
            Chain: $chain
            Blocks: $blockInfo
            Global levels: USB=${levels[0]}, Main=${levels[1]}, Headphone=${levels[2]}
            """.trimIndent()
    }

    private fun numArg(args: Map<String, Any>, key: String, default: Int = 0): Int {
        return (args[key] as? Number)?.toInt() ?: default
    }

    private fun boolArg(args: Map<String, Any>, key: String): Boolean {
        return args[key] as? Boolean ?: false
    }

    private fun argArrayInt(args: Map<String, Any>, key: String): IntArray {
        return (args[key] as? List<*>)?.mapNotNull { it as? Number }?.mapToInt { it.toInt() }?.toIntArray() ?: IntArray(0)
    }

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Info(val message: String) : Result()
        data class Error(val message: String) : Result()
    }
}
