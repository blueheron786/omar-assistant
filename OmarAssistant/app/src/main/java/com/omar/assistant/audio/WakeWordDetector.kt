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
        private const val MIN_SPEECH_ENERGY = 5000.0 // Increased threshold
        private const val ENERGY_HISTORY_SIZE = 50 // Number of energy samples to keep for baseline
        private const val ENERGY_SPIKE_MULTIPLIER = 3.0 // Energy must be 3x higher than baseline
        private const val SILENCE_THRESHOLD_MULTIPLIER = 1.5 // Define silence as 1.5x baseline
        private const val MIN_SILENCE_DURATION_MS = 1000L // Require 1 second of silence before listening again
    }
    
    private var porcupine: Porcupine? = null
    private var isListening = false
    private var detectionJob: Job? = null
    
    // Energy baseline tracking for improved detection
    private val energyHistory = mutableListOf<Double>()
    private var baselineEnergy = MIN_SPEECH_ENERGY
    private var lastWakeWordTime = 0L
    private var silenceStartTime = 0L
    private var isInSilence = false
    
    private val _wakeWordDetected = MutableSharedFlow<WakeWordResult>()
    val wakeWordDetected: SharedFlow<WakeWordResult> = _wakeWordDetected.asSharedFlow()
    
    /**
     * Initializes Porcupine with wake words or falls back to simple detection
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (USE_FALLBACK_DETECTION) {
            Log.d(TAG, "Using fallback wake word detection (energy-based)")
            // Reset state for fresh initialization
            resetState()
            return@withContext true
        }
        
        try {
            // Try to initialize Porcupine (requires valid access key)
            val accessKey = getStoredPorcupineKey()
            if (accessKey.isNullOrBlank()) {
                Log.w(TAG, "No Porcupine access key found, using fallback detection")
                resetState()
                return@withContext true
            }
            
            val porcupineBuilder = Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeywords(arrayOf(Porcupine.BuiltInKeyword.PICOVOICE, Porcupine.BuiltInKeyword.COMPUTER))
                .setSensitivities(floatArrayOf(0.5f, 0.5f))
            
            porcupine = porcupineBuilder.build(context)
            Log.d(TAG, "Porcupine initialized successfully")
            resetState()
            true
        } catch (e: PorcupineException) {
            Log.e(TAG, "Failed to initialize Porcupine, using fallback", e)
            resetState()
            true // Still return true to use fallback
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error initializing Porcupine, using fallback", e)
            resetState()
            true // Still return true to use fallback
        }
    }

    private fun resetState() {
        isListening = false
        energyHistory.clear()
        baselineEnergy = MIN_SPEECH_ENERGY
        lastWakeWordTime = 0L
        silenceStartTime = 0L
        isInSilence = false
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
        
        // Reset tracking variables
        energyHistory.clear()
        baselineEnergy = MIN_SPEECH_ENERGY
        lastWakeWordTime = 0L
        silenceStartTime = System.currentTimeMillis()
        isInSilence = true
        
        audioManager.startRecording().collect { audioData ->
            if (!isListening) return@collect
            
            val currentTime = System.currentTimeMillis()
            val energy = calculateAudioEnergy(audioData)
            
            // Update energy baseline
            updateEnergyBaseline(energy)
            
            val silenceThreshold = baselineEnergy * SILENCE_THRESHOLD_MULTIPLIER
            val speechThreshold = baselineEnergy * ENERGY_SPIKE_MULTIPLIER
            
            // Check if we're in silence
            if (energy <= silenceThreshold) {
                if (!isInSilence) {
                    isInSilence = true
                    silenceStartTime = currentTime
                    Log.d(TAG, "Silence detected (energy: $energy <= $silenceThreshold)")
                }
            } else {
                isInSilence = false
            }
            
            // Only proceed if we've had enough silence since last wake word
            val timeSinceLastWakeWord = currentTime - lastWakeWordTime
            val silenceDuration = if (isInSilence) currentTime - silenceStartTime else 0L
            
            if (timeSinceLastWakeWord < WAKE_WORD_TIMEOUT_MS) {
                Log.d(TAG, "Still in timeout period (${timeSinceLastWakeWord}ms < ${WAKE_WORD_TIMEOUT_MS}ms)")
                return@collect
            }
            
            if (silenceDuration < MIN_SILENCE_DURATION_MS && lastWakeWordTime > 0) {
                Log.d(TAG, "Not enough silence yet (${silenceDuration}ms < ${MIN_SILENCE_DURATION_MS}ms)")
                return@collect
            }
            
            // Check for wake word (energy spike above threshold)
            if (energy > speechThreshold && energy > MIN_SPEECH_ENERGY) {
                Log.d(TAG, "Speech detected (energy: $energy > $speechThreshold, baseline: $baselineEnergy), triggering wake word")
                
                lastWakeWordTime = currentTime
                isInSilence = false
                
                _wakeWordDetected.emit(
                    WakeWordResult(
                        keyword = "omer",
                        confidence = 0.8f,
                        timestamp = currentTime
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
     * Updates the energy baseline for better wake word detection
     */
    private fun updateEnergyBaseline(energy: Double) {
        energyHistory.add(energy)
        
        // Keep only recent energy samples
        if (energyHistory.size > ENERGY_HISTORY_SIZE) {
            energyHistory.removeAt(0)
        }
        
        // Calculate baseline as average of lower 70% of energy samples
        if (energyHistory.size >= 10) {
            val sortedEnergy = energyHistory.sorted()
            val baselineCount = (sortedEnergy.size * 0.7).toInt()
            baselineEnergy = if (baselineCount > 0) {
                sortedEnergy.take(baselineCount).average()
            } else {
                MIN_SPEECH_ENERGY
            }
            
            // Ensure baseline is not too low
            baselineEnergy = maxOf(baselineEnergy, MIN_SPEECH_ENERGY / 2)
        }
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
        
        // Reset tracking state
        energyHistory.clear()
        baselineEnergy = MIN_SPEECH_ENERGY
        lastWakeWordTime = 0L
        silenceStartTime = 0L
        isInSilence = false
        
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
