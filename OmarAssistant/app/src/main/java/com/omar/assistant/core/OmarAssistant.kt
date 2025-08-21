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
    private val commandTimeoutMs = 5000L // 5 seconds to speak command after wake word
    private val wakeWordCooldownMs = 3000L // 3 seconds cooldown after processing a wake word
    private var lastWakeWordTime = 0L
    private val minSilenceBetweenWakeWords = 1000L // Require 1 second of silence before detecting another wake word
    
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
    }
    
    /**
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
                    _responseFlow.emit("Yes?")
                    
                    // Brief pause to let user know we heard them
                    delay(500)
                    
                    // Listen for command
                    val command = listenForCommand()
                    if (command.isNotEmpty()) {
                        processCommand(command)
                    } else {
                        Log.d(TAG, "mainListeningLoop: No command detected after wake word")
                        _responseFlow.emit("I didn't hear anything. Try again.")
                        textToSpeech.speak("I didn't hear anything. Try again.")
                        
                        // Wait longer before listening for wake word again to avoid immediate cycling
                        delay(2000) // Wait 2 seconds to let TTS finish and give user time
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
                Log.d(TAG, "detectWakeWord: Still in cooldown period")
                return@withContext false
            }
            
            val audioData = audioProcessor.getLatestAudioData()
            Log.d(TAG, "detectWakeWord: Got ${audioData.size} audio samples")
            
            if (audioData.isNotEmpty()) {
                // First check if there's actual voice activity
                val hasVoiceActivity = vadDetector.isSpeechPresent(audioData)
                Log.d(TAG, "detectWakeWord: Voice activity detected = $hasVoiceActivity")
                
                if (!hasVoiceActivity) {
                    // No voice activity, so definitely no wake word
                    return@withContext false
                }
                
                // Use both keyword spotting and simple string matching
                val detected = wakeWordDetector.detectWakeWord(audioData, wakeWords)
                Log.d(TAG, "detectWakeWord: Wake word detection result = $detected")
                
                if (detected) {
                    lastWakeWordTime = currentTime
                    Log.d(TAG, "detectWakeWord: Wake word confirmed with voice activity")
                }
                
                detected
            } else {
                Log.d(TAG, "detectWakeWord: No audio data available")
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
                // Wait for voice activity
                val startTime = System.currentTimeMillis()
                var speechDetected = false
                
                while (System.currentTimeMillis() - startTime < commandTimeoutMs) {
                    val audioData = audioProcessor.getLatestAudioData()
                    
                    if (vadDetector.isSpeechPresent(audioData)) {
                        speechDetected = true
                        break
                    }
                    
                    delay(50)
                }
                
                if (!speechDetected) {
                    Log.d(TAG, "No speech detected within timeout")
                    return@withContext ""
                }
                
                // Capture speech for a few seconds
                updateState(AssistantState.PROCESSING_COMMAND)
                val speechAudio = audioProcessor.captureAudioForDuration(3000) // 3 seconds
                
                // Convert speech to text
                speechToText.processAudio(speechAudio)
                
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
