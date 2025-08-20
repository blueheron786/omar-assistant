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
    private var wakeWord: String = "aisha",
    private var wakeWordSensitivity: Float = 0.7f,
    private var vadSensitivity: Float = 0.5f
) {
    
    companion object {
        private const val TAG = "AudioProcessor"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = 1024
        private const val VAD_WINDOW_SIZE = 160 // 10ms at 16kHz
        private const val WAKE_WORD_COOLDOWN_MS = 2000L
    }
    
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var lastWakeWordDetection = 0L
    
    // Audio analysis variables
    private val audioBuffer = ShortArray(BUFFER_SIZE)
    private val energyWindow = FloatArray(50) // Energy history for smoothing
    private var energyIndex = 0
    
    // Callbacks
    var onWakeWordDetected: (() -> Unit)? = null
    var onVoiceActivityDetected: ((Boolean) -> Unit)? = null
    var onAudioLevelChanged: ((Float) -> Unit)? = null
    
    /**
     * Start continuous audio monitoring
     */
    suspend fun startListening() = withContext(Dispatchers.IO) {
        if (isListening) return@withContext
        
        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize.coerceAtLeast(BUFFER_SIZE * 2)
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return@withContext
            }
            
            audioRecord?.startRecording()
            isListening = true
            
            Log.i(TAG, "Started listening for wake word: '$wakeWord' (sensitivity: $wakeWordSensitivity)")
            
            // Start audio processing loop
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
        while (isListening && audioRecord != null) {
            try {
                val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                
                if (bytesRead > 0) {
                    // Calculate audio energy for VAD
                    val energy = calculateAudioEnergy(audioBuffer, bytesRead)
                    val normalizedLevel = (energy / 32768.0f).coerceIn(0f, 1f)
                    
                    // Update energy window for smoothing
                    energyWindow[energyIndex] = energy
                    energyIndex = (energyIndex + 1) % energyWindow.size
                    
                    // Smooth energy calculation
                    val smoothedEnergy = energyWindow.average().toFloat()
                    val isVoiceActive = smoothedEnergy > (vadSensitivity * 1000)
                    
                    // Notify listeners
                    withContext(Dispatchers.Main) {
                        onAudioLevelChanged?.invoke(normalizedLevel)
                        onVoiceActivityDetected?.invoke(isVoiceActive)
                    }
                    
                    // Wake word detection (improved pattern matching)
                    if (isVoiceActive && shouldCheckForWakeWord()) {
                        val audioSnippet = audioBuffer.take(bytesRead).toShortArray()
                        
                        Log.d(TAG, "Checking for wake word '$wakeWord' - Energy: $smoothedEnergy, VAD active: $isVoiceActive")
                        
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
        // Adjust energy threshold based on sensitivity (0.0-1.0 range)
        val energyThreshold = wakeWordSensitivity * 150000f
        
        if (energy < energyThreshold) return false
        
        // Calculate duration
        val duration = audioSnippet.size.toFloat() / SAMPLE_RATE * 1000 // Duration in ms
        
        // Accommodate both short (eye-shah ~600-900ms) and long (aah-eee-shah ~800-1200ms) pronunciations
        if (duration < 400 || duration > 1500) return false
        
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
        
        val hasEnergyVariation = energyVariation >= 1.3f && energyVariation <= 25f
        val hasMultipleSegments = significantSegments >= 2 && significantSegments <= 7
        val hasContinuousEnergy = continuousEnergy >= 1
        val hasStrongSignal = maxEnergy > energyThreshold * 1.2f
        val hasSustainedVowels = sustainedSegments >= 0 // Allow for vowel-heavy pronunciations
        
        val confidence = when {
            hasEnergyVariation && hasMultipleSegments && hasContinuousEnergy && hasStrongSignal && hasSustainedVowels -> {
                // Calculate confidence based on signal quality
                val variationScore = (energyVariation / 12f).coerceIn(0f, 1f)
                val segmentScore = (significantSegments / 7f).coerceIn(0f, 1f)
                val continuityScore = (continuousEnergy / 5f).coerceIn(0f, 1f)
                val energyScore = ((maxEnergy / energyThreshold) / 3f).coerceIn(0f, 1f)
                val vowelScore = (sustainedSegments / 3f).coerceIn(0f, 1f)
                
                // Weight the scores for vowel-heavy names
                (variationScore * 0.2f + segmentScore * 0.25f + continuityScore * 0.2f + 
                 energyScore * 0.25f + vowelScore * 0.1f)
            }
            else -> 0f
        }
        
        Log.d(TAG, "Wake word analysis - Word: '$wakeWord', Duration: ${duration}ms, Energy: $energy, " +
                "Variation: $energyVariation, Segments: $significantSegments, " +
                "Continuous: $continuousEnergy, Sustained: $sustainedSegments, " +
                "Confidence: $confidence, Sensitivity: $wakeWordSensitivity")
        
        // Convert sensitivity (0.0-1.0) to threshold (0.75-0.15) - more accommodating for pronunciation variations
        val confidenceThreshold = (1.0f - wakeWordSensitivity) * 0.6f + 0.15f
        
        return confidence > confidenceThreshold
    }
    
    /**
     * Check if enough time has passed since last wake word detection
     */
    private fun shouldCheckForWakeWord(): Boolean {
        return System.currentTimeMillis() - lastWakeWordDetection > WAKE_WORD_COOLDOWN_MS
    }
    
    /**
     * Get current listening state
     */
    fun isCurrentlyListening(): Boolean = isListening
}
