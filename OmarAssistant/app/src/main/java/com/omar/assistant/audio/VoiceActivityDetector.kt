package com.omar.assistant.audio

import android.util.Log
import kotlin.math.*

/**
 * VoiceActivityDetector - Detects speech activity in audio stream
 * 
 * This class implements a local Voice Activity Detection (VAD) algorithm
 * to determine when the user is speaking vs. silence/background noise.
 * Uses energy-based and spectral-based features for accurate detection.
 */
class VoiceActivityDetector {
    
    private val TAG = "VoiceActivityDetector"
    
    // VAD parameters
    private val energyThreshold = 1500.0
    private val zcrThreshold = 0.1
    private val spectralCentroidThreshold = 1000.0
    private val minSpeechDurationMs = 300 // Minimum speech duration to consider
    
    // Adaptive thresholds
    private var adaptiveEnergyThreshold = energyThreshold
    private var backgroundEnergyLevel = 0.0
    private val adaptationRate = 0.01
    
    // Smoothing for better detection
    private val smoothingWindow = 5
    private val recentDecisions = mutableListOf<Boolean>()
    
    /**
     * Detect if speech is present in the audio data
     */
    fun isSpeechPresent(audioData: ShortArray): Boolean {
        if (audioData.isEmpty()) return false
        
        try {
            // Calculate features
            val energy = calculateEnergy(audioData)
            val zcr = calculateZeroCrossingRate(audioData)
            val spectralCentroid = calculateSpectralCentroid(audioData)
            
            // Update adaptive threshold
            updateAdaptiveThreshold(energy)
            
            // Multi-feature decision
            val energyDecision = energy > adaptiveEnergyThreshold
            val zcrDecision = zcr > zcrThreshold
            val spectralDecision = spectralCentroid > spectralCentroidThreshold
            
            // Combine decisions (at least 2 out of 3 must be true)
            val currentDecision = listOf(energyDecision, zcrDecision, spectralDecision).count { it } >= 2
            
            // Apply temporal smoothing
            val smoothedDecision = applySmoothingFilter(currentDecision)
            
            Log.v(TAG, "VAD - Energy: %.1f (%.1f), ZCR: %.3f, SC: %.1f, Decision: %s"
                .format(energy, adaptiveEnergyThreshold, zcr, spectralCentroid, smoothedDecision))
            
            return smoothedDecision
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in VAD processing", e)
            return false
        }
    }
    
    /**
     * Calculate signal energy (RMS)
     */
    private fun calculateEnergy(audioData: ShortArray): Double {
        if (audioData.isEmpty()) return 0.0
        
        var sum = 0.0
        for (sample in audioData) {
            sum += sample.toDouble() * sample.toDouble()
        }
        return sqrt(sum / audioData.size)
    }
    
    /**
     * Calculate Zero Crossing Rate
     * High ZCR typically indicates unvoiced speech or noise
     * Low ZCR typically indicates voiced speech
     */
    private fun calculateZeroCrossingRate(audioData: ShortArray): Double {
        if (audioData.size < 2) return 0.0
        
        var crossings = 0
        for (i in 1 until audioData.size) {
            if ((audioData[i] >= 0) != (audioData[i - 1] >= 0)) {
                crossings++
            }
        }
        return crossings.toDouble() / audioData.size
    }
    
    /**
     * Calculate spectral centroid (brightness measure)
     * Higher values typically indicate speech content
     */
    private fun calculateSpectralCentroid(audioData: ShortArray): Double {
        if (audioData.isEmpty()) return 0.0
        
        // Simple frequency domain analysis
        val windowSize = minOf(512, audioData.size)
        val window = audioData.sliceArray(0 until windowSize)
        
        // Apply window function
        val windowedData = applyHammingWindow(window)
        
        // Simple magnitude spectrum calculation
        val spectrum = calculateMagnitudeSpectrum(windowedData)
        
        // Calculate centroid
        var numerator = 0.0
        var denominator = 0.0
        
        for (i in spectrum.indices) {
            val frequency = i * 16000.0 / windowSize // Assuming 16kHz sample rate
            numerator += frequency * spectrum[i]
            denominator += spectrum[i]
        }
        
        return if (denominator > 0) numerator / denominator else 0.0
    }
    
    /**
     * Apply Hamming window to reduce spectral leakage
     */
    private fun applyHammingWindow(data: ShortArray): FloatArray {
        val windowed = FloatArray(data.size)
        for (i in data.indices) {
            val w = 0.54 - 0.46 * cos(2.0 * PI * i / (data.size - 1))
            windowed[i] = data[i].toFloat() * w.toFloat()
        }
        return windowed
    }
    
    /**
     * Calculate magnitude spectrum using simple DFT
     */
    private fun calculateMagnitudeSpectrum(data: FloatArray): DoubleArray {
        val n = data.size
        val spectrum = DoubleArray(n / 2)
        
        for (k in 0 until n / 2) {
            var real = 0.0
            var imag = 0.0
            
            for (t in 0 until n) {
                val angle = -2.0 * PI * k * t / n
                real += data[t] * cos(angle)
                imag += data[t] * sin(angle)
            }
            
            spectrum[k] = sqrt(real * real + imag * imag)
        }
        
        return spectrum
    }
    
    /**
     * Update adaptive energy threshold based on background noise
     */
    private fun updateAdaptiveThreshold(currentEnergy: Double) {
        // Simple adaptation: if energy is consistently low, treat as background
        if (currentEnergy < adaptiveEnergyThreshold * 0.5) {
            backgroundEnergyLevel = backgroundEnergyLevel * (1 - adaptationRate) + currentEnergy * adaptationRate
            adaptiveEnergyThreshold = maxOf(energyThreshold, backgroundEnergyLevel * 3.0)
        }
    }
    
    /**
     * Apply smoothing filter to reduce false positives/negatives
     */
    private fun applySmoothingFilter(currentDecision: Boolean): Boolean {
        // Add current decision to sliding window
        recentDecisions.add(currentDecision)
        if (recentDecisions.size > smoothingWindow) {
            recentDecisions.removeAt(0)
        }
        
        // Return majority vote
        val trueCount = recentDecisions.count { it }
        return trueCount > recentDecisions.size / 2
    }
    
    /**
     * Detect speech boundaries (start and end of speech)
     */
    fun detectSpeechBoundaries(audioData: ShortArray, windowSizeMs: Int = 100): Pair<Int, Int> {
        val sampleRate = 16000
        val windowSizeSamples = (sampleRate * windowSizeMs) / 1000
        
        var speechStart = -1
        var speechEnd = -1
        
        // Scan for speech start
        for (i in 0 until audioData.size - windowSizeSamples step windowSizeSamples / 2) {
            val window = audioData.sliceArray(i until i + windowSizeSamples)
            if (isSpeechPresent(window)) {
                speechStart = i
                break
            }
        }
        
        if (speechStart == -1) {
            return Pair(-1, -1) // No speech found
        }
        
        // Scan for speech end (from the end backwards)
        for (i in audioData.size - windowSizeSamples downTo speechStart step windowSizeSamples / 2) {
            val window = audioData.sliceArray(i until i + windowSizeSamples)
            if (isSpeechPresent(window)) {
                speechEnd = i + windowSizeSamples
                break
            }
        }
        
        return Pair(speechStart, speechEnd)
    }
    
    /**
     * Reset adaptive parameters
     */
    fun reset() {
        adaptiveEnergyThreshold = energyThreshold
        backgroundEnergyLevel = 0.0
        recentDecisions.clear()
    }
    
    /**
     * Get current VAD statistics for debugging
     */
    fun getVadStats(): String {
        return "VAD Stats - Adaptive Threshold: %.1f, Background: %.1f, Recent Decisions: %s"
            .format(adaptiveEnergyThreshold, backgroundEnergyLevel, recentDecisions.toString())
    }
    
    /**
     * Configure VAD sensitivity
     */
    fun setSensitivity(sensitivity: Float) {
        // sensitivity should be between 0.1 (less sensitive) and 2.0 (more sensitive)
        val clampedSensitivity = sensitivity.coerceIn(0.1f, 2.0f)
        adaptiveEnergyThreshold = energyThreshold / clampedSensitivity
        Log.d(TAG, "VAD sensitivity set to $clampedSensitivity (threshold: $adaptiveEnergyThreshold)")
    }
}
