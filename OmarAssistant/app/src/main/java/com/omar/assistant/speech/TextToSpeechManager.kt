package com.omar.assistant.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

/**
 * Text-to-Speech manager using Android's built-in TTS engine
 * Converts text responses to speech output
 */
class TextToSpeechManager(private val context: Context) {
    
    companion object {
        private const val TAG = "TextToSpeechManager"
        private const val UTTERANCE_ID_PREFIX = "omar_tts_"
    }
    
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var utteranceCounter = 0
    
    /**
     * Initializes the TTS engine
     */
    suspend fun initialize(): Boolean = suspendCancellableCoroutine { continuation ->
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = textToSpeech?.setLanguage(Locale.US)
                isInitialized = when (langResult) {
                    TextToSpeech.LANG_MISSING_DATA,
                    TextToSpeech.LANG_NOT_SUPPORTED -> {
                        Log.e(TAG, "Language not supported")
                        false
                    }
                    else -> {
                        Log.d(TAG, "TTS initialized successfully")
                        // Set speech rate and pitch
                        textToSpeech?.setSpeechRate(1.0f)
                        textToSpeech?.setPitch(1.0f)
                        true
                    }
                }
            } else {
                Log.e(TAG, "TTS initialization failed")
                isInitialized = false
            }
            
            if (continuation.isActive) {
                continuation.resume(isInitialized)
            }
        }
        
        continuation.invokeOnCancellation {
            if (!isInitialized) {
                textToSpeech?.shutdown()
            }
        }
    }
    
    /**
     * Speaks the given text
     * Returns true if speech was queued successfully
     */
    suspend fun speak(text: String): Boolean = suspendCancellableCoroutine { continuation ->
        if (!isInitialized || textToSpeech == null) {
            Log.e(TAG, "TTS not initialized")
            if (continuation.isActive) {
                continuation.resume(false)
            }
            return@suspendCancellableCoroutine
        }
        
        if (text.isBlank()) {
            Log.w(TAG, "Empty text provided")
            if (continuation.isActive) {
                continuation.resume(false)
            }
            return@suspendCancellableCoroutine
        }
        
        val utteranceId = "${UTTERANCE_ID_PREFIX}${++utteranceCounter}"
        
        val utteranceListener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started for utterance: $utteranceId")
            }
            
            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS completed for utterance: $utteranceId")
                if (continuation.isActive) {
                    continuation.resume(true)
                }
            }
            
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error for utterance: $utteranceId")
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
            
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS error for utterance: $utteranceId, code: $errorCode")
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }
        
        textToSpeech?.setOnUtteranceProgressListener(utteranceListener)
        
        val result = textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH, // Interrupt any current speech
            null,
            utteranceId
        )
        
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "Failed to queue TTS speech")
            if (continuation.isActive) {
                continuation.resume(false)
            }
        }
        
        continuation.invokeOnCancellation {
            stop()
        }
    }
    
    /**
     * Speaks text without waiting for completion
     */
    fun speakAsync(text: String): Boolean {
        if (!isInitialized || textToSpeech == null) {
            Log.e(TAG, "TTS not initialized")
            return false
        }
        
        if (text.isBlank()) {
            Log.w(TAG, "Empty text provided")
            return false
        }
        
        val utteranceId = "${UTTERANCE_ID_PREFIX}${++utteranceCounter}"
        
        val result = textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_ADD, // Add to queue
            null,
            utteranceId
        )
        
        return result == TextToSpeech.SUCCESS
    }
    
    /**
     * Stops current speech
     */
    fun stop() {
        try {
            textToSpeech?.stop()
            Log.d(TAG, "TTS stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }
    
    /**
     * Checks if TTS is currently speaking
     */
    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }
    
    /**
     * Sets speech rate (0.5 to 2.0, where 1.0 is normal)
     */
    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }
    
    /**
     * Sets speech pitch (0.5 to 2.0, where 1.0 is normal)
     */
    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }
    
    /**
     * Sets the language for TTS
     */
    fun setLanguage(locale: Locale): Boolean {
        val result = textToSpeech?.setLanguage(locale)
        return when (result) {
            TextToSpeech.LANG_MISSING_DATA,
            TextToSpeech.LANG_NOT_SUPPORTED -> false
            else -> true
        }
    }
    
    /**
     * Gets available languages
     */
    fun getAvailableLanguages(): Set<Locale>? {
        return textToSpeech?.availableLanguages
    }
    
    /**
     * Releases TTS resources
     */
    fun release() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            isInitialized = false
            Log.d(TAG, "TTS resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing TTS", e)
        }
    }
}
