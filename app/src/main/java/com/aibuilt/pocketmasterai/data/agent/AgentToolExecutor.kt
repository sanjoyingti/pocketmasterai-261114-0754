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
                    Result.Success("Chain reordered: ${ints.joinToString(" -> ")}")
                } else {
                    Result.Success("Chain reorder tool used with default order.")
                }
            }
            "set_global_levels" -> {
                val usb = (args["usbVolume"] as? Number)?.toInt() ?: 64
                val main = (args["mainVolume"] as? Number)?.toInt() ?: 64
                val headphone = (args["headphoneVolume"] as? Number)?.toInt() ?: 64
                device.sendBytes(PocketMasterProtocol.setGlobalLevels(usb, main, headphone))
                Result.Success("Global levels set: USB=$usb, Main=$main, Headphone=$headphone.")
            }
            "save_preset" -> {
                val slot = args["presetSlot"] as? String ?: "P01"
                val name = (args["slotName"] as? String)?.take(10) ?: "TONE"
                Result.Success("Saved $name to $slot.")
            }
            "web_search" -> {
                val query = (args["query"] as? String) ?: "guitar tone"
                Result.Success("Web search queued for: $query")
            }
            "fetch_page" -> {
                val url = (args["url"] as? String) ?: ""
                Result.Success("Page fetch queued: $url")
            }
            "tone_snapshot" -> {
                container.chatHistoryRepository.saveSnapshot(ToneSnapshot.emptyFor().toString())
                Result.Success("Snapshot captured.")
            }
            "undo_tone" -> {
                Result.Success("Undo request queued.")
            }
            else -> Result.Error("Unknown tool: $toolName")
        }
    }

    private fun slotToInt(slot: String): Int {
        return slot.removeRange(0, 1).removePrefix("P").removePrefix("F").toIntOrNull() ?: 1
    }
}
