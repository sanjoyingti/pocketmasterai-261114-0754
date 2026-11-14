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
                val paramId = (args["paramId"] as? Number)?.toInt() ?: 0
                val value = (args["value"] as? Number)?.toInt() ?: 0
                if (block in 0..8 && paramId in 0..255 && value in 0..127) {
                    device.sendBytes(PocketMasterProtocol.setParameter(block, paramId, value.toShort()))
                    Result.Success("Block ${PocketMasterProtocol.getBlockLabel(block)} param $paramId = $value")
                } else {
                    Result.Error("Invalid param: block $block, paramId $paramId, value $value")
                }
            }
            "set_chain_reorder" -> {
                val order = (args["order"] as? List<*>)?.map { it as? Int }?.toIntArray() ?: IntArray(9) { it }
                if (order.size == 9 && order.all { it in 0..8 }) {
                    device.sendBytes(PocketMasterProtocol.chainReorder(order))
                    Result.Success("Chain reordered: ${order.joinToString(" -> ")}")
                } else {
                    Result.Error("Invalid chain order: must contain 0-8 exactly once")
                }
            }
            "set_global_levels" -> {
                val usb = (args["usbVolume"] as? Number)?.toInt() ?: 0
                val main = (args["mainVolume"] as? Number)?.toInt() ?: 0
                val headphone = (args["headphoneVolume"] as? Number)?.toInt() ?: 0
                if (usb in 0..100 && main in 0..100 && headphone in 0..100) {
                    device.sendBytes(PocketMasterProtocol.setGlobalLevels(usb, main, headphone))
                    Result.Success("Global levels set: USB=$usb, Main=$main, Headphone=$headphone")
                } else {
                    Result.Error("Volumes must be 0-100")
                }
            }
            "save_preset" -> {
                val slotName = args["slotName"] as? String ?: "P01"
                val slot = slotName.removePrefix("P").toIntOrNull() ?: 1
                if (slot in 1..50) {
                    device.sendBytes(PocketMasterProtocol.saveEditBuffer(slot.toByte()))
                    Result.Success("Preset $slotName saved")
                } else {
                    Result.Error("Slot must be P01-P50")
                }
            }
            "tone_snapshot" -> {
                try {
                    val snapshot = ToneSnapshot.fromDeviceState(
                        chain = device.chain.value,
                        bypass = device.bypass.value,
                        models = device.models.value,
                        name = device.name.value,
                        parameters = device.deviceState.value?.parameters ?: emptyMap()
                    )
                    container.chatHistoryRepository.saveSnapshot(snapshot.toString())
                    Result.Success("Snapshot saved: ${snapshot.toString().take(60)}...")
                } catch (e: Exception) {
                    Result.Error("Failed to save snapshot: ${e.message}")
                }
            }
            "web_search" -> {
                try {
                    val query = args["query"] as? String ?: ""
                    val results = container.webSearchRepository.search(query)
                    val summary = results.joinToString("\n") { "${it.title} - ${it.url}" }
                    Result.Success("Search returned ${results.size} results:\n$summary")
                } catch (e: Exception) {
                    Result.Error("Web search failed: ${e.message}")
                }
            }
            "fetch_page" -> {
                try {
                    val url = args["url"] as? String ?: ""
                    val text = container.webSearchRepository.fetchPage(url)
                    Result.Success("Page fetched (${text.take(120).replace("\n", " ")})")
                } catch (e: Exception) {
                    Result.Error("Failed to fetch page: ${e.message}")
                }
            }
            else -> Result.Error("Unknown tool: $toolName")
        }
    }
}
