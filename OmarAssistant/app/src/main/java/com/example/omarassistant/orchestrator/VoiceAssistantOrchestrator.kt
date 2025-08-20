package com.example.omarassistant.orchestrator

import android.content.Context
import android.util.Log
import com.example.omarassistant.api.GeminiApiService
import com.example.omarassistant.audio.AudioProcessor
import com.example.omarassistant.audio.VoiceRecorder
import com.example.omarassistant.config.ConfigManager
import com.example.omarassistant.model.AudioState
import com.example.omarassistant.model.AssistantConfig
import com.example.omarassistant.model.ExecutionResult
import com.example.omarassistant.model.VoiceCommand
import com.example.omarassistant.tts.BeepType
import com.example.omarassistant.tts.TextToSpeechEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Main orchestrator that coordinates all OMAR assistant components
 * Handles wake word detection, voice recording, LLM processing, and response generation
 */
class VoiceAssistantOrchestrator(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceOrchestrator"
    }
    
    // Core components
    private val configManager = ConfigManager.getInstance(context)
    private var geminiApiService: GeminiApiService? = null
    private val toolboxManager = ToolboxManager(context)
    private val ttsEngine = TextToSpeechEngine(context)
    private var audioProcessor: AudioProcessor? = null
    private var voiceRecorder: VoiceRecorder? = null
    
    // State management
    private val _state = MutableStateFlow(AudioState.IDLE)
    val state: StateFlow<AudioState> = _state.asStateFlow()
    
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    
    private val _lastResponse = MutableStateFlow("")
    val lastResponse: StateFlow<String> = _lastResponse.asStateFlow()
    
    // Configuration
    private var currentConfig: AssistantConfig? = null
    private val orchestratorScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Callbacks
    var onWakeWordDetected: (() -> Unit)? = null
    var onCommandProcessed: ((VoiceCommand, String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    /**
     * Initialize the orchestrator with current configuration
     */
    suspend fun initialize() {
        try {
            currentConfig = configManager.getConfig()
            currentConfig?.let { config ->
                
                // Initialize Gemini API service
                if (config.geminiApiKey.isNotEmpty()) {
                    geminiApiService = GeminiApiService(config.geminiApiKey)
                } else {
                    Log.w(TAG, "No Gemini API key configured")
                }
                
                // Initialize TTS
                ttsEngine.initialize()
                
                // Initialize audio components
                setupAudioComponents(config)
                
                Log.d(TAG, "Orchestrator initialized successfully")
            } ?: run {
                throw Exception("Failed to load configuration")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing orchestrator", e)
            onError?.invoke("Failed to initialize OMAR: ${e.message}")
        }
    }
    
    /**
     * Start the voice assistant
     */
    suspend fun start() {
        if (_isActive.value) return
        
        try {
            updateState(AudioState.LISTENING_FOR_WAKE_WORD)
            audioProcessor?.startListening()
            _isActive.value = true
            
            Log.d(TAG, "Voice assistant started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice assistant", e)
            onError?.invoke("Failed to start listening: ${e.message}")
            updateState(AudioState.IDLE)
        }
    }
    
    /**
     * Stop the voice assistant
     */
    fun stop() {
        orchestratorScope.launch {
            try {
                audioProcessor?.stopListening()
                voiceRecorder?.cancelRecording()
                ttsEngine.stop()
                _isActive.value = false
                updateState(AudioState.IDLE)
                
                Log.d(TAG, "Voice assistant stopped")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping voice assistant", e)
            }
        }
    }
    
    /**
     * Process text command directly (for testing or manual input)
     */
    suspend fun processTextCommand(text: String) {
        if (text.isBlank()) return
        
        try {
            updateState(AudioState.PROCESSING)
            
            val command = VoiceCommand(
                originalText = text,
                intent = "manual_input",
                confidence = 1.0f
            )
            
            val response = processCommand(command)
            speakResponse(response)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing text command", e)
            speakResponse("I had trouble processing that command.")
        } finally {
            returnToListening()
        }
    }
    
    /**
     * Update configuration
     */
    suspend fun updateConfiguration(config: AssistantConfig) {
        try {
            configManager.saveConfig(config)
            currentConfig = config
            
            // Update API service
            if (config.geminiApiKey.isNotEmpty()) {
                geminiApiService?.updateApiKey(config.geminiApiKey)
                    ?: run { geminiApiService = GeminiApiService(config.geminiApiKey) }
            }
            
            // Update audio components
            audioProcessor?.updateWakeWord(config.wakeWord)
            audioProcessor?.updateSensitivity(config.wakeWordSensitivity, config.vadSensitivity)
            voiceRecorder?.updateVadSensitivity(config.vadSensitivity)
            
            // Update TTS
            ttsEngine.setVolume(config.voiceVolume)
            
            Log.d(TAG, "Configuration updated")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating configuration", e)
            onError?.invoke("Failed to update settings: ${e.message}")
        }
    }
    
    /**
     * Setup audio processing components
     */
    private suspend fun setupAudioComponents(config: AssistantConfig) {
        // Setup audio processor for wake word detection
        audioProcessor = AudioProcessor(
            wakeWord = config.wakeWord,
            wakeWordSensitivity = config.wakeWordSensitivity,
            vadSensitivity = config.vadSensitivity
        ).apply {
            onWakeWordDetected = { handleWakeWordDetected() }
            onVoiceActivityDetected = { /* Handle VAD if needed */ }
            onAudioLevelChanged = { level -> _audioLevel.value = level }
        }
        
        // Setup voice recorder for command capture
        voiceRecorder = VoiceRecorder(config.vadSensitivity).apply {
            onRecordingStarted = { updateState(AudioState.RECORDING_COMMAND) }
            onRecordingFinished = { audioData -> handleVoiceRecorded(audioData) }
            onRecordingCancelled = { orchestratorScope.launch { returnToListening() } }
            onVoiceActivityChanged = { /* Handle if needed */ }
            onAudioLevelChanged = { level -> _audioLevel.value = level }
        }
    }
    
    /**
     * Handle wake word detection
     */
    private fun handleWakeWordDetected() {
        orchestratorScope.launch {
            try {
                Log.d(TAG, "Wake word detected!")
                onWakeWordDetected?.invoke()
                
                updateState(AudioState.WAKE_WORD_DETECTED)
                
                // Provide audio feedback
                ttsEngine.playBeep(BeepType.WAKE_WORD)
                
                // Small delay before starting recording
                delay(200)
                
                // Start recording user command
                voiceRecorder?.startRecording()
                
                // Audio feedback for recording start
                delay(100)
                ttsEngine.playBeep(BeepType.COMMAND_START)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error handling wake word detection", e)
                returnToListening()
            }
        }
    }
    
    /**
     * Handle recorded voice data
     */
    private fun handleVoiceRecorded(audioData: ByteArray) {
        orchestratorScope.launch {
            try {
                updateState(AudioState.PROCESSING)
                
                // Audio feedback for recording end
                ttsEngine.playBeep(BeepType.COMMAND_END)
                
                // Convert audio to text (simplified - in production use proper STT)
                val text = simulateSpeechToText(audioData)
                
                if (text.isNotEmpty()) {
                    val command = VoiceCommand(
                        originalText = text,
                        intent = "voice_command",
                        confidence = 0.8f
                    )
                    
                    Log.d(TAG, "Processing voice command: $text")
                    
                    val response = processCommand(command)
                    
                    // Success beep before speaking response
                    ttsEngine.playBeep(BeepType.SUCCESS)
                    delay(300) // Brief pause after success beep
                    
                    speakResponse(response)
                    
                    onCommandProcessed?.invoke(command, response)
                    
                } else {
                    // Error beep for unrecognized speech
                    ttsEngine.playBeep(BeepType.ERROR)
                    speakResponse("I didn't catch that. Could you please repeat?")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing voice recording", e)
                // Error beep for processing failure
                ttsEngine.playBeep(BeepType.ERROR)
                speakResponse("I had trouble understanding that. Please try again.")
            } finally {
                returnToListening()
            }
        }
    }
    
    /**
     * Process a voice command using LLM
     */
    private suspend fun processCommand(command: VoiceCommand): String {
        return try {
            val apiService = geminiApiService
            if (apiService == null) {
                return "I'm not connected to my brain right now. Please configure the API key in settings."
            }
            
            val llmResponse = apiService.processText(command.originalText)
            
            if (llmResponse.requiresExecution && llmResponse.functionCall != null) {
                // Execute the function and return its result
                val executionResult = toolboxManager.executeFunction(llmResponse.functionCall)
                executionResult.message
            } else {
                // Return the LLM's text response
                llmResponse.text
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing command with LLM", e)
            "I'm having trouble thinking right now. Please try again in a moment."
        }
    }
    
    /**
     * Speak response using TTS
     */
    private suspend fun speakResponse(response: String) {
        try {
            updateState(AudioState.SPEAKING)
            _lastResponse.value = response
            
            ttsEngine.speak(response) {
                // Called when TTS finishes
                orchestratorScope.launch {
                    returnToListening()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking response", e)
            returnToListening()
        }
    }
    
    /**
     * Return to listening state
     */
    private suspend fun returnToListening() {
        if (_isActive.value && currentConfig?.enableContinuousListening == true) {
            updateState(AudioState.LISTENING_FOR_WAKE_WORD)
        } else {
            updateState(AudioState.IDLE)
        }
    }
    
    /**
     * Update current state
     */
    private fun updateState(newState: AudioState) {
        _state.value = newState
        Log.d(TAG, "State changed to: $newState")
    }
    
    /**
     * Simplified speech-to-text simulation
     * In production, integrate with Google Speech-to-Text API or similar
     */
    private fun simulateSpeechToText(audioData: ByteArray): String {
        // This is a placeholder - in a real app you would:
        // 1. Send audio to Google Speech-to-Text API
        // 2. Or use Android's SpeechRecognizer
        // 3. Or integrate with Whisper API
        
        // For demo purposes, simulate some common commands based on audio characteristics
        val duration = audioData.size / (16000 * 2) // Approximate duration in seconds
        
        return when {
            duration < 1 -> ""
            duration < 2 -> "hello"
            duration < 3 -> listOf("turn on the lights", "what's the weather", "set volume to fifty").random()
            duration < 5 -> listOf("turn off the living room light", "set thermostat to seventy two", "turn on flashlight").random()
            else -> listOf("what's the weather like today", "turn on the lights in the bedroom", "set a timer for five minutes").random()
        }
    }
    
    /**
     * Get current configuration
     */
    fun getCurrentConfig(): AssistantConfig? = currentConfig
    
    /**
     * Test API connection
     */
    suspend fun testApiConnection(): Boolean {
        return try {
            geminiApiService?.testConnection() ?: false
        } catch (e: Exception) {
            Log.e(TAG, "API connection test failed", e)
            false
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        orchestratorScope.cancel()
        stop()
        ttsEngine.cleanup()
    }
}
