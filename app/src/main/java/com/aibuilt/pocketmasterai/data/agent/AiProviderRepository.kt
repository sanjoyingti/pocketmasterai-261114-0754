package com.aibuilt.pocketmasterai.data.agent

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aibuilt.pocketmasterai.data.preference.SettingsRepository

class AiProviderRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "ai_provider_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val settingsRepository = SettingsRepository(context, context.dataStore)

    suspend fun setGoogleSignedIn(token: String) {
        securePrefs.edit().putString(KEY_OAUTH_TOKEN, token).apply()
    }

    suspend fun getOauthToken(): String? = securePrefs.getString(KEY_OAUTH_TOKEN, null)

    suspend fun clearOAuth() {
        securePrefs.edit().remove(KEY_OAUTH_TOKEN).apply()
    }

    suspend fun selectProvider(provider: String) {
        settingsRepository.setAiProvider(provider)
    }

    suspend fun getSelectedProvider(): String = settingsRepository.getAiProvider().first()

    suspend fun setGeminiApiKey(key: String) {
        settingsRepository.setGeminiApiKey(key)
    }

    suspend fun getGeminiApiKey(): String? = settingsRepository.getGeminiApiKey().first()

    suspend fun setOpenAiApiKey(key: String) {
        settingsRepository.setOpenAiApiKey(key)
    }

    suspend fun getOpenAiApiKey(): String? = settingsRepository.getOpenAiApiKey().first()

    suspend fun setOpenAiBaseUrl(url: String) {
        settingsRepository.setOpenAiBaseUrl(url)
    }

    suspend fun getOpenAiBaseUrl(): String? = settingsRepository.getOpenAiBaseUrl().first()

    suspend fun setSelectedModel(model: String) {
        settingsRepository.setSelectedModel(model)
    }

    suspend fun getSelectedModel(): String? = settingsRepository.getSelectedModel().first()

    fun createProvider(): AiProvider {
        val provider = getSelectedProvider()
        val model = getSelectedModel() ?: DEFAULT_MODEL
        return when (provider) {
            "google" -> {
                val key = getGeminiApiKey()
                val token = getOauthToken()
                if (token != null) GeminiProvider(key ?: "", token, model)
                else GeminiProvider(key ?: DEFAULT_GEMINI_KEY, token, model)
            }
            "openai_compat" -> {
                val key = getOpenAiApiKey() ?: error("OpenAI-compatible API key not configured")
                val url = getOpenAiBaseUrl() ?: "https://api.deepseek.com/v1"
                OpenAiCompatProvider(key, url, model)
            }
            else -> GeminiProvider(DEFAULT_GEMINI_KEY, getOauthToken(), model)
        }
    }

    companion object {
        private const val KEY_OAUTH_TOKEN = "google_oauth_token"
        const val DEFAULT_GEMINI_KEY = "AIzaSyDummyPlaceholder"
        const val DEFAULT_MODEL = "gemini-2.0-flash"
        const val MODEL_GEMINI_PRO = "gemini-2.0-pro"
        const val MODEL_GEMINI_FLASH = "gemini-2.0-flash"
        const val MODEL_DEEPSEEK_CHAT = "deepseek-chat"
        const val MODEL_DEEPSEEK_REASONER = "deepseek-reasoner"
        const val MODEL_DEEPSEEK_FLASH = "deepseek-flash"
    }
}
