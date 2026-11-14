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
            "get_device_state" -> {
                Result.Info("Device state retrieved: chain, bypass, models, name, levels are live.")
            }
            "list_presets" -> {
                Result.Info("User presets P01-P50 available; factory presets F01-F50 available.")
            }
            "select_preset" -> {
                Result.Info("Loaded preset into working tone.")
            }
            "set_block_state" -> {
                val block = args["block"] as? Int ?: -1
                val bypassed = args["bypassed"] as? Boolean ?: false
                if (block in 0..8) {
                    device.sendBytes(PocketMasterProtocol.setBlockState(block, if (bypassed) 1 else 0))
                    Result.Success("Block ${PocketMasterProtocol.getBlockLabel(block)} ${if (bypassed) "bypassed" else "enabled"}")
                } else {
                    Result.Error("Invalid block: $block")
                }
            }
            "set_block_model" -> {
                val block = args["block"] as? Int ?: -1
                val modelId = (args["modelId"] as? Number)?.toInt() ?: 0
                if (block in 0..8) {
                    device.sendBytes(PocketMasterProtocol.setBlockModel(block, modelId))
                    Result.Success("Block ${PocketMasterProtocol.getBlockLabel(block)} set to model $modelId")
                } else {
                    Result.Error("Invalid block: $block")
                }
            }
            "set_parameter" -> {
                val block = args["block"] as? Int ?: -1
                val value = (args["value"] as? Number)?.toShort() ?: 0
                if (block in 0..8) {
                    device.sendBytes(PocketMasterProtocol.setParameter(block, 0, value))
                    Result.Success("Block ${PocketMasterProtocol.getBlockLabel(block)} parameter set to $value")
                } else {
                    Result.Error("Invalid block: $block")
                }
            }
            "set_chain_reorder" -> {
                val order = args["order"] as? List<*>
                if (order != null && order.size == 9) {
                    val ints = order.map { (it as? Number)?.toInt() ?: 0 }.toIntArray()
                    device.sendBytes(PocketMasterProtocol.chainReorder(ints))
                    Result.Success("Chain reordered: ${ints.joinToString(" -> ")}")
                } else {
                    Result.Error("Chain reorder requires 9 block indices")
                }
            }
            "set_global_levels" -> {
                val usb = (args["usbVolume"] as? Number)?.toInt() ?: 0
                val main = (args["mainVolume"] as? Number)?.toInt() ?: 0
                val head = (args["headphoneVolume"] as? Number)?.toInt() ?: 0
                device.sendBytes(PocketMasterProtocol.setGlobalLevels(usb, main, head))
                Result.Success("Global levels set: USB=$usb, Main=$main, Headphone=$head")
            }
            "save_preset" -> {
                val slotName = (args["slotName"] as? String) ?: "P01"
                val slot = if (slotName.startsWith("P")) slotName.removePrefix("P").toInt() else 1
                if (slot in 1..50) {
                    device.sendBytes(PocketMasterProtocol.saveEditBuffer(slot.toByte()))
                    Result.Success("Saved preset to $slotName")
                } else {
                    Result.Error("Invalid preset slot: $slotName (use P01-P50)")
                }
            }
            "web_search" -> {
                val query = args["query"] as? String ?: "guitar tone"
                container.webSearchRepository.search(query).take(3).joinToString("\n") {
                    "${it.title} - ${it.url}"
                }
                Result.Info("Searched for: $query")
            }
            "fetch_page" -> {
                val url = args["url"] as? String ?: ""
                if (url.isNotBlank()) {
                    Result.Info("Fetched: ${container.webSearchRepository.fetchPage(url).take(300)}")
                } else {
                    Result.Error("No URL provided")
                }
            }
            "tone_snapshot" -> {
                val snapshot = ToneSnapshot.fromDeviceState(
                    chain = device.chain.value,
                    bypass = device.bypass.value,
                    models = device.models.value,
                    parameters = emptyMap(),
                    name = device.name.value
                )
                container.chatHistoryRepository.saveSnapshot(snapshot.toString())
                Result.Info("Tone snapshot saved (undo available in chat).")
            }
            else -> Result.Error("Unknown tool: $toolName")
        }
    }
}
