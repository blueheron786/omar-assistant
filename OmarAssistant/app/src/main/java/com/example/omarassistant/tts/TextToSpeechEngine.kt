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
    private var currentLanguage = Locale.UK
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
                
                // Prefer a British male voice if available, with sensible fallbacks
                val britishSelected = selectBritishMaleVoice(ttsEngine)
                if (!britishSelected) {
                    // Fallback to any English male voice or lower-pitch English
                    selectMaleVoice(ttsEngine)
                }
                
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
     * Try to select a male voice from available voices
     * @return true if a male voice was successfully set, false otherwise
     */
    private fun selectMaleVoice(ttsEngine: TextToSpeech): Boolean {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val voices = ttsEngine.voices
                
                // Look for male voices in English
                val maleVoice = voices?.find { voice ->
                    voice.locale.language == "en" && 
                    (voice.name.contains("male", ignoreCase = true) || 
                     voice.name.contains("man", ignoreCase = true) ||
                     voice.name.contains("boy", ignoreCase = true))
                }
                
                if (maleVoice != null) {
                    val result = ttsEngine.setVoice(maleVoice)
                    if (result == TextToSpeech.SUCCESS) {
                        Log.d(TAG, "Successfully set male voice: ${maleVoice.name}")
                        return true
                    } else {
                        Log.w(TAG, "Failed to set male voice: ${maleVoice.name}")
                    }
                } else {
                    // If no specifically male voice found, try to find any English voice and lower pitch
                    val englishVoice = voices?.find { voice ->
                        voice.locale.language == "en"
                    }
                    
                    if (englishVoice != null) {
                        ttsEngine.setVoice(englishVoice)
                        return true
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting male voice", e)
        }
        
        return false
    }

    /**
     * Try to select a British (en-GB) male voice if available.
     * Falls back to any en-GB voice (with slightly lower pitch) if a clearly male voice isn't found.
     */
    private fun selectBritishMaleVoice(ttsEngine: TextToSpeech): Boolean {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val voices = ttsEngine.voices ?: return false

                val isEnGb: (Locale) -> Boolean = { loc ->
                    (loc.language.equals("en", true) && loc.country.equals("GB", true)) ||
                    loc.toString().equals("en_GB", true)
                }

                val enGbVoices = voices.filter { v -> isEnGb(v.locale) }
                if (enGbVoices.isEmpty()) return false

                // Prefer those that indicate male in the name
                val maleEnGb = enGbVoices.firstOrNull { v ->
                    v.name.contains("male", ignoreCase = true) ||
                    v.name.contains("_m", ignoreCase = true) ||
                    v.name.contains("-m", ignoreCase = true) ||
                    v.name.contains("#male", ignoreCase = true)
                }

                val chosen = maleEnGb ?: enGbVoices.first()
                val result = ttsEngine.setVoice(chosen)
                if (result == TextToSpeech.SUCCESS) {
                    if (maleEnGb == null) {
                        // If it's not explicitly male, nudge pitch slightly lower
                        ttsEngine.setPitch(0.9f)
                        Log.d(TAG, "Set en-GB voice (not marked male), adjusted pitch: ${chosen.name}")
                    } else {
                        Log.d(TAG, "Set en-GB male voice: ${chosen.name}")
                    }
                    return true
                } else {
                    Log.w(TAG, "Failed to set en-GB voice: ${chosen.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting British male voice", e)
        }
        return false
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
                "en" -> Locale.UK
                "en-gb", "en_gb", "en-uk", "en_GB" -> Locale.UK
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
                    tts?.setLanguage(Locale.UK)
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
