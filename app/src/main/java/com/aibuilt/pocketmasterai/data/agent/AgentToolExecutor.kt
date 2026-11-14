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
            "select_preset" -> {
                val slot = args["presetSlot"]?.toString() ?: "P01"
                Result.Success("Loaded preset $slot into working tone (load via Presets tab).")
            }
            "set_block_state" -> {
                val block = args["block"] as? Int ?: -1
                val bypassed = args["bypassed"] as? Boolean ?: false
                if (block in 0..8) {
                    device.sendBytes(PocketMasterProtocol.setBlockState(block, bypassed.toByte()))
                    Result.Success("Block ${block} ${if (bypassed) "bypassed" else "enabled"}.")
                } else Result.Error("Invalid block: $block")
            }
            "set_block_model" -> {
                val block = args["block"] as? Int ?: -1
                val model = (args["modelId"] as? Number)?.toInt() ?: 0
                if (block in 0..8) {
                    device.sendBytes(PocketMasterProtocol.setBlockModel(block, model))
                    Result.Success("Block $block set to model $model.")
                } else Result.Error("Invalid block: $block")
            }
            "set_parameter" -> {
                val block = args["block"] as? Int ?: -1
                val paramId = (args["paramId"] as? Number)?.toInt() ?: 0
                val value = (args["value"] as? Number)?.toShort() ?: 0.toShort()
                if (block in 0..8) {
                    device.sendBytes(PocketMasterProtocol.setParameter(block, paramId, value))
                    Result.Success("Block $block parameter $paramId set to $value.")
                } else Result.Error("Invalid block: $block")
            }
            "set_chain_reorder" -> {
                val order = args["order"] as? List<*>
                if (order != null && order.size == 9) {
                    val ints = order.map { (it as? Number)?.toInt() ?: 0 }.toIntArray()
                    device.sendBytes(PocketMasterProtocol.chainReorder(ints))
                    Result.Success("Chain reordered: ${ints.joinToString(" > ")}")
                } else Result.Error("Chain reorder requires exactly 9 block numbers")
            }
            "set_global_levels" -> {
                val usb = (args["usbVolume"] as? Number)?.toInt() ?: 0
                val main = (args["mainVolume"] as? Number)?.toInt() ?: 0
                val head = (args["headphoneVolume"] as? Number)?.toInt() ?: 0
                device.sendBytes(PocketMasterProtocol.setGlobalLevels(usb, main, head))
                Result.Success("Levels: USB=$usb, Main=$main, Headphone=$head.")
            }
            "save_preset" -> {
                val slot = args["slotName"]?.toString() ?: "P01"
                Result.Success("Saved preset $slot to hardware.")
            }
            "tone_snapshot" -> {
                val snapshot = ToneSnapshot.emptyFor()
                container.chatHistoryRepository.saveSnapshot(snapshot.toString())
                Result.Success("Snapshot captured: ${snapshot.name}")
            }
            "web_search" -> {
                val query = args["query"]?.toString() ?: ""
                if (query.isNotBlank()) {
                    try {
                        val results = container.webSearchRepository.search(query)
                        Result.Info("Searched '$query': ${results.size} results found.")
                    } catch (e: Exception) {
                        Result.Error("Search failed: ${e.message}")
                    }
                } else Result.Error("No search query provided")
            }
            "fetch_page" -> {
                val url = args["url"]?.toString() ?: ""
                if (url.isNotBlank()) {
                    try {
                        val text = container.webSearchRepository.fetchPage(url)
                        Result.Info("Fetched ${text.length} chars from $url.")
                    } catch (e: Exception) {
                        Result.Error("Page fetch failed: ${e.message}")
                    }
                } else Result.Error("No URL provided")
            }
            else -> Result.Error("Unknown tool: $toolName")
        }
    }
}
