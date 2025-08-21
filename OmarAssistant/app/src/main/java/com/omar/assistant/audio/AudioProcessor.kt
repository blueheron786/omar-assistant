package com.omar.assistant.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * AudioProcessor - Handles audio recording and buffering
 * 
 * This class manages the microphone input, maintains a rolling buffer
 * of audio data for wake word detection, and provides methods to
 * capture audio for speech recognition.
 */
class AudioProcessor(private val context: Context) {
    
    private val TAG = "AudioProcessor"
    
    // Audio configuration
    private val sampleRate = 16000 // 16kHz is standard for speech
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2
    
    // Audio recording
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    
    // Audio buffer for wake word detection (circular buffer)
    private val audioBufferSize = sampleRate * 3 // 3 seconds of audio
    private val audioBuffer = ConcurrentLinkedQueue<Short>()
    private val maxBufferSize = audioBufferSize
    
    // Temporary buffer for reading from AudioRecord
    private val tempBuffer = ShortArray(bufferSizeInBytes / 2)
    
    /**
     * Start audio recording
     */
    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSizeInBytes
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            // Start recording loop in background
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                recordingLoop()
            }
            
            Log.d(TAG, "Audio recording started")
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission not granted", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
        }
    }
    
    /**
     * Stop audio recording
     */
    fun stopRecording() {
        if (!isRecording) {
            return
        }
        
        isRecording = false
        recordingJob?.cancel()
        
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        
        audioBuffer.clear()
        Log.d(TAG, "Audio recording stopped")
    }
    
    /**
     * Main recording loop - continuously capture audio and maintain buffer
     */
    private suspend fun recordingLoop() {
        while (isRecording && currentCoroutineContext().isActive) {
            try {
                val bytesRead = audioRecord?.read(tempBuffer, 0, tempBuffer.size) ?: 0
                
                if (bytesRead > 0) {
                    // Add to circular buffer
                    synchronized(audioBuffer) {
                        for (i in 0 until bytesRead) {
                            audioBuffer.offer(tempBuffer[i])
                            
                            // Maintain buffer size limit
                            if (audioBuffer.size > maxBufferSize) {
                                audioBuffer.poll()
                            }
                        }
                    }
                } else {
                    delay(10) // Small delay if no data
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in recording loop", e)
                delay(100)
            }
        }
    }
    
    /**
     * Get the latest audio data from the buffer
     * @param durationMs Duration of audio to return in milliseconds
     * @return Array of audio samples
     */
    fun getLatestAudioData(durationMs: Int = 1000): ShortArray {
        synchronized(audioBuffer) {
            val samplesNeeded = (sampleRate * durationMs) / 1000
            val availableSamples = minOf(samplesNeeded, audioBuffer.size)
            
            Log.d(TAG, "getLatestAudioData: requested $durationMs ms ($samplesNeeded samples), available: $availableSamples")
            
            if (availableSamples == 0) {
                Log.d(TAG, "getLatestAudioData: No audio samples available")
                return ShortArray(0)
            }
            
            // Get the last N samples
            val result = ShortArray(availableSamples)
            val bufferArray = audioBuffer.toTypedArray()
            val startIndex = maxOf(0, bufferArray.size - availableSamples)
            
            for (i in 0 until availableSamples) {
                result[i] = bufferArray[startIndex + i]
            }
            
            Log.d(TAG, "getLatestAudioData: Returning ${result.size} samples")
            return result
        }
    }
    
    /**
     * Capture audio for a specific duration (for speech recognition)
     * @param durationMs Duration to capture in milliseconds
     * @return Array of audio samples
     */
    suspend fun captureAudioForDuration(durationMs: Int): ShortArray {
        val samplesNeeded = (sampleRate * durationMs) / 1000
        val capturedAudio = mutableListOf<Short>()
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < durationMs && capturedAudio.size < samplesNeeded) {
            val recentAudio = getLatestAudioData(100) // Get 100ms chunks
            if (recentAudio.isNotEmpty()) {
                capturedAudio.addAll(recentAudio.toList())
            }
            delay(50) // Small delay between captures
        }
        
        return capturedAudio.toShortArray()
    }
    
    /**
     * Get audio buffer statistics for debugging
     */
    fun getBufferStats(): String {
        synchronized(audioBuffer) {
            val bufferSeconds = audioBuffer.size.toFloat() / sampleRate
            return "Buffer: ${audioBuffer.size} samples (%.2f seconds)".format(bufferSeconds)
        }
    }
    
    /**
     * Check if audio is currently being recorded
     */
    fun isRecording(): Boolean = isRecording
    
    /**
     * Get the current sample rate
     */
    fun getSampleRate(): Int = sampleRate
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stopRecording()
    }
    
    /**
     * Calculate RMS (Root Mean Square) of audio data for volume detection
     */
    fun calculateRMS(audioData: ShortArray): Double {
        if (audioData.isEmpty()) return 0.0
        
        var sum = 0.0
        for (sample in audioData) {
            sum += sample.toDouble() * sample.toDouble()
        }
        return kotlin.math.sqrt(sum / audioData.size)
    }
    
    /**
     * Apply simple noise gate to filter out background noise
     */
    fun applyNoiseGate(audioData: ShortArray, threshold: Double = 1000.0): ShortArray {
        val rms = calculateRMS(audioData)
        return if (rms > threshold) audioData else ShortArray(audioData.size)
    }
}
