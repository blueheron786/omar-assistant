package com.example.omarassistant.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * Voice recorder for capturing user commands after wake word detection
 * Implements automatic silence detection to determine when user finished speaking
 */
class VoiceRecorder(
    private var vadSensitivity: Float = 0.5f
) {
    
    companion object {
        private const val TAG = "VoiceRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = 1024
        
        // Recording parameters
        private const val MAX_RECORDING_DURATION_MS = 5000L // 5 seconds max (reduced from 10s)
        private const val SILENCE_THRESHOLD_MS = 3000L // 3 seconds of silence to stop (increased from 1.5s)
        private const val MIN_RECORDING_DURATION_MS = 200L // Minimum 0.2 seconds (reduced from 0.5s)
    }
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    
    // Audio data collection
    private val audioData = ByteArrayOutputStream()
    private val audioBuffer = ShortArray(BUFFER_SIZE)
    
    // Silence detection
    private var silenceStartTime = 0L
    private var lastSoundTime = 0L
    
    // Callbacks
    var onRecordingStarted: (() -> Unit)? = null
    var onRecordingFinished: ((ByteArray) -> Unit)? = null
    var onRecordingCancelled: (() -> Unit)? = null
    var onVoiceActivityChanged: ((Boolean) -> Unit)? = null
    var onAudioLevelChanged: ((Float) -> Unit)? = null
    
    /**
     * Start recording user voice command
     */
    suspend fun startRecording() = withContext(Dispatchers.IO) {
        if (isRecording) return@withContext
        
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
            
            // Reset recording state
            audioData.reset()
            silenceStartTime = 0L
            lastSoundTime = System.currentTimeMillis()
            isRecording = true
            
            audioRecord?.startRecording()
            
            withContext(Dispatchers.Main) {
                onRecordingStarted?.invoke()
            }
            
            Log.d(TAG, "Started recording voice command")
            
            // Start recording process
            recordingJob = launch {
                recordAudioLoop()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice recording", e)
            stopRecording()
        }
    }
    
    /**
     * Stop recording manually
     */
    suspend fun stopRecording() = withContext(Dispatchers.IO) {
        if (!isRecording) return@withContext
        
        isRecording = false
        recordingJob?.cancel()
        
        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping voice recording", e)
            }
        }
        audioRecord = null
        
        // Return recorded audio if we have enough
        val recordedData = audioData.toByteArray()
        val recordingDuration = recordedData.size * 1000L / (SAMPLE_RATE * 2) // 16-bit = 2 bytes per sample
        
        Log.d(TAG, "Processing recording - Duration: ${recordingDuration}ms, Size: ${recordedData.size} bytes, Min required: ${MIN_RECORDING_DURATION_MS}ms")
        Log.d(TAG, "Recording check: duration>=min? ${recordingDuration >= MIN_RECORDING_DURATION_MS}, notEmpty? ${recordedData.isNotEmpty()}")
        
        // Use immediate callback without context switching to prevent hangs
        try {
            if (recordingDuration >= MIN_RECORDING_DURATION_MS && recordedData.isNotEmpty()) {
                Log.d(TAG, "Calling onRecordingFinished with ${recordedData.size} bytes")
                
                // Post to main thread separately to avoid blocking
                Handler(Looper.getMainLooper()).post {
                    try {
                        onRecordingFinished?.invoke(recordedData)
                        Log.d(TAG, "Recording finished callback completed - Duration: ${recordingDuration}ms, Size: ${recordedData.size} bytes")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onRecordingFinished callback", e)
                        try {
                            onRecordingCancelled?.invoke()
                        } catch (e2: Exception) {
                            Log.e(TAG, "Error in onRecordingCancelled callback", e2)
                        }
                    }
                }
            } else {
                Log.d(TAG, "Calling onRecordingCancelled - duration too short or empty")
                
                Handler(Looper.getMainLooper()).post {
                    try {
                        onRecordingCancelled?.invoke()
                        Log.d(TAG, "Recording cancelled callback completed - too short or empty")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onRecordingCancelled callback", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up recording callback", e)
            Handler(Looper.getMainLooper()).post {
                try {
                    onRecordingCancelled?.invoke()
                } catch (e2: Exception) {
                    Log.e(TAG, "Error in fallback callback", e2)
                }
            }
        }
    }
    
    /**
     * Cancel recording without returning data
     */
    suspend fun cancelRecording() = withContext(Dispatchers.IO) {
        if (!isRecording) return@withContext
        
        isRecording = false
        recordingJob?.cancel()
        
        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling voice recording", e)
            }
        }
        audioRecord = null
        
        withContext(Dispatchers.Main) {
            onRecordingCancelled?.invoke()
        }
        
        Log.d(TAG, "Recording cancelled")
    }
    
    /**
     * Update VAD sensitivity
     */
    fun updateVadSensitivity(sensitivity: Float) {
        this.vadSensitivity = sensitivity.coerceIn(0.1f, 1.0f)
        Log.d(TAG, "Updated VAD sensitivity to: $vadSensitivity")
    }
    
    /**
     * Main recording loop with automatic silence detection
     */
    private suspend fun recordAudioLoop() = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        while (isRecording && audioRecord != null) {
            try {
                val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                
                if (bytesRead > 0) {
                    // Convert to byte array and store
                    val byteData = ByteArray(bytesRead * 2)
                    for (i in 0 until bytesRead) {
                        val sample = audioBuffer[i].toInt()
                        byteData[i * 2] = (sample and 0xFF).toByte()
                        byteData[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
                    }
                    audioData.write(byteData)
                    
                    // Calculate audio energy for VAD
                    val energy = calculateAudioEnergy(audioBuffer, bytesRead)
                    val normalizedLevel = (energy / 32768.0f).coerceIn(0f, 1f)
                    // Use fixed threshold consistent with AudioProcessor (100 for VAD)
                    val energyThreshold = 100f
                    val isVoiceActive = energy > energyThreshold
                    
                    // Update voice activity state
                    val currentTime = System.currentTimeMillis()
                    
                    if (isVoiceActive) {
                        lastSoundTime = currentTime
                        if (silenceStartTime > 0) {
                            Log.d(TAG, "Voice detected, resetting silence timer. Energy: $energy")
                            silenceStartTime = 0L // Reset silence timer
                        }
                    } else {
                        if (silenceStartTime == 0L && currentTime - lastSoundTime > 100) {
                            Log.d(TAG, "Silence started. Energy: $energy, threshold: $energyThreshold")
                            silenceStartTime = currentTime // Start silence timer
                        }
                    }
                    
                    // Notify listeners
                    withContext(Dispatchers.Main) {
                        onVoiceActivityChanged?.invoke(isVoiceActive)
                        onAudioLevelChanged?.invoke(normalizedLevel)
                    }
                    
                    // Check for stopping conditions
                    val recordingDuration = currentTime - startTime
                    val silenceDuration = if (silenceStartTime > 0) currentTime - silenceStartTime else 0L
                    
                    // Stop if max duration reached
                    if (recordingDuration >= MAX_RECORDING_DURATION_MS) {
                        Log.d(TAG, "Max recording duration reached")
                        break
                    }
                    
                    // Stop if silence detected and minimum duration met
                    if (silenceDuration >= SILENCE_THRESHOLD_MS && 
                        recordingDuration >= MIN_RECORDING_DURATION_MS) {
                        Log.d(TAG, "Silence detected - stopping recording. Silence: ${silenceDuration}ms, Recording: ${recordingDuration}ms")
                        break
                    }
                }
                
                // Small delay to prevent excessive CPU usage
                delay(10)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in recording loop", e)
                break
            }
        }
        
        // Stop recording when loop exits
        if (isRecording) {
            stopRecording()
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
     * Get current recording state
     */
    fun isCurrentlyRecording(): Boolean = isRecording
    
    /**
     * Get current recording duration
     */
    fun getCurrentRecordingDuration(): Long {
        if (!isRecording) return 0L
        return audioData.size() * 1000L / (SAMPLE_RATE * 2)
    }
}
