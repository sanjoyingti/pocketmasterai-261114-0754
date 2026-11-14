package com.aibuilt.pocketmasterai.data.agent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

sealed class AiResponseEvent {
    data class TextChunk(val text: String) : AiResponseEvent()
    data class ToolCall(val toolName: String, val args: Map<String, Any>) : AiResponseEvent()
    data class ToolResult(val toolName: String, val result: AgentToolExecutor.Result) : AiResponseEvent()
    data object Done : AiResponseEvent()
    data class Error(val message: String) : AiResponseEvent()
}

interface AiProvider {
    val modelName: String
    val hasFunctionCalling: Boolean
    suspend fun generate(
        messages: List<AiMessage>,
        tools: List<AgentToolDefinitions.ToolDef>,
        onEvent: (AiResponseEvent) -> Unit
    )
}

data class AiMessage(
    val role: String,
    val content: String
)

// --- Google OAuth + Gemini REST provider ---
class GeminiProvider(
    private val apiKey: String,
    private val oauthToken: String?,
    private val model: String
) : AiProvider {

    override val modelName: String = model
    override val hasFunctionCalling: Boolean = true

    private val okHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiBase: String
        get() = "https://generativelanguage.googleapis.com/v1beta"

    override suspend fun generate(
        messages: List<AiMessage>,
        tools: List<AgentToolDefinitions.ToolDef>,
        onEvent: (AiResponseEvent) -> Unit
    ) {
        try {
            val systemMsg = AiMessage("system", SYSTEM_PROMPT)
            val allMessages = listOf(systemMsg) + messages
            val contentParts = allMessages.map { part ->
                buildString {
                    append("{\"role\": \"${part.role}\", \"parts\": [{\"text\": \"${part.content.replace("\\", "\\\\")}\"}]}")
                }
            }
            val toolsDef = if (tools.isNotEmpty()) {
                "{\"tools\": [{\"functionDeclarations\": [${tools.joinToString(", ") { "{\\\"name\\\": \\\"${it.name}\\\", \\\"description\\\": \\\"${it.description}\\\", \\\"parameters\\\": {\\\"type\\\": \\\"object\\\", \\\"properties\\\": {\\\"block\\\": {\\\"type\\\": \\\"integer\\\"}, \\\"modelId\\\": {\\\"type\\\": \\\"integer\\\"}, \\\"value\\\": {\\\"type\\\": \\\"integer\\\"}, \\\"presetSlot\\\": {\\\"type\\\": \\\"string\\\"}, \\\"query\\\": {\\\"type\\\": \\\"string\\\"}, \\\"url\\\": {\\\"type\\\": \\\"string\\\"}, \\\"bypassed\\\": {\\\"type\\\": \\\"boolean\\\"}, \\\"slotName\\\": {\\\"type\\\": \\\"string\\\"}, \\\"usbVolume\\\": {\\\"type\\\": \\\"integer\\\"}, \\\"mainVolume\\\": {\\\"type\\\": \\\"integer\\\"}, \\\"headphoneVolume\\\": {\\\"type\\\": \\\"integer\\\"}}}}]}}]}"
            } else "null"

            val bodyContent = buildString {
                append("{\"contents\": [")
                append(contentParts.joinToString(","))
                append("], \"generationConfig\": {\"temperature\": 0.3, \"maxOutputTokens\": 4096, \"responseFormat\": \"json_object\"}, \"tools\": ${toolsDef}}")
            }

            val url = "$apiBase/models/${model.encodeURL()}:generateContent?key=${apiKey.encodeURL()}"
            val headers = mutableMapOf("Content-Type" to "application/json")
            if (oauthToken != null) headers["Authorization"] = "Bearer $oauthToken"

            val request = okhttp3.Request.Builder()
                .url(url)
                .post(bodyContent.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .headers(okhttp3.Headers.of(headers))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string()
            response.close()
            if (!response.isSuccessful || body.isNullOrBlank()) {
                onEvent(AiResponseEvent.Error("API error: ${response.code} ${response.message}"))
                return
            }

            parseGeminiResponse(body, onEvent)
            onEvent(AiResponseEvent.Done)
        } catch (e: Exception) {
            onEvent(AiResponseEvent.Error(e.message ?: "Generation failed"))
        }
    }

    private fun String.encodeURL(): String =
        this.replace(" ", "%20").replace("<", "%3C").replace(">", "%3E").replace("&", "%26")
            .replace("\"", "%22").replace("'", "%27").replace("+", "%2B")

    private fun parseGeminiResponse(body: String, onEvent: (AiResponseEvent) -> Unit) {
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        val json = if (start >= 0 && end > start) body.substring(start, end + 1) else body
        val functionCall = extractFunctionCall(json)
        if (functionCall != null) {
            onEvent(functionCall)
            return
        }
        val text = extractText(json)
        if (text.isNotEmpty()) {
            onEvent(AiResponseEvent.TextChunk(text))
        }
    }

    private fun extractFunctionCall(json: String): AiResponseEvent.ToolCall? {
        val fnameStart = json.indexOf("\"name\"")
        if (fnameStart < 0) return null
        val fname = extractSimpleString(json, fnameStart + "\"name\"".length) ?: return null
        val argsStart = json.indexOf("{", fnameStart)
        val argsEnd = json.indexOf("}", argsStart)
        if (argsStart < 0 || argsEnd < 0) return null
        val argsJson = json.substring(argsStart + 1, argsEnd)
        val args = parseFunctionArgs(argsJson)
        return AiResponseEvent.ToolCall(fname, args)
    }

    private fun extractText(json: String): String {
        val candidates = listOf(
            json.substringAfter("\"text\":\""),
            json.substringAfter("\"candidates\": [{\"content\": {\"parts\": [{\"text\": \""),
            json.substringAfter("\"content\": {\"parts\": [{\"text\": \"")
        )
        val text = candidates.first { it.isNotBlank() }
        val clean = text.takeWhile { it != '"' }.replace("\\n", "\n")
        return clean
    }

    private fun extractSimpleString(json: String, from: Int): String? {
        val q = json.indexOf('"', from)
        if (q < 0) return null
        var end = q + 1
        var escape = false
        while (end < json.length) {
            val c = json[end]
            if (escape) { escape = false; end++ }
            else if (c == '\\') escape = true; else if (c == '"') break
            else end++
        }
        return if (end > q + 1) json.substring(q + 1, end) else null
    }

    private fun parseFunctionArgs(json: String): Map<String, Any> {
        val args = mutableMapOf<String, Any>()
        var pos = 0
        while (pos < json.length) {
            val nameStart = json.indexOf('"', pos)
            if (nameStart < 0) break
            val nameEnd = json.indexOf('"', nameStart + 1)
            if (nameEnd < 0) break
            val name = json.substring(nameStart + 1, nameEnd)
            val colon = json.indexOf(':', nameEnd)
            val valueStart = json.indexOf('{', colon)
            val valueEnd = json.indexOf('}', valueStart)
            if (valueStart < 0 || valueEnd < 0) {
                val valStart = colon + 1
                val valEnd = json.indexOf(',', valueStart)
                val raw = json.substring(valStart, if (valEnd < 0) json.length else valEnd).trim()
                args[name] = parseScalar(raw)
                pos = if (valEnd < 0) json.length else valEnd + 1
            } else {
                args[name] = parseFunctionArgs(json.substring(valueStart + 1, valueEnd))
                pos = valueEnd + 1
            }
        }
        return args
    }

    private fun parseScalar(raw: String): Any {
        val t = raw.trim()
        return when {
            t == "true" -> true
            t == "false" -> false
            t == "null" -> null
            try -> t.toInt()
            catch (_: NumberFormatException) -> t.toDouble()
            else -> raw
        }
    }
}

private const val SYSTEM_PROMPT =
    "You are an autonomous guitar tone agent for the Sonicake Pocket Master (QME-10) multi-effects processor. " +
    "You control the device through tool calls. Always follow this workflow for song/solo requests: " +
    "(1) web_search for the guitarist's exact rig, then (2) fetch_page on the best rig page, " +
    "(3) map every pedal/amp/cab/mic/setting to the Pocket Master's 9-slot chain using only model IDs and parameters " +
    "that exist in the device map, (4) set blocks, bypass, models, parameters, and chain order, (5) save to a user slot. " +
    "Always emit a valid JSON object with fields: intent (a one-line description of what you're doing), " +
    "tone (a JSON object with keys: chain=9-element block order array, blocks={\"0\":{\"model\":N,\"params\":{\"paramId\":value},\"bypassed\":bool}}). " +
    "When the user says 'load the tone' or 'set all settings accordingly', emit a tool call for every hardware tool needed, " +
    "in order: get_device_state, then set_chain_reorder, set_block_state for each block, set_block_model for each block, " +
    "set_parameter for each affected parameter, set_global_levels, save_preset, then web_search/fetch_page first for tone research. " +
    "Never invent model names or values outside the device map. Keep all tool args as JSON-serializable values (ints, strings, booleans)."

// --- OpenAI-compatible provider (DeepSeek, OpenRouter, local LLMs) ---
class OpenAiCompatProvider(
    private val apiKey: String,
    private val baseUrl: String,
    private val model: String
) : AiProvider {

    override val modelName: String = model
    override val hasFunctionCalling: Boolean = true

    private val okHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiBase: String
        get() = baseUrl.trimEnd('/') + "/chat/completions"

    override suspend fun generate(
        messages: List<AiMessage>,
        tools: List<AgentToolDefinitions.ToolDef>,
        onEvent: (AiResponseEvent) -> Unit
    ) {
        try {
            val allMessages = messages.map { m -> mapOf("role" to m.role, "content" to m.content) }
            val toolsDef = if (tools.isNotEmpty()) {
                tools.map { t ->
                    mapOf(
                        "type" to "function",
                        "function" to mapOf(
                            "name" to t.name,
                            "description" to t.description,
                            "parameters" to mapOf(
                                "type" to "object",
                                "properties" to mapOf(
                                    "block" to mapOf("type" to "integer"),
                                    "modelId" to mapOf("type" to "integer"),
                                    "value" to mapOf("type" to "integer"),
                                    "presetSlot" to mapOf("type" to "string"),
                                    "query" to mapOf("type" to "string"),
                                    "url" to mapOf("type" to "string"),
                                    "bypassed" to mapOf("type" to "boolean"),
                                    "slotName" to mapOf("type" to "string"),
                                    "usbVolume" to mapOf("type" to "integer"),
                                    "mainVolume" to mapOf("type" to "integer"),
                                    "headphoneVolume" to mapOf("type" to "integer")
                                )
                            )
                        )
                    )
                }
            } else null
            val body = buildMap<String, Any?> {
                put("model", model)
                put("messages", allMessages)
                put("temperature", 0.3)
                put("max_tokens", 4096)
                if (toolsDef != null) put("tools", toolsDef)
            }
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.encodeToString(kotlinx.serialization.SerializableSerializer(kotlinx.serialization.json.JsonElement), kotlinx.serialization.json.Json.parseToJsonElement(kotlinx.serialization.json.Json.encodeToString(body)).jsonPrimitive.content)
            val url = "$apiBase"
            val headers = mutableMapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer $apiKey"
            )

            val request = okhttp3.Request.Builder()
                .url(url)
                .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .headers(okhttp3.Headers.of(headers))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            response.close()
            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                onEvent(AiResponseEvent.Error("API error: ${response.code} ${response.message}"))
                return
            }

            parseOpenAiResponse(responseBody, onEvent)
            onEvent(AiResponseEvent.Done)
        } catch (e: Exception) {
            onEvent(AiResponseEvent.Error(e.message ?: "Generation failed"))
        }
    }

    private fun parseOpenAiResponse(body: String, onEvent: (AiResponseEvent) -> Unit) {
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        val json = if (start >= 0 && end > start) body.substring(start, end + 1) else body
        val choiceStart = json.indexOf("\"choices\"")
        if (choiceStart >= 0) {
            val choiceBlock = json.substring(choiceStart)
            val toolCallsStart = choiceBlock.indexOf("\"tool_calls\"")
            if (toolCallsStart >= 0) {
                val toolCallsBlock = choiceBlock.substring(toolCallsStart)
                extractOpenAiToolCalls(toolCallsBlock, onEvent)
                return
            }
            val text = extractOpenAiText(choiceBlock)
            if (text.isNotEmpty()) {
                onEvent(AiResponseEvent.TextChunk(text))
            }
        }
    }

    private fun extractOpenAiToolCalls(block: String, onEvent: (AiResponseEvent) -> Unit) {
        var pos = 0
        while (pos < block.length) {
            val objStart = block.indexOf("{", pos)
            if (objStart < 0) break
            val objEnd = block.indexOf("}", objStart)
            if (objEnd < 0) break
            val obj = block.substring(objStart, objEnd + 1)
            val nameStart = obj.indexOf("\"name\"")
            if (nameStart >= 0) {
                val name = extractSimpleString(obj, nameStart + "\"name\"".length) ?: break
                val argsStart = obj.indexOf("{", nameStart)
                val argsEnd = obj.lastIndexOf("}", obj)
                if (argsStart > 0 && argsEnd > argsStart) {
                    val argsJson = obj.substring(argsStart + 1, argsEnd)
                    val args = parseFunctionArgs(argsJson)
                    onEvent(AiResponseEvent.ToolCall(name, args))
                }
            }
            pos = objEnd + 1
        }
    }

    private fun extractOpenAiText(block: String): String {
        val textStart = block.indexOf("\"content\": \"")
        if (textStart < 0) return ""
        val after = block.substring(textStart + "\"content\": \"".length)
        return after.takeWhile { it != '"' }.replace("\\n", "\n").replace("\\\"", "\"")
    }
}
