package com.aibuilt.pocketmasterai.data.agent

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    override suspend fun generate(
        messages: List<AiMessage>,
        tools: List<AgentToolDefinitions.ToolDef>,
        onEvent: (AiResponseEvent) -> Unit
    ) {
        try {
            val systemMsg = AiMessage("system", SYSTEM_PROMPT)
            val allMessages = listOf(systemMsg) + messages
            val contents = allMessages.joinToString(",") { m ->
                buildString {
                    append("{\"role\":\"${m.role}\",\"parts\":[{\"text\":\"${m.content.escapeJson()}\"}]}")
                }
            }
            val toolsDef = if (tools.isNotEmpty()) {
                "{\"tools\":[{\"functionDeclarations\":[${tools.joinToString(",") { t ->
                    "{\"name\":\"${t.name}\",\"description\":\"${t.description}\",\"parameters\":{\"type\":\"object\",\"properties\":{\"block\":{\"type\":\"integer\"},\"modelId\":{\"type\":\"integer\"},\"paramId\":{\"type\":\"integer\"},\"value\":{\"type\":\"integer\"},\"presetSlot\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"},\"url\":{\"type\":\"string\"},\"bypassed\":{\"type\":\"boolean\"},\"slotName\":{\"type\":\"string\"},\"usbVolume\":{\"type\":\"integer\"},\"mainVolume\":{\"type\":\"integer\"},\"headphoneVolume\":{\"type\":\"integer\"},\"order\":{\"type\":\"array\"}}}}"
                }}]}}]"
            } else "null"

            val body = "{\"contents\":[$contents],\"generationConfig\":{\"temperature\":0.3,\"maxOutputTokens\":4096},\"tools\":$toolsDef}"
            val url = "https://generativelanguage.googleapis.com/v1beta/models/${model.escapeUrl()}:generateContent?key=${apiKey.escapeUrl()}"
            val headers = mutableMapOf("Content-Type" to "application/json")
            if (oauthToken != null) headers["Authorization"] = "Bearer $oauthToken"

            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .headers(Headers.of(headers))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            response.close()
            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                onEvent(AiResponseEvent.Error("API error: ${response.code} ${response.message}"))
                return
            }

            parseGeminiResponse(responseBody!!, onEvent)
            onEvent(AiResponseEvent.Done)
        } catch (e: Exception) {
            onEvent(AiResponseEvent.Error(e.message ?: "Generation failed"))
        }
    }

    private fun parseGeminiResponse(body: String, onEvent: (AiResponseEvent) -> Unit) {
        val json = extractObject(body) ?: return
        val functionCall = extractFunctionCall(json)
        if (functionCall != null) {
            onEvent(functionCall)
            return
        }
        val text = extractTextFromJson(json)
        if (text.isNotEmpty()) {
            onEvent(AiResponseEvent.TextChunk(text))
        }
    }

    private fun extractObject(body: String): String? {
        val start = body.indexOf('{')
        val end = body.lastIndexOf('}')
        return if (start >= 0 && end > start) body.substring(start, end + 1) else null
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

    private fun extractTextFromJson(json: String): String {
        val candidates = listOf(
            json.substringAfter("\"text\":\""),
            json.substringAfter("\"parts\":[{\"text\":\""),
            json.substringAfter("\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"")
        )
        val text = candidates.firstOrNull { it.isNotBlank() } ?: return ""
        val clean = text.takeWhile { it != '"' }.replace("\\n", "\n").replace("\\/", "/")
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
            else if (c == '\\') escape = true
            else if (c == '"') break
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
            if (valueStart >= 0) {
                val valueEnd = json.indexOf('}', valueStart)
                if (valueEnd < 0) break
                args[name] = parseFunctionArgs(json.substring(valueStart + 1, valueEnd))
                pos = valueEnd + 1
            } else {
                val valStart = colon + 1
                val valEnd = json.indexOf(',', valueStart)
                val raw = json.substring(valStart, if (valEnd < 0) json.length else valEnd).trim()
                args[name] = parseScalar(raw)
                pos = if (valEnd < 0) json.length else valEnd + 1
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
            catch (_: NumberFormatException) -> try -> t.toDouble()
            catch (_: NumberFormatException) -> t
            else -> t
        }
    }

    private fun String.escapeJson(): String =
        this.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private fun String.escapeUrl(): String =
        this.replace(" ", "%20").replace("<", "%3C").replace(">", "%3E")
            .replace("&", "%26").replace("\"", "%22")
}

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
            val allMessages = messages.map { m ->
                "{\"role\":\"${m.role}\",\"content\":\"${m.content.escapeJson()}\"}"
            }.joinToString(",")
            val toolsDef = if (tools.isNotEmpty()) {
                tools.map { t ->
                    buildString {
                        append("{\"type\":\"function\",\"function\":{\"name\":\"${t.name}\",")
                        append("\"description\":\"${t.description}\",")
                        append("\"parameters\":{\"type\":\"object\",\"properties\":{\"block\":{\"type\":\"integer\"},")
                        append("\"modelId\":{\"type\":\"integer\"},\"paramId\":{\"type\":\"integer\"},\"value\":{\"type\":\"integer\"},")
                        append("\"presetSlot\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"},")
                        append("\"url\":{\"type\":\"string\"},\"bypassed\":{\"type\":\"boolean\"},")
                        append("\"slotName\":{\"type\":\"string\"},\"usbVolume\":{\"type\":\"integer\"},")
                        append("\"mainVolume\":{\"type\":\"integer\"},\"headphoneVolume\":{\"type\":\"integer\"},\"order\":{\"type\":\"array\"}}}}")
                    }
                }.joinToString(",")
            } else "null"
            val body = "{\"model\":\"$model\",\"messages\":[$allMessages],\"temperature\":0.3,\"max_tokens\":4096,\"tools\":$toolsDef}"

            val request = Request.Builder()
                .url(apiBase)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            response.close()
            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                onEvent(AiResponseEvent.Error("API error: ${response.code} ${response.message}"))
                return
            }

            parseOpenAiResponse(responseBody!!, onEvent)
            onEvent(AiResponseEvent.Done)
        } catch (e: Exception) {
            onEvent(AiResponseEvent.Error(e.message ?: "Generation failed"))
        }
    }

    private fun parseOpenAiResponse(body: String, onEvent: (AiResponseEvent) -> Unit) {
        val json = extractObject(body) ?: return
        val choiceEnd = findChoiceEnd(json)
        if (choiceEnd < 0) return
        val choiceJson = json.substring(0, choiceEnd)

        val contentStart = choiceJson.indexOf("\"content\"")
        if (contentStart >= 0) {
            val text = extractStringInsideQuotes(choiceJson, contentStart + "\"content\"".length)
            if (text.isNotEmpty()) {
                onEvent(AiResponseEvent.TextChunk(text))
            }
        }

        val toolCallsStart = choiceJson.indexOf("\"tool_calls\"")
        if (toolCallsStart >= 0) {
            val funcStart = json.indexOf("{", toolCallsStart)
            val funcEnd = findMatchingBrace(json, funcStart)
            if (funcStart >= 0 && funcEnd >= 0) {
                val funcJson = json.substring(funcStart, funcEnd + 1)
                parseFunctionCallFromOpenAi(funcJson, onEvent)
            }
        }
    }

    private fun findChoiceEnd(json: String): Int {
        val choicesStart = json.indexOf("\"choices\"")
        if (choicesStart < 0) return -1
        val openBraces = json.indexOf('[', choicesStart)
        if (openBraces < 0) return -1
        val closeBraces = findMatchingBrace(json, openBraces)
        return closeBraces
    }

    private fun findMatchingBrace(json: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until json.length) {
            val c = json[i]
            when {
                escape -> escape = false
                c == '\\' && inString -> escape = true
                c == '"' -> inString = !inString
                !inString -> {
                    when (c) {
                        '{', '[' -> depth++
                        '}', ']' -> {
                            depth--
                            if (depth == 0) return i
                        }
                    }
                }
            }
        }
        return -1
    }

    private fun extractStringInsideQuotes(json: String, from: Int): String? {
        val q = json.indexOf('"', from)
        if (q < 0) return null
        var end = q + 1
        var escape = false
        while (end < json.length) {
            val c = json[end]
            if (escape) { escape = false; end++ }
            else if (c == '\\') escape = true
            else if (c == '"') break
            else end++
        }
        return if (end > q + 1) json.substring(q + 1, end) else null
    }

    private fun parseFunctionCallFromOpenAi(funcJson: String, onEvent: (AiResponseEvent) -> Unit) {
        val nameStart = funcJson.indexOf("\"name\"")
        if (nameStart < 0) return
        val name = extractSimpleString(funcJson, nameStart + "\"name\"".length) ?: return
        val argsStart = funcJson.indexOf("{", nameStart)
        val argsEnd = funcJson.indexOf("}", argsStart)
        if (argsStart < 0 || argsEnd < 0) return
        val argsJson = funcJson.substring(argsStart + 1, argsEnd)
        val args = parseFunctionArgs(argsJson)
        onEvent(AiResponseEvent.ToolCall(name, args))
    }

    private fun String.escapeJson(): String =
        this.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
