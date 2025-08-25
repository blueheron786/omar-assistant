package com.omar.assistant.speech

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Speech-to-Text manager using Android's built-in SpeechRecognizer
 * Converts audio to text for processing by the LLM
 */
class SpeechToTextManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SpeechToTextManager"
        private const val MICROPHONE_CHECK_TIMEOUT_MS = 2000L
        private const val MICROPHONE_CHECK_INTERVAL_MS = 100L
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    /**
     * Waits for microphone to be available for speech recognition
     */
    private suspend fun waitForMicrophoneAvailability(): Boolean = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < MICROPHONE_CHECK_TIMEOUT_MS) {
            try {
                // Check if microphone is being used by checking audio focus or mode
                val audioMode = audioManager.mode
                val isMicrophoneInUse = audioMode == AudioManager.MODE_IN_CALL || 
                                       audioMode == AudioManager.MODE_IN_COMMUNICATION
                
                if (!isMicrophoneInUse) {
                    Log.d(TAG, "Microphone appears to be available (mode: $audioMode)")
                    // Add small delay to ensure it's really available
                    delay(200)
                    return@withContext true
                }
                
                Log.d(TAG, "Microphone may be in use (mode: $audioMode), waiting...")
                delay(MICROPHONE_CHECK_INTERVAL_MS)
                
            } catch (e: Exception) {
                Log.w(TAG, "Error checking microphone availability", e)
                delay(MICROPHONE_CHECK_INTERVAL_MS)
            }
        }
        
        Log.w(TAG, "Microphone availability check timed out")
        return@withContext true // Proceed anyway after timeout
    }
    
    /**
     * Converts recorded audio to text using live microphone input
     * Waits for microphone availability before starting recognition
     */
    suspend fun transcribeAudio(): String? = suspendCancellableCoroutine { continuation ->
        var lastPartialResult: String? = null
        var retryCount = 0
        val maxRetries = 2
        var recognitionStarted = false
        var customTimeoutJob: Job? = null
        
        suspend fun attemptRecognition() {
            try {
                // Wait for microphone to be available
                Log.d(TAG, "Checking microphone availability...")
                if (!waitForMicrophoneAvailability()) {
                    Log.w(TAG, "Microphone availability check failed, proceeding anyway")
                }
                
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.e(TAG, "Speech recognition not available")
                    continuation.resume(null)
                    return
                }
                
                // Ensure any previous recognizer is cleaned up
                cleanup()
                
                Log.d(TAG, "Creating SpeechRecognizer...")
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                
                val recognitionListener = object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Ready for speech - recognition started")
                        recognitionStarted = true
                    }
                    
                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Beginning of speech detected")
                    }
                    
                    override fun onRmsChanged(rmsdB: Float) {
                        // Optional: Could be used for UI feedback
                        // Log.v(TAG, "Audio level: $rmsdB dB")
                    }
                    
                    override fun onBufferReceived(buffer: ByteArray?) {
                        // Not used in this implementation
                    }
                    
                    override fun onEndOfSpeech() {
                        Log.d(TAG, "End of speech detected")
                    }
                    
                    override fun onError(error: Int) {
                        customTimeoutJob?.cancel() // Cancel custom timeout
                        val errorMessage = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                            else -> "Unknown error (code: $error)"
                        }
                        Log.e(TAG, "Speech recognition error: $errorMessage")
                        
                        // If we have a partial result and the error is just "no match", use the partial result
                        if ((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 
                            && !lastPartialResult.isNullOrBlank()) {
                            Log.i(TAG, "Using last partial result as fallback: $lastPartialResult")
                            if (continuation.isActive) {
                                continuation.resume(lastPartialResult)
                            }
                        } else if ((error == SpeechRecognizer.ERROR_AUDIO || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 
                                   && retryCount < maxRetries) {
                            // Retry for audio or busy errors
                            Log.w(TAG, "Retrying speech recognition (attempt ${retryCount + 1})")
                            retryCount++
                            cleanup()
                            // Use a simple delay and retry in coroutine scope
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(500)
                                attemptRecognition()
                            }
                            return // Exit current attempt
                        } else {
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                        cleanup()
                    }
                    
                    override fun onResults(results: Bundle?) {
                        customTimeoutJob?.cancel() // Cancel custom timeout
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val transcription = matches?.firstOrNull()
                        
                        Log.d(TAG, "Speech recognition results: $transcription")
                        Log.d(TAG, "All results: $matches")
                        
                        if (continuation.isActive) {
                            // Use the transcription if available, otherwise fall back to last partial result
                            val finalResult = transcription ?: lastPartialResult
                            continuation.resume(finalResult)
                        }
                        cleanup()
                    }
                    
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partialText = matches?.firstOrNull()
                        if (!partialText.isNullOrBlank()) {
                            lastPartialResult = partialText // Store for potential fallback
                        }
                        Log.d(TAG, "Partial results: $partialText")
                    }
                    
                    override fun onEvent(eventType: Int, params: Bundle?) {
                        Log.d(TAG, "Speech recognition event: $eventType")
                    }
                }
                
                speechRecognizer?.setRecognitionListener(recognitionListener)
                
                // Start custom timeout that's longer than system timeout
                customTimeoutJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(10000) // Wait 10 seconds before giving up
                    if (continuation.isActive && !recognitionStarted) {
                        Log.w(TAG, "Custom timeout: Speech recognition never started")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                        cleanup()
                    } else if (continuation.isActive) {
                        Log.i(TAG, "Custom timeout: Using partial result if available")
                        if (!lastPartialResult.isNullOrBlank()) {
                            Log.i(TAG, "Using partial result from custom timeout: $lastPartialResult")
                            if (continuation.isActive) {
                                continuation.resume(lastPartialResult)
                            }
                        } else {
                            Log.w(TAG, "No partial results available from custom timeout")
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                        cleanup()
                    }
                }
                
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    
                    // Extended timeout parameters - try multiple variations that different engines might respect
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L) // 8 seconds
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L) // 6 seconds
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L) // Minimum speech
                    
                    // Additional timeout parameters that some engines might respect
                    putExtra("android.speech.extra.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 8000L)
                    putExtra("android.speech.extra.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 6000L)
                    putExtra("endpointer_silence_timeout", 8000L)
                    
                    // Try to force longer listening period
                    putExtra("listening_timeout", 10000L) // 10 seconds total
                    putExtra("speech_timeout", 8000L)
                    
                    // Keep online for better accuracy but add confidence parameters
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                    putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true)
                }
                
                speechRecognizer?.startListening(intent)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up speech recognition", e)
                if (continuation.isActive) {
                    continuation.resume(null)
                }
                cleanup()
            }
        }
        
        // Start the initial recognition attempt in coroutine scope
        CoroutineScope(Dispatchers.Main).launch {
            attemptRecognition()
        }
        
        // Set up cancellation
        continuation.invokeOnCancellation {
            customTimeoutJob?.cancel()
            cleanup()
        }
    }
    
    /**
     * Converts error codes to human-readable strings
     */
    private fun getErrorString(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error" 
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown error (code: $error)"
        }
    }

    /**
     * Converts pre-recorded audio data to text
     * Note: Android's SpeechRecognizer doesn't directly support raw audio input
     * This method would need a cloud-based STT service for implementation
     */
    suspend fun transcribeAudioData(audioData: ShortArray): String? {
        // For raw audio data, we would need to use a cloud service like:
        // - Google Speech-to-Text API
        // - Azure Speech Services
        // - AWS Transcribe
        // 
        // For now, we'll use the microphone-based approach
        Log.w(TAG, "Raw audio transcription not implemented, using microphone input")
        return transcribeAudio()
    }
    
    /**
     * Cleans up speech recognizer resources
     */
    private fun cleanup() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up speech recognizer", e)
        }
    }
    
    /**
     * Stops current speech recognition
     */
    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition", e)
        }
    }
    
    /**
     * Releases all resources
     */
    fun release() {
        cleanup()
    }
}
