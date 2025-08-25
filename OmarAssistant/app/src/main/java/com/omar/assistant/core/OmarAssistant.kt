package com.omar.assistant.core

import android.content.Context
import android.util.Log
import com.omar.assistant.audio.AudioProcessor
import com.omar.assistant.audio.WakeWordDetector
import com.omar.assistant.audio.VoiceActivityDetector
import com.omar.assistant.speech.SpeechToTextProcessor
import com.omar.assistant.speech.TextToSpeechProcessor
import com.omar.assistant.nlp.LocalNLUProcessor
import com.omar.assistant.toolbox.ToolboxManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * OmarAssistant - Main orchestrator class for the voice assistant
 * 
 * This class coordinates all the components:
 * - Audio processing and wake word detection
 * - Voice activity detection
 * - Speech-to-text conversion
 * - Natural language understanding
 * - Command execution via toolbox
 * - Text-to-speech responses
 */
class OmarAssistant(private val context: Context) {
    
    private val TAG = "OmarAssistant"
    
    // Core components
    private val audioProcessor = AudioProcessor(context)
    private val wakeWordDetector = WakeWordDetector(context)
    private val vadDetector = VoiceActivityDetector()
    private val speechToText = SpeechToTextProcessor(context)
    private val textToSpeech = TextToSpeechProcessor(context)
    private val nluProcessor = LocalNLUProcessor(context)
    private val toolboxManager = ToolboxManager(context)
    
    // Coroutine management
    private val assistantScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var listeningJob: Job? = null
    
    // State management
    private val _stateFlow = MutableStateFlow(AssistantState.IDLE)
    val stateFlow: StateFlow<AssistantState> = _stateFlow.asStateFlow()
    
    private val _commandFlow = MutableSharedFlow<String>()
    val commandFlow: SharedFlow<String> = _commandFlow.asSharedFlow()
    
    private val _responseFlow = MutableSharedFlow<String>()
    val responseFlow: SharedFlow<String> = _responseFlow.asSharedFlow()
    
    // Configuration
    private val wakeWords = listOf("omar", "عمر", "3umar")
    private val commandTimeoutMs = 8000L // 8 seconds to speak command after wake word
    private val wakeWordCooldownMs = 3000L // 3 seconds cooldown after processing a wake word
    private var lastWakeWordTime = 0L
    private val minSilenceBetweenWakeWords = 1000L // Require 1 second of silence before detecting another wake word
    
    // Timeout management
    private var consecutiveTimeouts = 0
    private val maxConsecutiveTimeouts = 2 // After 2 timeouts, go back to wake word listening
    
    init {
        initializeComponents()
    }
    
    private fun initializeComponents() {
        // Initialize TTS engine
        textToSpeech.initialize { success ->
            if (success) {
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
        }
        
        // Initialize toolbox with default commands
        toolboxManager.registerDefaultTools()
    }
    
    /**
     * Start the voice assistant listening loop
     */
    suspend fun startListening() {
        if (listeningJob?.isActive == true) {
            Log.w(TAG, "Already listening")
            return
        }

        Log.d(TAG, "Starting Omar Assistant")
        consecutiveTimeouts = 0 // Reset timeout counter
        updateState(AssistantState.LISTENING_FOR_WAKE_WORD)
        
        listeningJob = assistantScope.launch {
            try {
                audioProcessor.startRecording()
                mainListeningLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Error in listening loop", e)
                updateState(AssistantState.IDLE)
            }
        }
    }    /**
     * Stop the voice assistant
     */
    suspend fun stopListening() {
        Log.d(TAG, "Stopping Omar Assistant")
        listeningJob?.cancel()
        audioProcessor.stopRecording()
        updateState(AssistantState.IDLE)
    }
    
    /**
     * Process a debug command (for testing without voice input)
     */
    suspend fun processDebugCommand(command: String) {
        Log.d(TAG, "Processing debug command: $command")
        processCommand(command)
    }
    
    /**
     * Enable/disable debug logging for wake word detection
     */
    fun setDebugMode(enabled: Boolean) {
        Log.d(TAG, "Debug mode set to: $enabled")
        // You can add more debug flags here if needed
    }
    
    /**
     * Main listening loop - continuously listen for wake word
     */
    private suspend fun mainListeningLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                // Listen for wake word
                val wakeWordDetected = detectWakeWord()
                Log.d(TAG, "mainListeningLoop: Wake word detection result = $wakeWordDetected")
                
                if (wakeWordDetected) {
                    Log.d(TAG, "mainListeningLoop: Processing wake word detection")
                    updateState(AssistantState.WAKE_WORD_DETECTED)
                    
                    // No audio cue - go straight to listening for command
                    _responseFlow.emit("Listening...")
                    
                    // Listen for command
                    val command = listenForCommand()
                    if (command.isNotEmpty()) {
                        consecutiveTimeouts = 0 // Reset timeout counter on success
                        processCommand(command)
                    } else {
                        consecutiveTimeouts++
                        Log.d(TAG, "mainListeningLoop: No command detected (timeout #$consecutiveTimeouts)")
                        
                        if (consecutiveTimeouts <= maxConsecutiveTimeouts) {
                            val message = if (consecutiveTimeouts == 1) {
                                "I didn't hear anything. Try again."
                            } else {
                                "Still listening. Please speak your command."
                            }
                            _responseFlow.emit(message)
                            textToSpeech.speak(message)
                            delay(3000)
                        } else {
                            // Too many timeouts, go back to wake word listening
                            Log.d(TAG, "mainListeningLoop: Too many timeouts, returning to wake word listening")
                            consecutiveTimeouts = 0
                            val message = "Going back to wake word listening."
                            _responseFlow.emit(message)
                            textToSpeech.speak(message)
                            delay(2000)
                        }
                    }
                }
                
                // Return to listening for wake word
                updateState(AssistantState.LISTENING_FOR_WAKE_WORD)
                delay(100) // Small delay to prevent excessive CPU usage
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in main listening loop", e)
                delay(1000) // Wait before retrying
            }
        }
    }
    
    /**
     * Detect wake word in audio stream
     */
    private suspend fun detectWakeWord(): Boolean {
        return withContext(Dispatchers.IO) {
            // Check cooldown period to prevent immediate re-detection
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastWakeWordTime < wakeWordCooldownMs) {
                // Only log occasionally to avoid spam
                if (currentTime % 5000 < 100) {
                    Log.d(TAG, "detectWakeWord: Still in cooldown period")
                }
                return@withContext false
            }
            
            val audioData = audioProcessor.getLatestAudioData()
            
            // Log more frequently to debug the issue
            if (currentTime % 2000 < 100) { // Every ~2 seconds
                Log.d(TAG, "detectWakeWord: Got ${audioData.size} audio samples")
            }
            
            if (audioData.isNotEmpty()) {
                // Calculate energy for debugging
                val energy = audioData.map { it.toDouble() * it.toDouble() }.sum() / audioData.size
                
                // First check if there's actual voice activity
                val hasVoiceActivity = vadDetector.isSpeechPresent(audioData)
                
                // Log more details for debugging the wake word detection issue
                if (energy > 10000 || hasVoiceActivity) { // Log when there's any significant audio
                    Log.d(TAG, "detectWakeWord: Energy: $energy, Voice activity: $hasVoiceActivity, samples: ${audioData.size}")
                }
                
                if (!hasVoiceActivity) {
                    // No voice activity, so definitely no wake word
                    return@withContext false
                }
                
                // Use both keyword spotting and simple string matching
                val detected = wakeWordDetector.detectWakeWord(audioData, wakeWords)
                
                // TEMPORARY: Add energy-based fallback for debugging
                val energyBasedDetection = energy > 15000 // Lowered energy threshold for normal speaking volume
                
                if (detected) {
                    Log.d(TAG, "detectWakeWord: Wake word detected by algorithm")
                } else if (energyBasedDetection && hasVoiceActivity) {
                    Log.d(TAG, "detectWakeWord: Wake word detected by energy fallback (energy: $energy)")
                }
                
                val finalDetection = detected || (energyBasedDetection && hasVoiceActivity)
                Log.d(TAG, "detectWakeWord: Final result = $finalDetection (algorithm: $detected, energy fallback: $energyBasedDetection)")
                
                if (finalDetection) {
                    lastWakeWordTime = currentTime
                    Log.d(TAG, "detectWakeWord: Wake word confirmed")
                }
                
                finalDetection
            } else {
                // Log audio data issues more frequently
                if (currentTime % 3000 < 100) {
                    Log.d(TAG, "detectWakeWord: No audio data available - check microphone permissions/initialization")
                }
                false
            }
        }
    }
    
    /**
     * Listen for voice command after wake word detection
     */
    private suspend fun listenForCommand(): String {
        updateState(AssistantState.LISTENING_FOR_COMMAND)
        
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "listenForCommand: Starting to listen for command")
                
                // Simplified approach: wait for any speech and capture it
                val startTime = System.currentTimeMillis()
                var speechDetected = false
                
                Log.d(TAG, "listenForCommand: Waiting for speech (timeout: ${commandTimeoutMs}ms)")
                
                // Look for speech activity - much simpler approach
                while (System.currentTimeMillis() - startTime < commandTimeoutMs) {
                    val audioData = audioProcessor.getLatestAudioData(500) // Get 500ms of audio
                    
                    if (audioData.isNotEmpty()) {
                        // Calculate simple energy for debugging
                        val energy = audioData.map { it.toDouble() * it.toDouble() }.sum() / audioData.size
                        val hasSpeech = vadDetector.isSpeechPresent(audioData)
                        
                        // Log occasionally for debugging
                        if (System.currentTimeMillis() % 1000 < 200) { // Log every ~1 second
                            Log.d(TAG, "listenForCommand: Energy: $energy, VAD says speech: $hasSpeech, audioSize: ${audioData.size}")
                        }
                        
                        // Use either VAD or energy threshold (much more permissive for normal speaking volume)
                        if (hasSpeech || energy > 8000) {
                            speechDetected = true
                            Log.d(TAG, "listenForCommand: Speech detected! Energy: $energy, VAD: $hasSpeech")
                            break
                        }
                    } else {
                        Log.d(TAG, "listenForCommand: No audio data available")
                    }
                    
                    delay(200) // Check every 200ms
                }
                
                if (!speechDetected) {
                    Log.d(TAG, "listenForCommand: No speech detected within timeout")
                    return@withContext ""
                }
                
                // Give user a moment to continue speaking
                delay(200)
                
                // Capture speech for command
                updateState(AssistantState.PROCESSING_COMMAND)
                Log.d(TAG, "listenForCommand: Capturing speech for 4 seconds")
                val speechAudio = audioProcessor.captureAudioForDuration(4000)
                
                // Convert speech to text
                val result = speechToText.processAudio(speechAudio)
                Log.d(TAG, "listenForCommand: Speech-to-text result: '$result'")
                
                result
                
            } catch (e: Exception) {
                Log.e(TAG, "Error listening for command", e)
                ""
            }
        }
    }
    
    /**
     * Process the recognized command
     */
    private suspend fun processCommand(command: String) {
        Log.d(TAG, "Processing command: $command")
        _commandFlow.emit(command)
        
        try {
            // Understand the intent using local NLU
            val intent = nluProcessor.processCommand(command)
            
            // Execute the command using toolbox
            val result = toolboxManager.executeCommand(intent)
            
            // Speak the response
            updateState(AssistantState.SPEAKING_RESPONSE)
            _responseFlow.emit(result.response)
            textToSpeech.speak(result.response)
            
            // Wait for TTS to complete
            delay(result.response.length * 100L) // Rough estimate
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing command", e)
            val errorResponse = "Sorry, I couldn't process that command."
            _responseFlow.emit(errorResponse)
            textToSpeech.speak(errorResponse)
        }
    }
    
    /**
     * Update the assistant state
     */
    private fun updateState(newState: AssistantState) {
        Log.d(TAG, "State: ${_stateFlow.value} -> $newState")
        _stateFlow.value = newState
    }
    
    /**
     * Clean up resources
     */
    suspend fun cleanup() {
        Log.d(TAG, "Cleaning up Omar Assistant")
        stopListening()
        textToSpeech.cleanup()
        audioProcessor.cleanup()
        assistantScope.cancel()
    }
}
