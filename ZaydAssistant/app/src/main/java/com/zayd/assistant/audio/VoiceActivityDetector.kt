package com.zayd.assistant.audio

import android.util.Log
import kotlin.math.sqrt

/**
 * Voice Activity Detection using energy-based approach
 * Determines when the user is speaking vs silence/background noise
 */
class VoiceActivityDetector {
    
    companion object {
        private const val TAG = "VoiceActivityDetector"
        private const val DEFAULT_ENERGY_THRESHOLD = 2000.0
        private const val NOISE_FLOOR_MULTIPLIER = 3.0
        private const val SILENCE_DURATION_MS = 1500L // 1.5 seconds of silence to stop
        private const val MIN_SPEECH_DURATION_MS = 300L // Minimum speech duration
    }
    
    private var energyThreshold = DEFAULT_ENERGY_THRESHOLD
    private var noiseFloor = 0.0
    private var isCalibrated = false
    private var lastSpeechTime = 0L
    private var speechStartTime = 0L
    private var isSpeaking = false
    
    /**
     * Calibrates the detector with background noise
     * Should be called during silence
     */
    fun calibrate(audioSamples: List<ShortArray>) {
        val totalEnergy = audioSamples.sumOf { calculateRMSEnergy(it) }
        noiseFloor = totalEnergy / audioSamples.size
        energyThreshold = noiseFloor * NOISE_FLOOR_MULTIPLIER
        isCalibrated = true
        
        Log.d(TAG, "VAD calibrated - Noise floor: $noiseFloor, Threshold: $energyThreshold")
    }
    
    /**
     * Detects voice activity in audio samples
     * Returns true if speech is detected
     */
    fun detectVoiceActivity(audioSamples: ShortArray): VoiceActivityResult {
        val energy = calculateRMSEnergy(audioSamples)
        val currentTime = System.currentTimeMillis()
        
        val hasVoice = if (isCalibrated) {
            energy > energyThreshold
        } else {
            // Fallback to default threshold if not calibrated
            energy > DEFAULT_ENERGY_THRESHOLD
        }
        
        val previouslySpeaking = isSpeaking
        
        if (hasVoice) {
            if (!isSpeaking) {
                speechStartTime = currentTime
                isSpeaking = true
                Log.d(TAG, "Speech started - Energy: $energy")
            }
            lastSpeechTime = currentTime
        } else {
            if (isSpeaking && currentTime - lastSpeechTime > SILENCE_DURATION_MS) {
                val speechDuration = currentTime - speechStartTime
                if (speechDuration > MIN_SPEECH_DURATION_MS) {
                    isSpeaking = false
                    Log.d(TAG, "Speech ended - Duration: ${speechDuration}ms")
                    return VoiceActivityResult(
                        hasVoice = false,
                        speechEnded = true,
                        speechDuration = speechDuration,
                        energy = energy
                    )
                } else {
                    // Too short, consider it noise
                    isSpeaking = false
                    Log.d(TAG, "Speech too short, ignored - Duration: ${speechDuration}ms")
                }
            }
        }
        
        return VoiceActivityResult(
            hasVoice = hasVoice,
            speechEnded = false,
            speechDuration = if (isSpeaking) currentTime - speechStartTime else 0L,
            energy = energy
        )
    }
    
    /**
     * Calculates RMS (Root Mean Square) energy of audio samples
     */
    private fun calculateRMSEnergy(samples: ShortArray): Double {
        if (samples.isEmpty()) return 0.0
        
        val sumSquares = samples.sumOf { it.toDouble() * it.toDouble() }
        return sqrt(sumSquares / samples.size)
    }
    
    /**
     * Returns true if currently detecting speech
     */
    fun isSpeaking(): Boolean = isSpeaking
    
    /**
     * Resets the detector state
     */
    fun reset() {
        isSpeaking = false
        lastSpeechTime = 0L
        speechStartTime = 0L
    }
    
    /**
     * Sets a custom energy threshold
     */
    fun setEnergyThreshold(threshold: Double) {
        energyThreshold = threshold
        Log.d(TAG, "Energy threshold set to: $threshold")
    }
    
    /**
     * Gets current energy threshold
     */
    fun getEnergyThreshold(): Double = energyThreshold
}

/**
 * Result of voice activity detection
 */
data class VoiceActivityResult(
    val hasVoice: Boolean,
    val speechEnded: Boolean,
    val speechDuration: Long,
    val energy: Double
)
