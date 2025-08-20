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
            
            Log.d(TAG, "Started listening for wake word: $wakeWord")
            
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
        Log.d(TAG, "Updated wake word to: $wakeWord")
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
                    
                    // Wake word detection (simplified pattern matching)
                    if (isVoiceActive && shouldCheckForWakeWord()) {
                        val audioSnippet = audioBuffer.take(bytesRead).toShortArray()
                        if (detectWakeWord(audioSnippet, smoothedEnergy)) {
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
     * Simple wake word detection using energy patterns and basic phonetic matching
     * In production, you'd want to use a more sophisticated approach like keyword spotting models
     */
    private fun detectWakeWord(audioSnippet: ShortArray, energy: Float): Boolean {
        // Simple energy-based detection with cooldown
        val energyThreshold = wakeWordSensitivity * 2000f
        
        if (energy < energyThreshold) return false
        
        // Basic pattern detection (this is simplified - in production use ML models)
        val duration = audioSnippet.size.toFloat() / SAMPLE_RATE * 1000 // Duration in ms
        
        // "Omar" typically takes 400-800ms to say
        if (duration < 300 || duration > 1000) return false
        
        // Energy pattern analysis (simplified)
        val segments = 4
        val segmentSize = audioSnippet.size / segments
        val segmentEnergies = FloatArray(segments)
        
        for (i in 0 until segments) {
            val start = i * segmentSize
            val end = ((i + 1) * segmentSize).coerceAtMost(audioSnippet.size)
            segmentEnergies[i] = calculateAudioEnergy(
                audioSnippet.sliceArray(start until end),
                end - start
            )
        }
        
        // Look for the energy pattern of "O-mar" (peak-dip-peak pattern)
        val firstPeak = segmentEnergies[0] > segmentEnergies[1] * 1.2f
        val secondPeak = segmentEnergies[2] > segmentEnergies[1] * 1.1f || segmentEnergies[3] > segmentEnergies[1] * 1.1f
        
        val confidence = if (firstPeak && secondPeak) {
            val energyVariation = segmentEnergies.maxOrNull()!! / (segmentEnergies.minOrNull()!! + 1f)
            (energyVariation / 10f).coerceIn(0f, 1f)
        } else {
            0f
        }
        
        Log.d(TAG, "Wake word detection - Energy: $energy, Confidence: $confidence, Threshold: ${wakeWordSensitivity}")
        
        return confidence > wakeWordSensitivity
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
