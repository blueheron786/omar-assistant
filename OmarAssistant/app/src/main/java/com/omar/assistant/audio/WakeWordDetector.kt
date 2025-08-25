package com.omar.assistant.audio

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Wake word detection using Porcupine
 * Listens for "omer" and "3umar" wake words
 */
class WakeWordDetector(
    private val context: Context,
    private val audioManager: AudioManager
) {
    
    companion object {
        private const val TAG = "WakeWordDetector"
        // These would be the actual keyword files from Porcupine Console
        // For now, we'll use built-in keywords as placeholders
        private val WAKE_WORDS = arrayOf("picovoice", "computer") // Placeholders for "omer" and "3umar"
    }
    
    private var porcupine: Porcupine? = null
    private var isListening = false
    private var detectionJob: Job? = null
    
    private val _wakeWordDetected = MutableSharedFlow<WakeWordResult>()
    val wakeWordDetected: SharedFlow<WakeWordResult> = _wakeWordDetected.asSharedFlow()
    
    /**
     * Initializes Porcupine with wake words
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // In a real implementation, you would download custom keyword files
            // from Porcupine Console for "omer" and "3umar"
            val porcupineBuilder = Porcupine.Builder()
                .setAccessKey("YOUR_PICOVOICE_ACCESS_KEY") // This should be in secure storage
                .setKeywords(arrayOf(Porcupine.BuiltInKeyword.PICOVOICE, Porcupine.BuiltInKeyword.COMPUTER))
                .setSensitivities(floatArrayOf(0.5f, 0.5f))
            
            porcupine = porcupineBuilder.build(context)
            Log.d(TAG, "Porcupine initialized successfully")
            true
        } catch (e: PorcupineException) {
            Log.e(TAG, "Failed to initialize Porcupine", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error initializing Porcupine", e)
            false
        }
    }
    
    /**
     * Starts listening for wake words
     */
    fun startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening for wake words")
            return
        }
        
        if (porcupine == null) {
            Log.e(TAG, "Porcupine not initialized")
            return
        }
        
        isListening = true
        detectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                audioManager.startRecording().collect { audioData ->
                    if (!isListening) return@collect
                    
                    // Porcupine expects specific frame length
                    val frameLength = porcupine?.frameLength ?: 512
                    
                    // Process audio in chunks of the required frame length
                    for (i in audioData.indices step frameLength) {
                        if (!isListening) break
                        
                        val endIndex = minOf(i + frameLength, audioData.size)
                        if (endIndex - i < frameLength) break
                        
                        val frame = audioData.sliceArray(i until endIndex)
                        
                        try {
                            val keywordIndex = porcupine?.process(frame) ?: -1
                            if (keywordIndex >= 0) {
                                val detectedWord = when (keywordIndex) {
                                    0 -> "omer" // Maps to PICOVOICE placeholder
                                    1 -> "3umar" // Maps to COMPUTER placeholder
                                    else -> "unknown"
                                }
                                
                                Log.d(TAG, "Wake word detected: $detectedWord")
                                _wakeWordDetected.emit(
                                    WakeWordResult(
                                        keyword = detectedWord,
                                        index = keywordIndex,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )
                            }
                        } catch (e: PorcupineException) {
                            Log.e(TAG, "Error processing audio frame", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in wake word detection", e)
            }
        }
        
        Log.d(TAG, "Started listening for wake words")
    }
    
    /**
     * Stops listening for wake words
     */
    fun stopListening() {
        isListening = false
        detectionJob?.cancel()
        Log.d(TAG, "Stopped listening for wake words")
    }
    
    /**
     * Releases Porcupine resources
     */
    fun release() {
        stopListening()
        try {
            porcupine?.delete()
            porcupine = null
            Log.d(TAG, "Porcupine resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Porcupine", e)
        }
    }
    
    /**
     * Returns true if currently listening
     */
    fun isListening(): Boolean = isListening
    
    /**
     * Alternative simple wake word detection for testing
     * Uses energy-based detection when Porcupine is not available
     */
    fun startSimpleWakeWordDetection() {
        if (isListening) return
        
        isListening = true
        detectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                var silenceCounter = 0
                val silenceThreshold = 50 // Frames of silence before considering "wake word"
                
                audioManager.startRecording().collect { audioData ->
                    if (!isListening) return@collect
                    
                    // Simple energy-based detection (placeholder)
                    val energy = audioData.sumOf { it.toDouble() * it.toDouble() } / audioData.size
                    
                    if (energy < 1000) { // Silence
                        silenceCounter++
                    } else { // Sound detected
                        if (silenceCounter > silenceThreshold) {
                            // Simulate wake word detection after silence
                            Log.d(TAG, "Simple wake word detected (energy-based)")
                            _wakeWordDetected.emit(
                                WakeWordResult(
                                    keyword = "omer",
                                    index = 0,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                        silenceCounter = 0
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in simple wake word detection", e)
            }
        }
        
        Log.d(TAG, "Started simple wake word detection")
    }
}

/**
 * Result of wake word detection
 */
data class WakeWordResult(
    val keyword: String,
    val index: Int,
    val timestamp: Long
)
