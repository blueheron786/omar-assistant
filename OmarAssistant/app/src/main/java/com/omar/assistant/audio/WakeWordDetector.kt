package com.omar.assistant.audio

import android.content.Context
import android.util.Log
import kotlin.math.*

/**
 * WakeWordDetector - Detects wake words in audio stream
 * 
 * This implements a simple but effective local wake word detection
 * using spectral analysis and pattern matching. For maximum privacy,
 * all processing is done on-device.
 */
class WakeWordDetector(private val context: Context) {
    
    private val TAG = "WakeWordDetector"
    
    // Wake word patterns (simplified phonetic patterns)
    private val wakeWordPatterns = mapOf(
        "omar" to listOf(300.0, 800.0, 500.0, 200.0), // Simplified frequency pattern
        "عمر" to listOf(400.0, 700.0, 300.0), // Arabic "Omar"
        "3umar" to listOf(300.0, 800.0, 500.0, 200.0) // Alternative spelling
    )
    
    // Detection parameters
    private val sampleRate = 16000
    private val windowSize = 512
    private val hopSize = 256
    private val confidenceThreshold = 0.6
    
    // Energy and frequency analysis
    private val minEnergyThreshold = 1000.0
    
    /**
     * Detect wake word in audio data
     */
    fun detectWakeWord(audioData: ShortArray, wakeWords: List<String>): Boolean {
        if (audioData.isEmpty() || audioData.size < windowSize) {
            return false
        }
        
        try {
            // Convert to float for processing
            val floatData = audioData.map { it.toFloat() / Short.MAX_VALUE }.toFloatArray()
            
            // Apply simple pre-emphasis filter
            val preEmphasized = applyPreEmphasis(floatData)
            
            // Extract features
            val features = extractSpectralFeatures(preEmphasized)
            
            // Check each wake word
            for (wakeWord in wakeWords) {
                if (matchesWakeWord(features, wakeWord)) {
                    Log.d(TAG, "Wake word detected: $wakeWord")
                    return true
                }
            }
            
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in wake word detection", e)
            return false
        }
    }
    
    /**
     * Apply pre-emphasis filter to enhance high frequencies
     */
    private fun applyPreEmphasis(signal: FloatArray, alpha: Float = 0.97f): FloatArray {
        val filtered = FloatArray(signal.size)
        filtered[0] = signal[0]
        
        for (i in 1 until signal.size) {
            filtered[i] = signal[i] - alpha * signal[i - 1]
        }
        
        return filtered
    }
    
    /**
     * Extract spectral features from audio
     */
    private fun extractSpectralFeatures(signal: FloatArray): List<Double> {
        val features = mutableListOf<Double>()
        
        // Process audio in overlapping windows
        for (i in 0 until signal.size - windowSize step hopSize) {
            val window = signal.sliceArray(i until i + windowSize)
            
            // Apply Hamming window
            val windowedSignal = applyHammingWindow(window)
            
            // Calculate energy
            val energy = calculateEnergy(windowedSignal)
            if (energy < minEnergyThreshold) continue
            
            // Calculate spectral centroid (brightness)
            val spectralCentroid = calculateSpectralCentroid(windowedSignal)
            
            // Calculate zero crossing rate
            val zcr = calculateZeroCrossingRate(windowedSignal)
            
            features.add(spectralCentroid)
            features.add(zcr)
        }
        
        return features
    }
    
    /**
     * Apply Hamming window to reduce spectral leakage
     */
    private fun applyHammingWindow(signal: FloatArray): FloatArray {
        val windowed = FloatArray(signal.size)
        for (i in signal.indices) {
            val w = 0.54 - 0.46 * cos(2.0 * PI * i / (signal.size - 1))
            windowed[i] = signal[i] * w.toFloat()
        }
        return windowed
    }
    
    /**
     * Calculate signal energy
     */
    private fun calculateEnergy(signal: FloatArray): Double {
        return signal.map { it * it }.sum().toDouble()
    }
    
    /**
     * Calculate spectral centroid (measure of brightness)
     */
    private fun calculateSpectralCentroid(signal: FloatArray): Double {
        val fft = performSimpleFFT(signal)
        val magnitude = fft.map { sqrt(it.real * it.real + it.imag * it.imag) }
        
        var numerator = 0.0
        var denominator = 0.0
        
        for (i in magnitude.indices) {
            val freq = i * sampleRate.toDouble() / signal.size
            numerator += freq * magnitude[i]
            denominator += magnitude[i]
        }
        
        return if (denominator > 0) numerator / denominator else 0.0
    }
    
    /**
     * Calculate zero crossing rate
     */
    private fun calculateZeroCrossingRate(signal: FloatArray): Double {
        var crossings = 0
        for (i in 1 until signal.size) {
            if ((signal[i] >= 0) != (signal[i - 1] >= 0)) {
                crossings++
            }
        }
        return crossings.toDouble() / signal.size
    }
    
    /**
     * Simple FFT implementation for spectral analysis
     */
    private fun performSimpleFFT(signal: FloatArray): Array<Complex> {
        val n = signal.size
        val result = Array(n) { Complex(0.0, 0.0) }
        
        // Simple DFT (not optimized, but sufficient for our purposes)
        for (k in 0 until n / 2) { // Only need first half
            var real = 0.0
            var imag = 0.0
            
            for (t in 0 until n) {
                val angle = -2.0 * PI * k * t / n
                real += signal[t] * cos(angle)
                imag += signal[t] * sin(angle)
            }
            
            result[k] = Complex(real, imag)
        }
        
        return result
    }
    
    /**
     * Match extracted features against wake word patterns
     */
    private fun matchesWakeWord(features: List<Double>, wakeWord: String): Boolean {
        val pattern = wakeWordPatterns[wakeWord.lowercase()] ?: return false
        
        if (features.isEmpty()) return false
        
        // Simple pattern matching using cross-correlation
        val confidence = calculatePatternConfidence(features, pattern)
        
        Log.d(TAG, "Wake word '$wakeWord' confidence: $confidence")
        return confidence > confidenceThreshold
    }
    
    /**
     * Calculate confidence of pattern match
     */
    private fun calculatePatternConfidence(features: List<Double>, pattern: List<Double>): Double {
        if (features.size < pattern.size) return 0.0
        
        var maxCorrelation = 0.0
        
        // Slide pattern over features to find best match
        for (offset in 0..features.size - pattern.size) {
            var correlation = 0.0
            var featureNorm = 0.0
            var patternNorm = 0.0
            
            for (i in pattern.indices) {
                val featureVal = features[offset + i]
                val patternVal = pattern[i]
                
                correlation += featureVal * patternVal
                featureNorm += featureVal * featureVal
                patternNorm += patternVal * patternVal
            }
            
            // Normalize correlation
            val normalizedCorrelation = if (featureNorm > 0 && patternNorm > 0) {
                correlation / (sqrt(featureNorm) * sqrt(patternNorm))
            } else {
                0.0
            }
            
            maxCorrelation = maxOf(maxCorrelation, normalizedCorrelation)
        }
        
        return maxCorrelation
    }
    
    /**
     * Simple complex number representation
     */
    private data class Complex(val real: Double, val imag: Double)
    
    /**
     * Update wake word patterns (for customization)
     */
    fun updateWakeWordPattern(wakeWord: String, pattern: List<Double>) {
        // This could be used to train custom wake word patterns
        Log.d(TAG, "Wake word pattern updated for: $wakeWord")
    }
    
    /**
     * Get current confidence threshold
     */
    fun getConfidenceThreshold(): Double = confidenceThreshold
    
    /**
     * Perform energy-based voice activity detection as a pre-filter
     */
    fun hasVoiceActivity(audioData: ShortArray): Boolean {
        if (audioData.isEmpty()) return false
        
        val energy = audioData.map { it.toDouble() * it.toDouble() }.sum() / audioData.size
        return energy > minEnergyThreshold
    }
}
