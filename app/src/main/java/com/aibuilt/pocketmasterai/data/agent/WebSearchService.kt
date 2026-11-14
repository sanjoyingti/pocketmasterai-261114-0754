package com.aibuilt.pocketmasterai.data.agent

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebSearchService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // DuckDuckGo instant answer / bang-free web search via their HTML search page.
    // Uses the public DDG search form (no key required), then Jsoup parses the results.
    suspend fun searchDuckDuckGo(query: String): List<WebSearchResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=$encoded")
            .get()
            .build()
        val html = client.newCall(request).execute().use { it.body?.string() } ?: return emptyList()
        val doc = Jsoup.parse(html)
        val results = mutableListOf<WebSearchResult>()
        doc.select("a.result__a").forEach { a ->
            val title = a.text().takeUnless { it.isNullOrBlank() } ?: "Search result"
            val url = a.attr("abs:href")
            val snippetElement = a.parent()?.selectFirst("a.result__snippet")
            val snippet = snippetElement?.text().orEmpty()
            if (url.isNotEmpty()) {
                results.add(WebSearchResult(title, snippet, url))
            }
        }
        return results.take(10)
    }

    // Google Custom Search: requires a CSE engine ID (user enters it in Settings).
    suspend fun searchGoogle(query: String, engineId: String): List<WebSearchResult> {
        if (engineId.isBlank()) {
            return emptyList()
        }
        val encoded = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://www.googleapis.com/customsearch/v1?key=YOUR_API_KEY&cx=$engineId&q=$encoded")
            .get()
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        response.close()
        if (response.code != 200 || body.isNullOrBlank()) {
            return emptyList()
        }
        // We do not use kotlinx-serialization here to keep imports minimal and avoid
        // needing a JSON model class; a simple Jsoup-style extraction of the parsed object.
        // Fallback to raw JSON text snippet extraction.
        return parseGoogleResults(body)
    }

    private fun parseGoogleResults(json: String): List<WebSearchResult> {
        val results = mutableListOf<WebSearchResult>()
        // Extract items array: {"title":..., "link":..., "snippet":...}
        var i = 0
        while (i < json.length) {
            val start = json.indexOf("{" , i)
            if (start < 0) break
            val titleStart = json.indexOf("\"title\"", start)
            if (titleStart < 0) { i = start + 1; continue }
            val title = extractString(json, titleStart + "\"title\"" .length)
            val linkStart = json.indexOf("\"link\"", start)
            val link = if (linkStart > start) extractString(json, linkStart + "\"link\"".length) else ""
            val snippetStart = json.indexOf("\"snippet\"", start)
            val snippet = if (snippetStart > start) extractString(json, snippetStart + "\"snippet\"".length) else ""
            if (title.isNullOrBlank().not() || link.isNotEmpty()) {
                results.add(WebSearchResult(title ?: "Search result", snippet, link))
            }
            i = start + 1
        }
        return results.take(10)
    }

    private fun extractString(json: String, from: Int): String? {
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

    suspend fun fetchPage(url: String): String {
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        response.close()
        return if (body.isNullOrBlank()) "" else Jsoup.parse(body).text().replace(Regex("\\s+"), " ")
    }
}
