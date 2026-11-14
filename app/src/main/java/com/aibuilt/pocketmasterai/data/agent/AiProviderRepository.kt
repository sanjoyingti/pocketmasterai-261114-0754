package com.aibuilt.pocketmasterai.data.agent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aibuilt.pocketmasterai.data.preference.SettingsRepository
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_provider_settings")

class AiProviderRepository(private val context: Context) {

    suspend fun getSelectedProvider(): String {
        return context.dataStore.data.map { it[KEY_AI_PROVIDER] ?: "google" }.first()
    }

    suspend fun getGeminiApiKey(): String? {
        return context.dataStore.data.map { it[KEY_GEMINI_API_KEY] }.first()
    }

    suspend fun getOauthToken(): String? {
        return context.dataStore.data.map { it[KEY_OAUTH_TOKEN] }.first()
    }

    suspend fun getOpenAiApiKey(): String? {
        return context.dataStore.data.map { it[KEY_OPENAI_API_KEY] }.first()
    }

    suspend fun getOpenAiBaseUrl(): String? {
        return context.dataStore.data.map { it[KEY_OPENAI_BASE_URL] }.first()
    }

    suspend fun getSelectedModel(): String? {
        return context.dataStore.data.map { it[KEY_SELECTED_MODEL] }.first()
    }

    suspend fun setSelectedProvider(provider: String) {
        context.dataStore.edit {
            it[KEY_AI_PROVIDER] = provider
        }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit {
            it[KEY_GEMINI_API_KEY] = key
        }
    }

    suspend fun setOauthToken(token: String) {
        context.dataStore.edit {
            it[KEY_OAUTH_TOKEN] = token
        }
    }

    suspend fun setOpenAiApiKey(key: String) {
        context.dataStore.edit {
            it[KEY_OPENAI_API_KEY] = key
        }
    }

    suspend fun setOpenAiBaseUrl(url: String) {
        context.dataStore.edit {
            it[KEY_OPENAI_BASE_URL] = url
        }
    }

    suspend fun setSelectedModel(model: String) {
        context.dataStore.edit {
            it[KEY_SELECTED_MODEL] = model
        }
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
