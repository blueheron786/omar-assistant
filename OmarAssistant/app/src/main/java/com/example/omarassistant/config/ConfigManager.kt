package com.example.omarassistant.config

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.omarassistant.model.AssistantConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secure configuration manager for OMAR Assistant
 * Handles encrypted storage of API keys and user preferences
 */
class ConfigManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: ConfigManager? = null
        
        fun getInstance(context: Context): ConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConfigManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        private const val PREFS_FILE_NAME = "omar_assistant_prefs"
        private const val KEY_WAKE_WORD = "wake_word"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_VOICE_VOLUME = "voice_volume"
        private const val KEY_WAKE_WORD_SENSITIVITY = "wake_word_sensitivity"
        private const val KEY_VAD_SENSITIVITY = "vad_sensitivity"
        private const val KEY_CONTINUOUS_LISTENING = "continuous_listening"
        private const val KEY_SMART_HOME_API_URL = "smart_home_api_url"
        private const val KEY_SMART_HOME_API_KEY = "smart_home_api_key"
        private const val KEY_FIRST_RUN = "first_run"
    }
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Load current configuration
     */
    suspend fun getConfig(): AssistantConfig = withContext(Dispatchers.IO) {
        AssistantConfig(
            wakeWord = sharedPreferences.getString(KEY_WAKE_WORD, "omar") ?: "omar",
            geminiApiKey = sharedPreferences.getString(KEY_GEMINI_API_KEY, "") ?: "",
            language = sharedPreferences.getString(KEY_LANGUAGE, "en") ?: "en",
            voiceVolume = sharedPreferences.getFloat(KEY_VOICE_VOLUME, 1.0f),
            wakeWordSensitivity = sharedPreferences.getFloat(KEY_WAKE_WORD_SENSITIVITY, 0.7f),
            vadSensitivity = sharedPreferences.getFloat(KEY_VAD_SENSITIVITY, 0.5f),
            enableContinuousListening = sharedPreferences.getBoolean(KEY_CONTINUOUS_LISTENING, true),
            smartHomeApiUrl = sharedPreferences.getString(KEY_SMART_HOME_API_URL, "") ?: "",
            smartHomeApiKey = sharedPreferences.getString(KEY_SMART_HOME_API_KEY, "") ?: ""
        )
    }
    
    /**
     * Save configuration
     */
    suspend fun saveConfig(config: AssistantConfig) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().apply {
            putString(KEY_WAKE_WORD, config.wakeWord)
            putString(KEY_GEMINI_API_KEY, config.geminiApiKey)
            putString(KEY_LANGUAGE, config.language)
            putFloat(KEY_VOICE_VOLUME, config.voiceVolume)
            putFloat(KEY_WAKE_WORD_SENSITIVITY, config.wakeWordSensitivity)
            putFloat(KEY_VAD_SENSITIVITY, config.vadSensitivity)
            putBoolean(KEY_CONTINUOUS_LISTENING, config.enableContinuousListening)
            putString(KEY_SMART_HOME_API_URL, config.smartHomeApiUrl)
            putString(KEY_SMART_HOME_API_KEY, config.smartHomeApiKey)
            apply()
        }
    }
    
    /**
     * Update specific configuration values
     */
    suspend fun updateApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putString(KEY_GEMINI_API_KEY, apiKey).apply()
    }
    
    suspend fun updateWakeWord(wakeWord: String) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().putString(KEY_WAKE_WORD, wakeWord.lowercase()).apply()
    }
    
    suspend fun updateSensitivity(wakeWordSensitivity: Float, vadSensitivity: Float) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().apply {
            putFloat(KEY_WAKE_WORD_SENSITIVITY, wakeWordSensitivity)
            putFloat(KEY_VAD_SENSITIVITY, vadSensitivity)
            apply()
        }
    }
    
    /**
     * Check if this is the first run
     */
    fun isFirstRun(): Boolean {
        val isFirst = sharedPreferences.getBoolean(KEY_FIRST_RUN, true)
        if (isFirst) {
            sharedPreferences.edit().putBoolean(KEY_FIRST_RUN, false).apply()
        }
        return isFirst
    }
    
    /**
     * Clear all stored configuration (for debugging or reset)
     */
    suspend fun clearConfig() = withContext(Dispatchers.IO) {
        sharedPreferences.edit().clear().apply()
    }
}
