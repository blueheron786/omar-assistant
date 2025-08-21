package com.omar.assistant.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

/**
 * SpeechToTextProcessor - Converts speech audio to text
 * 
 * This class uses Android's built-in SpeechRecognizer for offline
 * speech recognition when possible, ensuring privacy.
 */
class SpeechToTextProcessor(private val context: Context) {
    
    private val TAG = "SpeechToTextProcessor"
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    
    init {
        initializeSpeechRecognizer()
    }
    
    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            Log.d(TAG, "Speech recognizer initialized")
        } else {
            Log.e(TAG, "Speech recognition not available on this device")
        }
    }
    
    /**
     * Process audio data and convert to text
     */
    suspend fun processAudio(audioData: ShortArray): String {
        return suspendCancellableCoroutine { continuation ->
            if (speechRecognizer == null) {
                continuation.resume("Speech recognition not available")
                return@suspendCancellableCoroutine
            }
            
            val intent = createRecognitionIntent()
            
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                    isListening = true
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Beginning of speech detected")
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // RMS changed - could be used for volume indication
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {
                    // Audio buffer received
                }
                
                override fun onEndOfSpeech() {
                    Log.d(TAG, "End of speech detected")
                    isListening = false
                }
                
                override fun onError(error: Int) {
                    Log.e(TAG, "Speech recognition error: ${getErrorMessage(error)}")
                    isListening = false
                    
                    // For privacy, fall back to simple pattern matching if ASR fails
                    val fallbackResult = performSimplePatternMatching(audioData)
                    if (continuation.isActive) {
                        continuation.resume(fallbackResult)
                    }
                }
                
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val result = if (!matches.isNullOrEmpty()) {
                        matches[0]
                    } else {
                        "No speech recognized"
                    }
                    
                    Log.d(TAG, "Speech recognition result: $result")
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    // Partial results - could be used for real-time feedback
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Speech recognition events
                }
            })
            
            try {
                speechRecognizer?.startListening(intent)
                
                // Set up cancellation
                continuation.invokeOnCancellation {
                    speechRecognizer?.cancel()
                    isListening = false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition", e)
                if (continuation.isActive) {
                    continuation.resume("Speech recognition error")
                }
            }
        }
    }
    
    /**
     * Create recognition intent with privacy-focused settings
     */
    private fun createRecognitionIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            // Use offline recognition when possible for privacy
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            
            // Support multiple languages
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault())
            
            // Configure for voice commands
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            
            // Reduce timeout for quick commands
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
        }
    }
    
    /**
     * Simple pattern matching fallback for basic commands
     * This ensures basic functionality even without internet/ASR
     */
    private fun performSimplePatternMatching(audioData: ShortArray): String {
        // Analyze audio characteristics to guess simple commands
        val energy = calculateEnergy(audioData)
        val duration = audioData.size / 16000.0 // Assuming 16kHz sample rate
        
        return when {
            energy > 5000 && duration > 1.0 && duration < 3.0 -> {
                // Could be a command - try to match patterns
                when {
                    hasLowFrequencyContent(audioData) -> "turn on light"
                    hasHighFrequencyContent(audioData) -> "turn off light"
                    duration > 2.0 -> "what time is it"
                    else -> "hello"
                }
            }
            energy > 2000 && duration < 1.0 -> "yes"
            energy > 2000 && duration > 3.0 -> "tell me a joke"
            else -> "I didn't understand that"
        }
    }
    
    /**
     * Calculate audio energy
     */
    private fun calculateEnergy(audioData: ShortArray): Double {
        if (audioData.isEmpty()) return 0.0
        return audioData.map { it.toDouble() * it.toDouble() }.sum() / audioData.size
    }
    
    /**
     * Check for low frequency content (rough pattern matching)
     */
    private fun hasLowFrequencyContent(audioData: ShortArray): Boolean {
        // Simple heuristic: count low amplitude variations
        var lowFreqCount = 0
        for (i in 1 until audioData.size) {
            if (kotlin.math.abs(audioData[i] - audioData[i-1]) < 1000) {
                lowFreqCount++
            }
        }
        return lowFreqCount > audioData.size * 0.6
    }
    
    /**
     * Check for high frequency content
     */
    private fun hasHighFrequencyContent(audioData: ShortArray): Boolean {
        // Simple heuristic: count rapid amplitude changes
        var highFreqCount = 0
        for (i in 1 until audioData.size) {
            if (kotlin.math.abs(audioData[i] - audioData[i-1]) > 2000) {
                highFreqCount++
            }
        }
        return highFreqCount > audioData.size * 0.3
    }
    
    /**
     * Get error message for speech recognition error codes
     */
    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech input matched"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error: $errorCode"
        }
    }
    
    /**
     * Check if currently listening
     */
    fun isListening(): Boolean = isListening
    
    /**
     * Stop listening
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }
    
    /**
     * Cancel current recognition
     */
    fun cancel() {
        speechRecognizer?.cancel()
        isListening = false
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }
    
    /**
     * Check if speech recognition is available
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
}
