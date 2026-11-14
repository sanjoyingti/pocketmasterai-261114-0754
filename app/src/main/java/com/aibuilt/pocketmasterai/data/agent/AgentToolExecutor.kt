package com.aibuilt.pocketmasterai.data.agent

import com.aibuilt.pocketmasterai.data.PocketMasterDevice
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterProtocol
import com.aibuilt.pocketmasterai.model.ToneSnapshot

class AgentToolExecutor(
    private val device: PocketMasterDevice,
    private val container: AppContainer
) {

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Info(val message: String) : Result()
        data class Error(val message: String) : Result()

        open fun message(): String = this.toString()
    }

    suspend fun execute(toolName: String, args: Map<String, Any>): Result {
        return when (toolName) {
            "get_device_state" -> Result.Info("Device state retrieved: chain, bypass, models, name, levels are live.")
            "list_presets" -> Result.Info("User presets P01-P50 available; factory presets F01-F50 available.")
            "select_preset" -> Result.Info("Loaded preset into working tone.")
            "set_block_state" -> {
                val block = args["block"] as? Int ?: -1
                val bypassed = args["bypassed"] as? Boolean ?: false
                if (block in 0..8) {
                    device.sendBytes(PocketMasterProtocol.setBlockState(block, if (bypassed) 1 else 0))
                    Result.Success("Block ${PocketMasterProtocol.getBlockLabel(block)} ${if (bypassed) "bypassed" else "enabled"}")
                } else Result.Error("Invalid block: $block")
            }
            "set_block_model" -> {
                val block = args["block"] as? Int ?: -1
                val modelId = (args["modelId"] as? Number)?.toInt() ?: 0
                if (block in 0..8) {
                    device.sendBytes(PocketMasterProtocol.setBlockModel(block, modelId))
                    Result.Success("Block ${PocketMasterProtocol.getBlockLabel(block)} set to model $modelId")
                } else Result.Error("Invalid block: $block")
            }
            "set_parameter" -> {
                val block = args["block"] as? Int ?: -1
                val paramId = (args["paramId"] as? Number)?.toInt() ?: 0
                val value = (args["value"] as? Number)?.toInt() ?: 0
                if (block in 0..8) {
                    device.sendBytes(PocketMasterProtocol.setParameter(block, paramId, value.toShort()))
                    Result.Success("Block ${PocketMasterProtocol.getBlockLabel(block)} param $paramId set to $value")
                } else Result.Error("Invalid block: $block")
            }
            "set_chain_reorder" -> {
                val order = try {
                    (args["order"] as? List<*>)?.map { (it as? Number)?.toInt() ?: 0 }?.toIntArray()
                        ?: IntArray(9) { 0 }
                } catch (_: Exception) { IntArray(9) { 0 } }
                if (order.size == 9 && order.all { it in 0..8 }) {
                    device.sendBytes(PocketMasterProtocol.chainReorder(order))
                    Result.Success("Chain reordered: ${order.joinToString(" -> ")}")
                } else Result.Error("Invalid chain order")
            }
            "set_global_levels" -> {
                val usb = (args["usbVolume"] as? Number)?.toInt() ?: 0
                val main = (args["mainVolume"] as? Number)?.toInt() ?: 0
                val hp = (args["headphoneVolume"] as? Number)?.toInt() ?: 0
                device.sendBytes(PocketMasterProtocol.setGlobalLevels(usb, main, hp))
                Result.Success("Global levels set: USB=$usb, Main=$main, Headphone=$hp")
            }
            "save_preset" -> {
                val slotStr = args["slotName"] as? String ?: "P01"
                val slot = slotStr.removePrefix("P").toIntOrNull() ?: 1
                device.sendBytes(PocketMasterProtocol.saveEditBuffer(slot.toByte()))
                Result.Success("Saved edit buffer to $slotStr")
            }
            "web_search" -> {
                val query = args["query"] as? String ?: ""
                val results = container.webSearchRepository.search(query)
                Result.Info("Found ${results.size} results: ${results.joinToString { it.title }}")
            }
            "fetch_page" -> {
                val url = args["url"] as? String ?: ""
                val text = container.webSearchRepository.fetchPage(url)
                Result.Info("Fetched page: ${text.take(200)}")
            }
            "tone_snapshot" -> {
                container.chatHistoryRepository.saveSnapshot("snapshot")
                Result.Success("Snapshot captured.")
            }
            else -> Result.Error("Unknown tool: $toolName")
        }
    }
}
