package com.omar.assistant.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

/**
 * TextToSpeechProcessor - Converts text responses to speech
 * 
 * This class handles text-to-speech functionality for Omar's responses,
 * with customizable voice parameters and multi-language support.
 */
class TextToSpeechProcessor(private val context: Context) {
    
    private val TAG = "TextToSpeechProcessor"
    
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var isSpeaking = false
    
    /**
     * Initialize TTS engine
     */
    fun initialize(callback: (Boolean) -> Unit) {
        textToSpeech = TextToSpeech(context) { status ->
            when (status) {
                TextToSpeech.SUCCESS -> {
                    isInitialized = true
                    configureTTS()
                    Log.d(TAG, "TTS initialized successfully")
                    callback(true)
                }
                else -> {
                    Log.e(TAG, "TTS initialization failed with status: $status")
                    callback(false)
                }
            }
        }
    }
    
    /**
     * Configure TTS settings
     */
    private fun configureTTS() {
        textToSpeech?.apply {
            // Set language (with fallback)
            val result = setLanguage(Locale.getDefault())
            when (result) {
                TextToSpeech.LANG_MISSING_DATA,
                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    Log.w(TAG, "Language not supported, using default")
                    setLanguage(Locale.US) // Fallback to US English
                }
            }
            
            // Configure voice parameters for natural sound
            setSpeechRate(0.9f) // Slightly slower for clarity
            setPitch(1.0f) // Normal pitch
            
            // Set utterance progress listener
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS started speaking: $utteranceId")
                    this@TextToSpeechProcessor.isSpeaking = true
                }
                
                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS finished speaking: $utteranceId")
                    this@TextToSpeechProcessor.isSpeaking = false
                }
                
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error for utterance: $utteranceId")
                    this@TextToSpeechProcessor.isSpeaking = false
                }
            })
        }
    }
    
    /**
     * Speak text with default parameters
     */
    fun speak(text: String) {
        if (!isInitialized || textToSpeech == null) {
            Log.w(TAG, "TTS not initialized")
            return
        }
        
        if (text.isBlank()) {
            Log.w(TAG, "Empty text provided")
            return
        }
        
        Log.d(TAG, "Speaking: $text")
        
        val utteranceId = "omar_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }
    
    /**
     * Speak text and wait for completion
     */
    suspend fun speakAndWait(text: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            if (!isInitialized || textToSpeech == null) {
                Log.w(TAG, "TTS not initialized")
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            
            if (text.isBlank()) {
                Log.w(TAG, "Empty text provided")
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            
            val utteranceId = "omar_wait_${System.currentTimeMillis()}"
            
            // Set temporary listener for this specific utterance
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                }
                
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }
                
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            })
            
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            
            // Handle cancellation
            continuation.invokeOnCancellation {
                stop()
            }
        }
    }
    
    /**
     * Stop current speech
     */
    fun stop() {
        textToSpeech?.stop()
        isSpeaking = false
    }
    
    /**
     * Check if currently speaking
     */
    fun isSpeaking(): Boolean = isSpeaking
    
    /**
     * Set speech rate (0.5 = half speed, 2.0 = double speed)
     */
    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate.coerceIn(0.1f, 3.0f))
    }
    
    /**
     * Set pitch (0.5 = lower pitch, 2.0 = higher pitch)
     */
    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch.coerceIn(0.1f, 2.0f))
    }
    
    /**
     * Set language for speech
     */
    fun setLanguage(locale: Locale): Boolean {
        if (!isInitialized || textToSpeech == null) return false
        
        val result = textToSpeech?.setLanguage(locale)
        return when (result) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                Log.d(TAG, "Language set to: $locale")
                true
            }
            else -> {
                Log.w(TAG, "Language not supported: $locale")
                false
            }
        }
    }
    
    /**
     * Get available languages
     */
    fun getAvailableLanguages(): Set<Locale>? {
        return textToSpeech?.availableLanguages
    }
    
    /**
     * Speak with emotion/personality
     */
    fun speakWithPersonality(text: String, emotion: VoiceEmotion = VoiceEmotion.NEUTRAL) {
        if (!isInitialized) return
        
        // Adjust voice parameters based on emotion
        when (emotion) {
            VoiceEmotion.HAPPY -> {
                setSpeechRate(1.1f)
                setPitch(1.2f)
            }
            VoiceEmotion.SAD -> {
                setSpeechRate(0.8f)
                setPitch(0.9f)
            }
            VoiceEmotion.EXCITED -> {
                setSpeechRate(1.2f)
                setPitch(1.3f)
            }
            VoiceEmotion.CALM -> {
                setSpeechRate(0.9f)
                setPitch(1.0f)
            }
            VoiceEmotion.NEUTRAL -> {
                setSpeechRate(0.9f)
                setPitch(1.0f)
            }
        }
        
        speak(text)
    }
    
    /**
     * Speak command acknowledgment
     */
    fun speakAcknowledgment() {
        val acknowledgments = listOf(
            "Yes",
            "Understood",
            "Got it",
            "On it",
            "Right away"
        )
        speak(acknowledgments.random())
    }
    
    /**
     * Speak error message
     */
    fun speakError(error: String) {
        speakWithPersonality("Sorry, $error", VoiceEmotion.SAD)
    }
    
    /**
     * Add speech markup for better pronunciation
     */
    private fun addSpeechMarkup(text: String): String {
        return text
            .replace("Omar", "<prosody rate=\"medium\">Omar</prosody>")
            .replace("API", "A P I")
            .replace("IoT", "I o T")
            .replace("WiFi", "Wi Fi")
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
    }
    
    /**
     * Voice emotion enumeration
     */
    enum class VoiceEmotion {
        NEUTRAL,
        HAPPY,
        SAD,
        EXCITED,
        CALM
    }
    
    /**
     * Check if TTS is available
     */
    fun isAvailable(): Boolean {
        return isInitialized && textToSpeech != null
    }
    
    /**
     * Get TTS engine info
     */
    fun getEngineInfo(): String {
        return if (isInitialized && textToSpeech != null) {
            "TTS Engine: ${textToSpeech?.defaultEngine}, Language: ${Locale.getDefault()}"
        } else {
            "TTS not initialized"
        }
    }
}
