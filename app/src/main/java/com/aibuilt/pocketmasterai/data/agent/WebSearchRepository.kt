package com.aibuilt.pocketmasterai.data.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aibuilt.pocketmasterai.data.preference.PrefKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "websearch_settings")

class WebSearchRepository(
    private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    private val searchService = WebSearchService(context)

    suspend fun search(query: String): List<WebSearchResult> {
        val source = dataStore.data.map { it[WebSearchRepository.KEY_SEARCH_SOURCE] }.first()
        val cseKey = dataStore.data.map { it[WebSearchRepository.KEY_CSE_ENGINE_ID] }.first()
        return when (source) {
            "google" -> searchService.searchGoogle(query, cseKey ?: "")
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

    suspend fun setGoogleCseKey(key: String) {
        dataStore.edit {
            it[KEY_CSE_ENGINE_ID] = key
        }
    }

    companion object {
        private val KEY_SEARCH_SOURCE = stringPreferencesKey("web_search_source")
        private val KEY_CSE_ENGINE_ID = stringPreferencesKey("google_cse_engine_id")
    }
}

data class WebSearchResult(
    val title: String,
    val snippet: String,
    val url: String
)
