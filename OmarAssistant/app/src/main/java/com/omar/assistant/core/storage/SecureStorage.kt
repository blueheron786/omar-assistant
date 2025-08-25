package com.omar.assistant.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage for sensitive data like API keys
 * Uses Android's EncryptedSharedPreferences for security
 */
class SecureStorage(private val context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "omar_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_SELECTED_LLM_PROVIDER = "selected_llm_provider"
        private const val KEY_WAKE_WORD_SENSITIVITY = "wake_word_sensitivity"
        private const val KEY_VAD_SENSITIVITY = "vad_sensitivity"
    }
    
    fun setGeminiApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_GEMINI_API_KEY, apiKey).apply()
    }
    
    fun getGeminiApiKey(): String? {
        return sharedPreferences.getString(KEY_GEMINI_API_KEY, null)
    }
    
    fun setSelectedLLMProvider(provider: String) {
        sharedPreferences.edit().putString(KEY_SELECTED_LLM_PROVIDER, provider).apply()
    }
    
    fun getSelectedLLMProvider(): String {
        return sharedPreferences.getString(KEY_SELECTED_LLM_PROVIDER, "gemini") ?: "gemini"
    }
    
    fun setWakeWordSensitivity(sensitivity: Float) {
        sharedPreferences.edit().putFloat(KEY_WAKE_WORD_SENSITIVITY, sensitivity).apply()
    }
    
    fun getWakeWordSensitivity(): Float {
        return sharedPreferences.getFloat(KEY_WAKE_WORD_SENSITIVITY, 0.5f)
    }
    
    fun setVADSensitivity(sensitivity: Float) {
        sharedPreferences.edit().putFloat(KEY_VAD_SENSITIVITY, sensitivity).apply()
    }
    
    fun getVADSensitivity(): Float {
        return sharedPreferences.getFloat(KEY_VAD_SENSITIVITY, 0.6f)
    }
    
    fun hasGeminiApiKey(): Boolean {
        return !getGeminiApiKey().isNullOrBlank()
    }
    
    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }
}
