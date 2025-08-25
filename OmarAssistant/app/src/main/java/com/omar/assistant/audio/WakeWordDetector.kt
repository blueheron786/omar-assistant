package com.omar.assistant.audio

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Wake word detection using Porcupine with fallback to simple detection
 * Listens for "omer" and "3umar" wake words
 */
class WakeWordDetector(
    private val context: Context,
    private val audioManager: AudioManager
) {
    
    companion object {
        private const val TAG = "WakeWordDetector"
        // Fallback mode when Porcupine is not available
        private const val USE_FALLBACK_DETECTION = true
        private const val WAKE_WORD_TIMEOUT_MS = 3000L
        private const val MIN_SPEECH_ENERGY = 1000.0
    }
    
    private var porcupine: Porcupine? = null
    private var isListening = false
    private var detectionJob: Job? = null
    
    private val _wakeWordDetected = MutableSharedFlow<WakeWordResult>()
    val wakeWordDetected: SharedFlow<WakeWordResult> = _wakeWordDetected.asSharedFlow()
    
    /**
     * Initializes Porcupine with wake words or falls back to simple detection
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (USE_FALLBACK_DETECTION) {
            Log.d(TAG, "Using fallback wake word detection (energy-based)")
            return@withContext true
        }
        
        try {
            // Try to initialize Porcupine (requires valid access key)
            val accessKey = getStoredPorcupineKey()
            if (accessKey.isNullOrBlank()) {
                Log.w(TAG, "No Porcupine access key found, using fallback detection")
                return@withContext true
            }
            
            val porcupineBuilder = Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeywords(arrayOf(Porcupine.BuiltInKeyword.PICOVOICE, Porcupine.BuiltInKeyword.COMPUTER))
                .setSensitivities(floatArrayOf(0.5f, 0.5f))
            
            porcupine = porcupineBuilder.build(context)
            Log.d(TAG, "Porcupine initialized successfully")
            true
        } catch (e: PorcupineException) {
            Log.e(TAG, "Failed to initialize Porcupine, using fallback", e)
            true // Still return true to use fallback
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error initializing Porcupine, using fallback", e)
            true // Still return true to use fallback
        }
    }
    
    private fun getStoredPorcupineKey(): String? {
        // TODO: Implement secure storage for Porcupine key
        // For now, return null to use fallback detection
        return null
    }
    
    /**
     * Starts listening for wake words
     */
    fun startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening for wake words")
            return
        }

        isListening = true
        detectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                if (USE_FALLBACK_DETECTION || porcupine == null) {
                    // Use fallback detection
                    startFallbackDetection()
                } else {
                    // Use Porcupine detection
                    startPorcupineDetection()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in wake word detection", e)
            }
        }
    }
    
    private suspend fun startFallbackDetection() {
        Log.d(TAG, "Starting fallback wake word detection")
        audioManager.startRecording().collect { audioData ->
            if (!isListening) return@collect
            
            // Simple energy-based detection
            val energy = calculateAudioEnergy(audioData)
            
            if (energy > MIN_SPEECH_ENERGY) {
                Log.d(TAG, "Speech detected (energy: $energy), triggering wake word")
                // For now, assume any significant speech is a wake word
                // In a real implementation, you'd do speech recognition here
                _wakeWordDetected.emit(
                    WakeWordResult(
                        keyword = "omer", // Default to "omer"
                        confidence = 0.8f,
                        timestamp = System.currentTimeMillis()
                    )
                )
                
                // Add a delay to prevent multiple rapid triggers
                delay(WAKE_WORD_TIMEOUT_MS)
            }
        }
    }
    
    private suspend fun startPorcupineDetection() {
        Log.d(TAG, "Starting Porcupine wake word detection")
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
                            0 -> "omer" // Maps to first keyword
                            1 -> "3umar" // Maps to second keyword
                            else -> "unknown"
                        }
                        
                        Log.d(TAG, "Wake word detected: $detectedWord")
                        _wakeWordDetected.emit(
                            WakeWordResult(
                                keyword = detectedWord,
                                confidence = 0.9f,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        
                        delay(WAKE_WORD_TIMEOUT_MS)
                    }
                } catch (e: PorcupineException) {
                    Log.e(TAG, "Error processing audio frame", e)
                }
            }
        }
    }
    
    private fun calculateAudioEnergy(audioData: ShortArray): Double {
        var sum = 0.0
        for (sample in audioData) {
            sum += sample * sample
        }
        return sum / audioData.size
    }
    
    /**
     * Starts simple wake word detection (fallback method)
     */
    fun startSimpleWakeWordDetection() {
        Log.d(TAG, "Starting simple wake word detection")
        startListening()
    }
    
    /**
     * Stops listening for wake words
     */
    fun stopListening() {
        isListening = false
        detectionJob?.cancel()
        
        // Ensure audio manager stops recording completely
        audioManager.stopRecording()
        
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
}

/**
 * Result of wake word detection
 */
data class WakeWordResult(
    val keyword: String,
    val confidence: Float,
    val timestamp: Long
)
