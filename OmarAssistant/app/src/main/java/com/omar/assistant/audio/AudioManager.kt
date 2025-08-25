package com.omar.assistant.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Manages audio recording and provides audio streams
 * Handles microphone access and audio format configuration
 */
class AudioManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioManager"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE_FACTOR = 2
    }
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    ) * BUFFER_SIZE_FACTOR
    
    /**
     * Starts continuous audio recording and returns a flow of audio data
     */
    fun startRecording(): Flow<ShortArray> = flow {
        try {
            if (isRecording) {
                Log.w(TAG, "Already recording")
                return@flow
            }
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return@flow
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            val buffer = ShortArray(bufferSize / 2) // 16-bit samples
            
            while (isRecording) {
                val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (readResult > 0) {
                    // Create a copy of the buffer with actual data
                    val audioData = buffer.copyOf(readResult)
                    emit(audioData)
                } else {
                    Log.w(TAG, "Audio read failed: $readResult")
                }
                
                // Small delay to prevent excessive CPU usage
                delay(10)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during audio recording", e)
        } finally {
            stopRecording()
        }
    }
    
    /**
     * Stops audio recording
     */
    fun stopRecording() {
        try {
            isRecording = false
            recordingJob?.cancel()
            
            audioRecord?.apply {
                try {
                    if (state == AudioRecord.STATE_INITIALIZED) {
                        stop()
                    }
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "AudioRecord was already stopped or in invalid state", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping AudioRecord", e)
                }
                
                try {
                    release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing AudioRecord", e)
                }
            }
            audioRecord = null
            
            Log.d(TAG, "Audio recording stopped")
            
            // Add a small delay to ensure complete cleanup
            Thread.sleep(50)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio recording", e)
        }
    }
    
    /**
     * Records audio for a specific duration
     */
    suspend fun recordForDuration(durationMs: Long): ShortArray = withContext(Dispatchers.IO) {
        val audioData = mutableListOf<Short>()
        val samplesNeeded = (SAMPLE_RATE * durationMs / 1000).toInt()
        
        try {
            val tempAudioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (tempAudioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Temporary AudioRecord initialization failed")
                return@withContext ShortArray(0)
            }
            
            tempAudioRecord.startRecording()
            val buffer = ShortArray(bufferSize / 2)
            
            var samplesCollected = 0
            while (samplesCollected < samplesNeeded) {
                val readResult = tempAudioRecord.read(buffer, 0, buffer.size)
                if (readResult > 0) {
                    val samplesToAdd = minOf(readResult, samplesNeeded - samplesCollected)
                    for (i in 0 until samplesToAdd) {
                        audioData.add(buffer[i])
                    }
                    samplesCollected += samplesToAdd
                }
            }
            
            tempAudioRecord.stop()
            tempAudioRecord.release()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during timed recording", e)
        }
        
        audioData.toShortArray()
    }
    
    fun isCurrentlyRecording(): Boolean = isRecording
}
