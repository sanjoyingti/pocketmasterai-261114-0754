package com.aibuilt.pocketmasterai.data.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_provider_settings")

class AiProviderRepository(context: Context) {
    private val dataStore: DataStore<Preferences> = context.aiDataStore

    companion object {
        private val KEY_PROVIDER = stringPreferencesKey("ai_provider")
        private val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        private val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val KEY_OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        private val KEY_OPENAI_BASE_URL = stringPreferencesKey("openai_base_url")
        private val KEY_OAUTH_TOKEN = stringPreferencesKey("oauth_token")
    }

    val providers = listOf(
        AiProviderChoice("google", "Google Gemini (Sign-In)", "Google Gemini via OAuth 2.0 / API key"),
        AiProviderChoice("openai", "OpenAI-Compatible (DeepSeek / OpenRouter)", "Custom endpoint with API key")
    )

    fun getProviderChoices(): List<AiProviderChoice> = providers

    fun getSelectedProvider(): Flow<String> =
        dataStore.data.map { it[KEY_PROVIDER] ?: "google" }

    fun getSelectedModel(): Flow<String?> =
        dataStore.data.map { it[KEY_SELECTED_MODEL] }

    fun getGeminiApiKey(): Flow<String?> =
        dataStore.data.map { it[KEY_GEMINI_API_KEY] }

    fun getOpenAiApiKey(): Flow<String?> =
        dataStore.data.map { it[KEY_OPENAI_API_KEY] }

    fun getOpenAiBaseUrl(): Flow<String?> =
        dataStore.data.map { it[KEY_OPENAI_BASE_URL] }

    fun getOauthToken(): Flow<String?> =
        dataStore.data.map { it[KEY_OAUTH_TOKEN] }

    suspend fun selectProvider(provider: String) {
        dataStore.edit { it[KEY_PROVIDER] = provider }
    }

    suspend fun setSelectedModel(model: String) {
        dataStore.edit { it[KEY_SELECTED_MODEL] = model }
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

    suspend fun setOauthToken(token: String) {
        dataStore.edit { it[KEY_OAUTH_TOKEN] = token }
    }
}

data class AiProviderChoice(
    val key: String,
    val displayName: String,
    val description: String
)
