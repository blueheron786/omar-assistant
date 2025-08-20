package com.example.omarassistant.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import java.util.*

/**
 * Text-to-Speech engine for OMAR assistant
 * Handles voice output with customizable settings and audio feedback
 */
class TextToSpeechEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "TTSEngine"
        private const val UTTERANCE_ID_PREFIX = "omar_utterance_"
    }
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentVolume = 1.0f
    private var currentLanguage = Locale.US
    private var utteranceCounter = 0
    
    // Tone generator for beeps and sound effects
    private var toneGenerator: ToneGenerator? = null
    
    // Callbacks
    private val utteranceCallbacks = mutableMapOf<String, () -> Unit>()
    
    /**
     * Initialize the TTS engine
     */
    suspend fun initialize() = withContext(Dispatchers.Main) {
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    setupTTS()
                    isInitialized = true
                    Log.d(TAG, "TTS initialized successfully")
                } else {
                    Log.e(TAG, "TTS initialization failed with status: $status")
                }
            }
            
            // Initialize tone generator for audio feedback
            initializeToneGenerator()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TTS", e)
        }
    }
    
    /**
     * Setup TTS parameters
     */
    private fun setupTTS() {
        tts?.let { ttsEngine ->
            try {
                // Set language
                val result = ttsEngine.setLanguage(currentLanguage)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Language not supported, using default")
                    ttsEngine.setLanguage(Locale.US)
                }
                
                // Set speech rate and pitch
                ttsEngine.setSpeechRate(1.0f) // Normal speed
                ttsEngine.setPitch(1.0f) // Normal pitch
                
                // Set up utterance progress listener
                ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "TTS started for utterance: $utteranceId")
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "TTS completed for utterance: $utteranceId")
                        utteranceId?.let { id ->
                            utteranceCallbacks[id]?.invoke()
                            utteranceCallbacks.remove(id)
                        }
                    }
                    
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS error for utterance: $utteranceId")
                        utteranceId?.let { id ->
                            utteranceCallbacks[id]?.invoke()
                            utteranceCallbacks.remove(id)
                        }
                    }
                })
                
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up TTS", e)
            }
        }
    }
    
    /**
     * Initialize tone generator for audio feedback
     */
    private fun initializeToneGenerator() {
        try {
            toneGenerator = ToneGenerator(
                android.media.AudioManager.STREAM_NOTIFICATION,
                80 // Volume percentage
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing tone generator", e)
        }
    }
    
    /**
     * Speak text with optional completion callback
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS not initialized, cannot speak: $text")
            onComplete?.invoke()
            return
        }
        
        if (text.isBlank()) {
            onComplete?.invoke()
            return
        }
        
        try {
            val utteranceId = "$UTTERANCE_ID_PREFIX${++utteranceCounter}"
            
            // Store callback if provided
            onComplete?.let { callback ->
                utteranceCallbacks[utteranceId] = callback
            }
            
            // Create speech parameters
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, currentVolume)
            }
            
            // Speak the text
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            
            if (result != TextToSpeech.SUCCESS) {
                Log.e(TAG, "Failed to queue text for speaking: $text")
                onComplete?.invoke()
                utteranceCallbacks.remove(utteranceId)
            } else {
                Log.d(TAG, "Speaking: $text")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking text: $text", e)
            onComplete?.invoke()
        }
    }
    
    /**
     * Play a beep sound for audio feedback
     */
    fun playBeep(type: BeepType = BeepType.WAKE_WORD) {
        try {
            toneGenerator?.let { generator ->
                when (type) {
                    BeepType.WAKE_WORD -> {
                        // Two quick beeps for wake word detection
                        generator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                        CoroutineScope(Dispatchers.IO).launch {
                            delay(200)
                            generator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                        }
                    }
                    BeepType.COMMAND_START -> {
                        // Single beep for command recording start
                        generator.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                    }
                    BeepType.COMMAND_END -> {
                        // Lower tone for command recording end
                        generator.startTone(ToneGenerator.TONE_PROP_PROMPT, 200)
                    }
                    BeepType.ERROR -> {
                        // Error tone
                        generator.startTone(ToneGenerator.TONE_PROP_NACK, 300)
                    }
                    BeepType.SUCCESS -> {
                        // Success tone
                        generator.startTone(ToneGenerator.TONE_PROP_ACK, 200)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing beep", e)
        }
    }
    
    /**
     * Stop current speech
     */
    fun stop() {
        try {
            tts?.stop()
            utteranceCallbacks.clear()
            Log.d(TAG, "TTS stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }
    
    /**
     * Set speech volume
     */
    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        Log.d(TAG, "TTS volume set to: $currentVolume")
    }
    
    /**
     * Set speech language
     */
    fun setLanguage(language: String) {
        try {
            val locale = when (language.lowercase()) {
                "en" -> Locale.US
                "ar" -> Locale("ar")
                "es" -> Locale("es")
                "fr" -> Locale.FRENCH
                "de" -> Locale.GERMAN
                else -> Locale.US
            }
            
            currentLanguage = locale
            
            if (isInitialized) {
                val result = tts?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Language $language not supported, using English")
                    tts?.setLanguage(Locale.US)
                } else {
                    Log.d(TAG, "Language set to: $language")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting language to: $language", e)
        }
    }
    
    /**
     * Set speech rate
     */
    fun setSpeechRate(rate: Float) {
        try {
            val clampedRate = rate.coerceIn(0.5f, 2.0f)
            tts?.setSpeechRate(clampedRate)
            Log.d(TAG, "Speech rate set to: $clampedRate")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting speech rate", e)
        }
    }
    
    /**
     * Set speech pitch
     */
    fun setPitch(pitch: Float) {
        try {
            val clampedPitch = pitch.coerceIn(0.5f, 2.0f)
            tts?.setPitch(clampedPitch)
            Log.d(TAG, "Speech pitch set to: $clampedPitch")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting speech pitch", e)
        }
    }
    
    /**
     * Check if TTS is currently speaking
     */
    fun isSpeaking(): Boolean {
        return try {
            tts?.isSpeaking ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if TTS is speaking", e)
            false
        }
    }
    
    /**
     * Get available languages
     */
    suspend fun getAvailableLanguages(): Set<Locale> = withContext(Dispatchers.IO) {
        return@withContext try {
            tts?.availableLanguages ?: emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available languages", e)
            emptySet()
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        try {
            utteranceCallbacks.clear()
            tts?.stop()
            tts?.shutdown()
            toneGenerator?.release()
            toneGenerator = null
            isInitialized = false
            Log.d(TAG, "TTS engine cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up TTS", e)
        }
    }
    
    /**
     * Check if TTS is initialized
     */
    fun isReady(): Boolean = isInitialized && tts != null
}

/**
 * Types of audio feedback beeps
 */
enum class BeepType {
    WAKE_WORD,
    COMMAND_START,
    COMMAND_END,
    ERROR,
    SUCCESS
}
