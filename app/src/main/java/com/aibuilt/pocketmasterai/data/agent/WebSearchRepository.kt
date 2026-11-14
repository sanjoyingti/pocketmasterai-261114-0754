package com.aibuilt.pocketmasterai.data.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

class WebSearchRepository(
    private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    private val searchService = WebSearchService(context)

    suspend fun search(query: String): List<WebSearchResult> {
        val source = dataStore.data.map { it[KEY_SEARCH_SOURCE] }.first()
        return when (source) {
            "google" -> searchService.searchGoogle(query, "")
            else -> searchService.searchDuckDuckGo(query)
        }
    }

    suspend fun fetchPage(url: String): String {
        return searchService.fetchPage(url)
    }

    suspend fun setSearchSource(source: String) {
        dataStore.edit {
            it[KEY_SEARCH_SOURCE] = source
        }
    }

    companion object {
        private val KEY_SEARCH_SOURCE = stringPreferencesKey("web_search_source")
    }
}

data class WebSearchResult(
    val title: String,
    val snippet: String,
    val url: String
)
