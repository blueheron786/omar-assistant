package com.omar.assistant.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Speech-to-Text manager using Android's built-in SpeechRecognizer
 * Converts audio to text for processing by the LLM
 */
class SpeechToTextManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SpeechToTextManager"
    }
    
    private var speechRecognizer: SpeechRecognizer? = null
    
    /**
     * Converts recorded audio to text
     * Returns the transcribed text or null if recognition fails
     */
    suspend fun transcribeAudio(): String? = suspendCancellableCoroutine { continuation ->
        var lastPartialResult: String? = null // Store last partial result as fallback
        
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.e(TAG, "Speech recognition not available")
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            
            val recognitionListener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Beginning of speech")
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // Optional: Could be used for UI feedback
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {
                    // Not used in this implementation
                }
                
                override fun onEndOfSpeech() {
                    Log.d(TAG, "End of speech")
                }
                
                override fun onError(error: Int) {
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
                    } else {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                    cleanup()
                }
                
                override fun onResults(results: Bundle?) {
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
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US") // Can be made configurable
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5) // Get more results
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Significantly increase timeout values to give more time
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000) // 5 seconds
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000) // 4 seconds
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
                // Add preference for offline recognition to avoid network issues
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false) // Keep online for better accuracy
            }
            
            speechRecognizer?.startListening(intent)
            
            // Set up cancellation
            continuation.invokeOnCancellation {
                cleanup()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up speech recognition", e)
            if (continuation.isActive) {
                continuation.resume(null)
            }
            cleanup()
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
