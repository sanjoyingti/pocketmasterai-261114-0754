package com.aibuilt.pocketmasterai.data.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_provider_settings")

class AiProviderRepository(private val context: Context) {

    fun getSelectedProvider(): Flow<String> =
        context.dataStore.data.map { it[KEY_AI_PROVIDER] ?: "google" }

    fun getGeminiApiKey(): Flow<String?> =
        context.dataStore.data.map { it[KEY_GEMINI_API_KEY] }

    fun getOauthToken(): Flow<String?> =
        context.dataStore.data.map { it[KEY_OAUTH_TOKEN] }

    fun getOpenAiApiKey(): Flow<String?> =
        context.dataStore.data.map { it[KEY_OPENAI_API_KEY] }

    fun getOpenAiBaseUrl(): Flow<String?> =
        context.dataStore.data.map { it[KEY_OPENAI_BASE_URL] }

    fun getSelectedModel(): Flow<String?> =
        context.dataStore.data.map { it[KEY_SELECTED_MODEL] }

    suspend fun setSelectedProvider(provider: String) {
        context.dataStore.edit { it[KEY_AI_PROVIDER] = provider }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { it[KEY_GEMINI_API_KEY] = key }
    }

    suspend fun setOauthToken(token: String) {
        context.dataStore.edit { it[KEY_OAUTH_TOKEN] = token }
    }

    suspend fun setOpenAiApiKey(key: String) {
        context.dataStore.edit { it[KEY_OPENAI_API_KEY] = key }
    }

    suspend fun setOpenAiBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_OPENAI_BASE_URL] = url }
    }

    suspend fun setSelectedModel(model: String) {
        context.dataStore.edit { it[KEY_SELECTED_MODEL] = model }
    }

    companion object {
        private val KEY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_OAUTH_TOKEN = stringPreferencesKey("oauth_token")
        private val KEY_OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        private val KEY_OPENAI_BASE_URL = stringPreferencesKey("openai_base_url")
        private val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
    }
}
