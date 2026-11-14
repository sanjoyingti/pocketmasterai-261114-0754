package com.aibuilt.pocketmasterai.data.preference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsRepository(
    private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        private val KEY_OPENAI_BASE_URL = stringPreferencesKey("openai_base_url")
        private val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        private val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        private val KEY_OAUTH_TOKEN = stringPreferencesKey("oauth_token")
        private val KEY_SEARCH_SOURCE = stringPreferencesKey("search_source")
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    fun getGeminiApiKey(): Flow<String?> = dataStore.data.map { it[KEY_GEMINI_API_KEY] }
    fun getOpenAiApiKey(): Flow<String?> = dataStore.data.map { it[KEY_OPENAI_API_KEY] }
    fun getOpenAiBaseUrl(): Flow<String?> = dataStore.data.map { it[KEY_OPENAI_BASE_URL] }
    fun getSelectedModel(): Flow<String?> = dataStore.data.map { it[KEY_SELECTED_MODEL] }
    fun getAiProvider(): Flow<String> = dataStore.data.map {
        it[KEY_AI_PROVIDER] ?: "google"
    }
    fun getOauthToken(): Flow<String?> = dataStore.data.map { it[KEY_OAUTH_TOKEN] }
    fun getSearchSource(): Flow<String> = dataStore.data.map {
        it[KEY_SEARCH_SOURCE] ?: "duckduckgo"
    }

    suspend fun setGeminiApiKey(key: String) {
        dataStore.edit { it[KEY_GEMINI_API_KEY] = key }
    }

    suspend fun setOpenAiApiKey(key: String) {
        dataStore.edit { it[KEY_OPENAI_API_KEY] = key }
    }

    suspend fun setOpenAiBaseUrl(url: String) {
        dataStore.edit { it[KEY_OPENAI_BASE_URL] = url }
    }

    suspend fun setSelectedModel(model: String) {
        dataStore.edit { it[KEY_SELECTED_MODEL] = model }
    }

    suspend fun setAiProvider(provider: String) {
        dataStore.edit { it[KEY_AI_PROVIDER] = provider }
    }

    suspend fun setOauthToken(token: String) {
        dataStore.edit { it[KEY_OAUTH_TOKEN] = token }
    }

    suspend fun setSearchSource(source: String) {
        dataStore.edit { it[KEY_SEARCH_SOURCE] = source }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
