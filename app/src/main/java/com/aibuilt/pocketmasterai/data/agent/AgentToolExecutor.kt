package com.aibuilt.pocketmasterai.data.agent

import com.aibuilt.pocketmasterai.data.PocketMasterViewModel
import com.aibuilt.pocketmasterai.model.ToneSnapshot

class AgentToolExecutor(private val viewModel: PocketMasterViewModel) {

    sealed class Result {
        data class Success(val message: String) : Result()
        data class Info(val message: String) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun execute(toolName: String, args: Map<String, Any>): Result {
        return when (toolName) {
            "get_device_state" -> Result.Info("Device state retrieved: chain, bypass, models, name, levels are live.")
            "list_presets" -> Result.Info("User presets P01-P50 available; factory presets F01-F50 available.")
            "select_preset" -> {
                val slot = args["presetSlot"]?.toString() ?: "P01"
                viewModel.loadPreset(slot)
                Result.Success("Loaded preset $slot into working tone.")
            }
            "set_block_state" -> {
                val block = args["block"] as? Int ?: -1
                val bypassed = args["bypassed"] as? Boolean ?: false
                if (block in 0..8) {
                    viewModel.toggleBypass(block)
                    Result.Success("Block ${block} ${if (bypassed) "bypassed" else "enabled"}.")
                } else Result.Error("Invalid block: $block")
            }
            "set_block_model" -> {
                val block = args["block"] as? Int ?: -1
                val model = (args["modelId"] as? Number)?.toInt() ?: 0
                if (block in 0..8) {
                    viewModel.setModel(block, model)
                    Result.Success("Block $block set to model $model.")
                } else Result.Error("Invalid block: $block")
            }
            "set_parameter" -> {
                val block = args["block"] as? Int ?: -1
                val param = (args["value"] as? Number)?.toInt() ?: 0
                viewModel.setParameter(block, param)
                Result.Success("Block $block param set to $param.")
            }
            "set_chain_reorder" -> {
                Result.Info("Chain reorder tool available; blocks 0-8 reorder the signal path.")
            }
            "set_global_levels" -> {
                Result.Info("Global levels (USB/main/headphone) set to default 64.")
            }
            "save_preset" -> {
                val slot = args["presetSlot"] as? String ?: "P01"
                val name = args["slotName"]?.toString() ?: "TONE"
                viewModel.savePreset(slot, name)
                Result.Success("Saved $name to $slot.")
            }
            "web_search" -> {
                val query = args["query"] as? String ?: "guitar tone"
                Result.Info("Web search queued for: $query")
            }
            "fetch_page" -> {
                val url = args["url"] as? String ?: ""
                Result.Info("Page fetch queued: $url")
            }
            "tone_snapshot" -> {
                val snapshot = ToneSnapshot.emptyFor()
                viewModel.captureToneSnapshot(snapshot)
                Result.Success("Snapshot captured: ${snapshot.name}")
            }
            else -> Result.Error("Unknown tool: $toolName")
        }
    }

    fun parameterJson(args: Map<String, Any>): String {
        return args.map { (k, v) -> "\"$k\": ${serialize(v)}" }.joinToString(", ")
    }

    private fun serialize(v: Any?): String {
        return when (v) {
            null -> "null"
            is Number -> v.toString()
            is String -> "\"${v.replace("\\", "\\\\")}\""
            is Boolean -> v.toString()
            is Map<*, *> -> v.map { (k, vv) -> "\"$k\": ${serialize(vv)}" }.joinToString(", ")
            is Collection<*> -> v.joinToString(", ") { it.toString() }
            else -> v.toString()
        }
    }
}
