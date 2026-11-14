package com.aibuilt.pocketmasterai.data.agent

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiProvider(
    private val credential: String,
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
                    "{\"name\":\"${t.name}\",\"description\":\"${t.description}\",\"parameters\":{\"type\":\"object\",\"properties\":{\"block\":{\"type\":\"integer\"},\"modelId\":{\"type\":\"integer\"},\"value\":{\"type\":\"integer\"},\"presetSlot\":{\"type\":\"string\"},\"query\":{\"type\":\"string\"},\"url\":{\"type\":\"string\"},\"bypassed\":{\"type\":\"boolean\"},\"slotName\":{\"type\":\"string\"},\"usbVolume\":{\"type\":\"integer\"},\"mainVolume\":{\"type\":\"integer\"},\"headphoneVolume\":{\"type\":\"integer\"}}}}"
                }}]}}]"
            } else "null"

            val body = "{\"contents\":[$contents],\"generationConfig\":{\"temperature\":0.3,\"maxOutputTokens\":4096},\"tools\":$toolsDef}"
            val url = "https://generativelanguage.googleapis.com/v1beta/models/${model.escapeUrl()}:generateContent?key=${credential.escapeUrl()}"

            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Content-Type", "application/json")
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
