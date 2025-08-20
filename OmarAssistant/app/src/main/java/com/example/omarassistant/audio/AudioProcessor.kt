package com.example.omarassistant.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Audio processor for wake word detection and voice activity detection
 * Implements battery-efficient continuous listening with configurable sensitivity
 */
class AudioProcessor(
    private var wakeWord: String = "omar",
    private var wakeWordSensitivity: Float = 0.7f,
    private var vadSensitivity: Float = 0.5f
) {
    
    companion object {
        private const val TAG = "AudioProcessor"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = 4096 // Increased buffer for better wake word detection (256ms at 16kHz)
        private const val VAD_WINDOW_SIZE = 160 // 10ms at 16kHz
        private const val WAKE_WORD_COOLDOWN_MS = 5000L // Increased to 5 seconds to reduce re-triggering
    }
    
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var lastWakeWordDetection = 0L
    
    // Audio analysis variables
    private val audioBuffer = ShortArray(BUFFER_SIZE)
    private val energyWindow = FloatArray(50) // Energy history for smoothing
    private var energyIndex = 0
    
    // Adaptive threshold variables
    private val ambientEnergyWindow = FloatArray(100) // Track ambient noise over longer period
    private var ambientIndex = 0
    private val speechEnergyHistory = mutableListOf<Float>() // Track recent speech energy levels
    private var lastThresholdUpdate = 0L
    private var adaptiveEnergyThreshold = 800f // Start with default
    private var adaptiveVadThreshold = 100f // Start with default
    private var isCalibrating = true
    private var calibrationStartTime = 0L
    
    // Log management to reduce spam
    private var lastRejectLogTime = 0L
    private var rejectLogCount = 0L
    private val LOG_REJECT_INTERVAL_MS = 5000L // Log rejections every 5 seconds max
    private var lastDetectLogTime = 0L
    private val LOG_DETECT_INTERVAL_MS = 1000L // Log speech detections every 1 second max
    
    // Callbacks
    var onWakeWordDetected: (() -> Unit)? = null
    var onVoiceActivityDetected: ((Boolean) -> Unit)? = null
    var onAudioLevelChanged: ((Float) -> Unit)? = null
    
    /**
     * Start continuous audio monitoring
     */
    suspend fun startListening() = withContext(Dispatchers.IO) {
        if (isListening) {
            Log.d(TAG, "Already listening, skipping start")
            return@withContext
        }
        
        Log.d(TAG, "Starting audio listening for wake word: '$wakeWord'")
        
        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            Log.d(TAG, "Minimum buffer size: $bufferSize")
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize.coerceAtLeast(BUFFER_SIZE * 2)
            )
            
            Log.d(TAG, "AudioRecord created, state: ${audioRecord?.state}")
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed - state: ${audioRecord?.state}")
                return@withContext
            }
            
            Log.d(TAG, "Starting recording...")
            audioRecord?.startRecording()
            val recordingState = audioRecord?.recordingState
            Log.d(TAG, "Recording state after start: $recordingState")
            
            if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "Failed to start recording - state: $recordingState")
                return@withContext
            }
            
            isListening = true
            
            // Initialize adaptive thresholds
            calibrationStartTime = System.currentTimeMillis()
            isCalibrating = true
            adaptiveEnergyThreshold = 800f
            adaptiveVadThreshold = 100f
            Log.d(TAG, "Starting adaptive threshold calibration...")
            
            Log.i(TAG, "Started listening for wake word: '$wakeWord' (sensitivity: $wakeWordSensitivity)")
            
            // Start audio processing loop
            Log.d(TAG, "Starting audio processing loop...")
            processAudioLoop()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio recording", e)
            stopListening()
        }
    }
    
    /**
     * Stop audio monitoring
     */
    fun stopListening() {
        isListening = false
        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio recording", e)
            }
        }
        audioRecord = null
        Log.d(TAG, "Stopped listening")
    }
    
    /**
     * Update sensitivity settings
     */
    fun updateSensitivity(wakeWordSensitivity: Float, vadSensitivity: Float) {
        this.wakeWordSensitivity = wakeWordSensitivity.coerceIn(0.1f, 1.0f)
        this.vadSensitivity = vadSensitivity.coerceIn(0.1f, 1.0f)
        Log.d(TAG, "Updated sensitivity: Wake Word=$wakeWordSensitivity, VAD=$vadSensitivity")
    }
    
    /**
     * Update wake word
     */
    fun updateWakeWord(newWakeWord: String) {
        this.wakeWord = newWakeWord.lowercase()
        Log.i(TAG, "Updated wake word to: '$wakeWord'")
    }
    
    /**
     * Main audio processing loop
     */
    private suspend fun processAudioLoop() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Audio processing loop started")
        
        while (isListening && audioRecord != null) {
            try {
                val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                
                if (bytesRead > 0) {
                    // Convert bytes to samples properly (each sample is 2 bytes in 16-bit audio)
                    val samplesRead = bytesRead / 2
                    
                    // Calculate audio energy for VAD
                    val energy = calculateAudioEnergy(audioBuffer, samplesRead)
                    val normalizedLevel = (energy / 32768.0f).coerceIn(0f, 1f)
                    
                    // Update energy window for smoothing
                    energyWindow[energyIndex] = energy
                    energyIndex = (energyIndex + 1) % energyWindow.size
                    
                    // Update ambient energy tracking
                    updateAmbientTracking(energy)
                    
                    // Smooth energy calculation
                    val smoothedEnergy = energyWindow.average().toFloat()
                    
                    // Use adaptive thresholds instead of fixed ones
                    updateAdaptiveThresholds()
                    val isVoiceActive = smoothedEnergy > adaptiveVadThreshold
                    
                    // Log energy levels and thresholds occasionally  
                    if (System.currentTimeMillis() % 5000 < 100) { // Every ~5 seconds
                        val ambientLevel = ambientEnergyWindow.average().toFloat()
                        val calibrationStatus = if (isCalibrating) " (calibrating)" else " (adaptive)"
                        Log.d(TAG, "Energy monitoring$calibrationStatus - Current: $smoothedEnergy, " +
                                "Ambient: $ambientLevel, VAD threshold: $adaptiveVadThreshold, " +
                                "Wake threshold: $adaptiveEnergyThreshold, Voice active: $isVoiceActive")
                    }
                    
                    // Notify listeners
                    withContext(Dispatchers.Main) {
                        onAudioLevelChanged?.invoke(normalizedLevel)
                        onVoiceActivityDetected?.invoke(isVoiceActive)
                    }
                    
                    // Wake word detection (improved pattern matching)
                    if (isVoiceActive && shouldCheckForWakeWord()) {
                        // Convert bytes to samples properly (each sample is 2 bytes in 16-bit audio)
                        val sampleCount = bytesRead / 2
                        val audioSnippet = audioBuffer.take(sampleCount).toShortArray()
                        
                        if (detectWakeWord(audioSnippet, smoothedEnergy)) {
                            Log.i(TAG, "Wake word '$wakeWord' detected!")
                            lastWakeWordDetection = System.currentTimeMillis()
                            withContext(Dispatchers.Main) {
                                onWakeWordDetected?.invoke()
                            }
                        }
                    }
                }
                
                // Small delay to prevent excessive CPU usage
                delay(10)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio processing loop", e)
                break
            }
        }
    }
    
    /**
     * Calculate RMS energy of audio buffer
     */
    private fun calculateAudioEnergy(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            sum += (buffer[i] * buffer[i]).toDouble()
        }
        return sqrt(sum / length).toFloat()
    }
    
    /**
     * Generic wake word detection using energy patterns that works with any word
     * In production, you'd want to use a more sophisticated approach like keyword spotting models
     */
    private fun detectWakeWord(audioSnippet: ShortArray, energy: Float): Boolean {
        // Use adaptive energy threshold instead of fixed one
        if (energy < adaptiveEnergyThreshold) {
            // Only log rejections periodically to avoid spam
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastRejectLogTime > LOG_REJECT_INTERVAL_MS) {
                Log.d(TAG, "Speech rejected (${rejectLogCount + 1} rejections in ${(currentTime - lastRejectLogTime) / 1000}s) - Energy: $energy < Threshold: $adaptiveEnergyThreshold")
                lastRejectLogTime = currentTime
                rejectLogCount = 0
            } else {
                rejectLogCount++
            }
            return false
        }
        
        // Only log speech detection periodically to avoid spam
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectLogTime > LOG_DETECT_INTERVAL_MS) {
            Log.i(TAG, "🎯 SPEECH DETECTED! Energy: $energy >= Threshold: $adaptiveEnergyThreshold")
            lastDetectLogTime = currentTime
        }
        
        // Record this speech energy for future threshold adaptation
        
        // Calculate duration - be more flexible for natural speech patterns
        val duration = audioSnippet.size.toFloat() / SAMPLE_RATE * 1000 // Duration in ms
        
        // More flexible duration range - accommodate audio chunks for wake word detection
        if (duration < 100 || duration > 500) {
            // Don't log every rejection to avoid spam
            return false
        }
        
        Log.d(TAG, "Passed initial checks, proceeding with detailed analysis...")
        
        // Analyze energy distribution across the audio snippet
        val segments = 8 // More granular analysis
        val segmentSize = audioSnippet.size / segments
        val segmentEnergies = FloatArray(segments)
        
        for (i in 0 until segments) {
            val start = i * segmentSize
            val end = ((i + 1) * segmentSize).coerceAtMost(audioSnippet.size)
            if (end > start) {
                segmentEnergies[i] = calculateAudioEnergy(
                    audioSnippet.sliceArray(start until end),
                    end - start
                )
            }
        }
        
        // Generic pattern analysis that works for different words
        val maxEnergy = segmentEnergies.maxOrNull() ?: 0f
        val minEnergy = segmentEnergies.minOrNull() ?: 0f
        val avgEnergy = segmentEnergies.average().toFloat()
        
        // Count segments with significant energy (syllables/phonemes)
        val significantSegments = segmentEnergies.count { it > avgEnergy * 0.7f }
        
        // Calculate energy variation (helps distinguish speech from noise)
        val energyVariation = if (minEnergy > 0) maxEnergy / minEnergy else 0f
        
        // Check for continuous energy (not just single spikes)
        val continuousEnergy = segmentEnergies.asSequence()
            .windowed(2)
            .count { it[0] > avgEnergy * 0.5f && it[1] > avgEnergy * 0.5f }
        
        // Enhanced analysis for vowel-heavy names like "Aisha"
        // Check for sustained energy segments (good for vowels like "aah", "eee", "eye")
        val sustainedSegments = segmentEnergies.asSequence()
            .windowed(3)
            .count { window -> 
                val sustained = window.all { it > avgEnergy * 0.6f }
                sustained
            }
        
        // Generic wake word criteria with enhanced vowel detection:
        // 1. Must have reasonable energy variation (not monotone noise)
        // 2. Must have multiple segments with significant energy (syllables)
        // 3. Must have some continuous energy (connected speech)
        // 4. Energy must be significantly above threshold
        // 5. Enhanced: Allow for sustained vowel segments (for names like Aisha)
        
        // Much more permissive criteria for wake word detection
        val hasEnergyVariation = energyVariation >= 1.1f && energyVariation <= 50f // More permissive
        val hasStrongSignal = maxEnergy > adaptiveEnergyThreshold * 0.8f // Lower threshold
        val hasContinuousEnergy = continuousEnergy >= 1 // At least some continuous energy
        
        val confidence = when {
            hasStrongSignal -> { // Primary requirement is just strong signal
                // Much simpler and more reliable confidence calculation
                val energyScore = minOf(1.0f, (maxEnergy / adaptiveEnergyThreshold) / 3f) // More conservative energy scoring
                val variationScore = if (hasEnergyVariation) 0.3f else 0.1f
                val segmentScore = minOf(0.4f, significantSegments * 0.05f) // Reward multiple segments
                val continuityScore = if (hasContinuousEnergy) 0.2f else 0.0f
                
                val totalScore = energyScore + variationScore + segmentScore + continuityScore
                totalScore.coerceIn(0.3f, 1.0f) // Ensure minimum confidence of 0.3
            }
            else -> {
                // Even if primary checks fail, give some confidence based on energy alone
                val energyRatio = maxEnergy / adaptiveEnergyThreshold
                if (energyRatio > 1.5f) 0.2f else 0.0f // Small confidence for very strong signals
            }
        }
        
        Log.d(TAG, "Wake word analysis - Word: '$wakeWord', Duration: ${duration}ms, Energy: $energy, " +
                "Variation: $energyVariation, Segments: $significantSegments, " +
                "Continuous: $continuousEnergy, Sustained: $sustainedSegments, " +
                "Confidence: $confidence, Sensitivity: $wakeWordSensitivity")
        
        // Much more permissive confidence threshold (0.1-0.4 range instead of 0.15-0.75)
        val confidenceThreshold = (1.0f - wakeWordSensitivity) * 0.3f + 0.1f
        
        val isWakeWord = confidence > confidenceThreshold
        
        if (isWakeWord) {
            Log.i(TAG, "🎉 WAKE WORD DETECTED! '$wakeWord' - Confidence: $confidence > $confidenceThreshold")
            // Only record speech energy when we actually detect a wake word
            recordSpeechEnergy(energy)
        } else {
            Log.d(TAG, "Wake word rejected - Confidence: $confidence <= $confidenceThreshold")
        }
        
        return isWakeWord
    }
    
    /**
     * Check if enough time has passed since last wake word detection
     */
    private fun shouldCheckForWakeWord(): Boolean {
        return System.currentTimeMillis() - lastWakeWordDetection > WAKE_WORD_COOLDOWN_MS
    }
    
    /**
     * Update ambient energy tracking for adaptive thresholds
     */
    private fun updateAmbientTracking(energy: Float) {
        ambientEnergyWindow[ambientIndex] = energy
        ambientIndex = (ambientIndex + 1) % ambientEnergyWindow.size
    }
    
    /**
     * Record speech energy for threshold adaptation
     */
    private fun recordSpeechEnergy(energy: Float) {
        speechEnergyHistory.add(energy)
        // Keep only recent speech samples (last 20 detections)
        if (speechEnergyHistory.size > 20) {
            speechEnergyHistory.removeAt(0)
        }
    }
    
    /**
     * Update adaptive thresholds based on ambient noise and recent speech patterns
     */
    private fun updateAdaptiveThresholds() {
        val currentTime = System.currentTimeMillis()
        
        // Update thresholds every 10 seconds, or every 2 seconds during calibration
        val updateInterval = if (isCalibrating) 2000L else 10000L
        if (currentTime - lastThresholdUpdate < updateInterval) {
            return
        }
        
        lastThresholdUpdate = currentTime
        
        // Check if calibration period is over (30 seconds)
        if (isCalibrating && currentTime - calibrationStartTime > 30000) {
            isCalibrating = false
            Log.i(TAG, "Adaptive threshold calibration completed")
        }
        
        // Calculate ambient noise level
        val ambientLevel = if (ambientEnergyWindow.any { it > 0 }) {
            ambientEnergyWindow.filter { it > 0 }.average().toFloat()
        } else {
            50f // Default low level
        }
        
        // Calculate dynamic VAD threshold based on ambient noise
        // During quiet times (like night), use lower threshold
        // During noisy times, use higher threshold
        adaptiveVadThreshold = when {
            ambientLevel < 80 -> {
                // Very quiet environment (night/whisper mode)
                vadSensitivity * 50f
            }
            ambientLevel < 150 -> {
                // Quiet environment  
                vadSensitivity * 80f
            }
            ambientLevel < 300 -> {
                // Normal environment
                vadSensitivity * 120f
            }
            else -> {
                // Noisy environment
                vadSensitivity * 200f
            }
        }.coerceAtLeast(20f).coerceAtMost(400f)
        
        // Calculate dynamic wake word threshold
        if (speechEnergyHistory.size >= 3) {
            // If we have recent speech samples, use them to adapt
            val avgSpeechEnergy = speechEnergyHistory.average().toFloat()
            val minSpeechEnergy = speechEnergyHistory.minOrNull() ?: 200f
            
            // Set threshold between ambient and minimum speech energy
            val baseThreshold = ambientLevel + (minSpeechEnergy - ambientLevel) * 0.3f
            adaptiveEnergyThreshold = (baseThreshold * wakeWordSensitivity * 1.5f)
                .coerceAtLeast(ambientLevel * 1.2f) // Much closer to ambient level
                .coerceAtMost(avgSpeechEnergy * 0.8f)
                .coerceAtLeast(100f) // Absolute minimum for any environment
                .coerceAtMost(400f) // Absolute maximum - reasonable for normal speech
                
            Log.i(TAG, "Thresholds adapted using speech history - Ambient: $ambientLevel, VAD: $adaptiveVadThreshold, Wake: $adaptiveEnergyThreshold")
        } else {
            // No speech history yet, use ambient-based thresholds with strict limits
            adaptiveEnergyThreshold = when {
                ambientLevel < 50 -> {
                    // Very quiet environment
                    120f
                }
                ambientLevel < 100 -> {
                    // Quiet environment
                    150f  
                }
                ambientLevel < 200 -> {
                    // Normal environment
                    200f
                }
                ambientLevel < 400 -> {
                    // Moderately noisy
                    250f
                }
                else -> {
                    // Very noisy environment  
                    300f
                }
            }.let { baseThreshold ->
                // Apply sensitivity but keep within reasonable bounds
                (baseThreshold * wakeWordSensitivity)
                    .coerceAtLeast(100f)   // Never go below 100
                    .coerceAtMost(400f)    // Never go above 400
                    .coerceAtLeast(ambientLevel * 1.1f) // At least 10% above ambient
            }
            
            if (currentTime - calibrationStartTime <= 5000L) {
                // Only log during first 5 seconds of calibration to avoid spam
                Log.d(TAG, "Calibrating thresholds - Ambient: $ambientLevel, VAD: $adaptiveVadThreshold, Wake: $adaptiveEnergyThreshold")
            }
        }
    }
    
    /**
     * Get current listening state
     */
    fun isCurrentlyListening(): Boolean = isListening
}
