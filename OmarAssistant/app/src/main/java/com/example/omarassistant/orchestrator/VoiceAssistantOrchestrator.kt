package com.example.omarassistant.orchestrator

import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    
    // State management
    private val _state = MutableStateFlow(AudioState.IDLE)
    val state: StateFlow<AudioState> = _state.asStateFlow()
    
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    
    private val _lastResponse = MutableStateFlow("")
    val lastResponse: StateFlow<String> = _lastResponse.asStateFlow()
    
    // Recovery mechanism
    private var recoveryJob: Job? = null
    
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
                setupSpeechRecognizer(config)
                
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
        if (_isActive.value) {
            Log.d(TAG, "Voice assistant already active, skipping start")
            return
        }
        
        Log.d(TAG, "Starting voice assistant...")
        
        try {
            updateState(AudioState.LISTENING_FOR_WAKE_WORD)
            
            Log.d(TAG, "AudioProcessor instance: ${if (audioProcessor != null) "available" else "null"}")
            
            if (audioProcessor == null) {
                Log.e(TAG, "AudioProcessor is null! Cannot start listening.")
                onError?.invoke("AudioProcessor not initialized")
                updateState(AudioState.IDLE)
                return
            }
            
            Log.d(TAG, "Calling audioProcessor.startListening()...")
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
            Log.i(TAG, "📝 UNDERSTOOD (Manual): '$text'")
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
        Log.d(TAG, "Setting up audio components...")
        
        // Setup audio processor for wake word detection
        Log.d(TAG, "Creating AudioProcessor with wake word: '${config.wakeWord}', sensitivity: ${config.wakeWordSensitivity}")
        audioProcessor = AudioProcessor(
            wakeWord = config.wakeWord,
            wakeWordSensitivity = config.wakeWordSensitivity,
            vadSensitivity = config.vadSensitivity
        ).apply {
            onWakeWordDetected = { handleWakeWordDetected() }
            onVoiceActivityDetected = { /* Handle VAD if needed */ }
            onAudioLevelChanged = { level -> _audioLevel.value = level }
        }
        
        Log.d(TAG, "AudioProcessor created successfully")
        
        // Setup voice recorder for command capture
        Log.d(TAG, "Creating VoiceRecorder...")
        voiceRecorder = VoiceRecorder(config.vadSensitivity).apply {
            onRecordingStarted = { updateState(AudioState.RECORDING_COMMAND) }
            onRecordingFinished = { audioData -> 
                try {
                    handleVoiceRecorded(audioData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in VoiceRecorder callback", e)
                    orchestratorScope.launch {
                        speakResponse("I had trouble processing that. Please try again.")
                    }
                }
            }
            onRecordingCancelled = { 
                orchestratorScope.launch { 
                    Log.d(TAG, "Voice recording was cancelled")
                    speakResponse("I didn't hear anything. Please try again.")
                } 
            }
            onVoiceActivityChanged = { /* Handle if needed */ }
            onAudioLevelChanged = { level -> _audioLevel.value = level }
        }
        
        Log.d(TAG, "Audio components setup completed")
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
                
                // Pause wake listening while we capture speech
                audioProcessor?.stopListening()
                
                // Start speech recognition if available, otherwise fallback to recorder
                if (speechRecognizer != null && recognizerIntent != null) {
                    startSpeechRecognition()
                } else {
                    Log.w(TAG, "SpeechRecognizer not available, falling back to internal recorder")
                    // Audio feedback for recording start - REMOVED
                    delay(100)
                    voiceRecorder?.startRecording()
                }
                
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
                Log.d(TAG, "handleVoiceRecorded called with ${audioData.size} bytes")
                updateState(AudioState.PROCESSING)
                
                // Add timeout protection to prevent hanging
                withTimeout(10000) { // 10 second timeout
                    // Audio feedback for recording end - REMOVED
                    
                    // Convert audio to text (simplified - in production use proper STT)
                    val text = simulateSpeechToText(audioData)
                    Log.d(TAG, "simulateSpeechToText returned: '$text'")
                    
                    if (text.isNotEmpty()) {
                        Log.i(TAG, "📝 UNDERSTOOD (VoiceRecorder): '$text'")
                        val command = VoiceCommand(
                            originalText = text,
                            intent = "voice_command",
                            confidence = 0.8f
                        )
                        
                        Log.d(TAG, "Processing voice command: $text")
                        
                        val response = processCommand(command)
                        
                        // Success beep before speaking response - REMOVED
                        delay(300) // Brief pause after success beep
                        
                        speakResponse(response)
                        
                        onCommandProcessed?.invoke(command, response)
                        
                    } else {
                        Log.w(TAG, "📝 UNDERSTOOD (VoiceRecorder): (empty)")
                        // Error beep for unrecognized speech - REMOVED
                        speakResponse("I didn't catch that. Could you please repeat?")
                    }
                }
                
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "VoiceRecorder processing timeout", e)
                speakResponse("I had trouble processing that. Please try again.")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing voice recording", e)
                // Error beep for processing failure - REMOVED
                speakResponse("I had trouble understanding that. Please try again.")
            }
        }
    }

    /**
     * Initialize Android SpeechRecognizer with current language settings
     */
    private fun setupSpeechRecognizer(config: AssistantConfig) {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "Speech recognition not available on this device")
                return
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "onReadyForSpeech")
                    // Command start beep - REMOVED
                }
                override fun onBeginningOfSpeech() { Log.d(TAG, "onBeginningOfSpeech") }
                override fun onRmsChanged(rmsdB: Float) { _audioLevel.value = rmsdB.coerceIn(0f, 12f) / 12f }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { Log.d(TAG, "onEndOfSpeech") }
                override fun onError(error: Int) {
                    Log.e(TAG, "Speech recognition error: $error")
                    orchestratorScope.launch {
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> {
                                Log.d(TAG, "No speech match from SpeechRecognizer, trying VoiceRecorder fallback")
                                // Don't give immediate feedback - try VoiceRecorder instead
                                updateState(AudioState.RECORDING_COMMAND)
                                try {
                                    voiceRecorder?.startRecording()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to start VoiceRecorder fallback", e)
                                    speakResponse("Sorry, I couldn't hear that clearly.")
                                }
                            }
                            else -> {
                                // For other errors, give feedback and return to listening  
                                speakResponse("Sorry, I couldn't hear that clearly.")
                            }
                        }
                    }
                }
                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    Log.d(TAG, "Speech results: $text")
                    if (text.isNotEmpty()) {
                        Log.i(TAG, "📝 UNDERSTOOD: '$text'")
                        orchestratorScope.launch {
                            updateState(AudioState.PROCESSING)
                            val command = VoiceCommand(text, "voice_command", 0.9f)
                            val response = processCommand(command)
                            // Success beep - REMOVED
                            delay(200)
                            speakResponse(response)
                            onCommandProcessed?.invoke(command, response)
                        }
                    } else {
                        Log.w(TAG, "📝 UNDERSTOOD: (empty)")
                        orchestratorScope.launch {
                            // Error beep - REMOVED
                            speakResponse("I didn't catch that. Could you repeat?")
                        }
                    }
                }
                override fun onPartialResults(partialResults: Bundle) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val locale = when (config.language.lowercase()) {
                "en", "en-gb", "en_uk" -> java.util.Locale.UK
                "ar" -> java.util.Locale("ar")
                "es" -> java.util.Locale("es")
                "fr" -> java.util.Locale.FRENCH
                "de" -> java.util.Locale.GERMAN
                else -> java.util.Locale.getDefault()
            }

            recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                // Silence/timeout tuning
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 700)
            }
            Log.d(TAG, "SpeechRecognizer set up with locale: ${locale}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set up SpeechRecognizer", e)
        }
    }

    private fun startSpeechRecognition() {
        try {
            if (recognizerIntent == null) {
                Log.w(TAG, "Recognizer intent not ready")
                return
            }
            val intent = recognizerIntent!!
            updateState(AudioState.RECORDING_COMMAND)
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            orchestratorScope.launch {
                // Error beep - REMOVED
                speakResponse("I couldn't start listening. Please try again.")
            }
        }
    }

    private fun stopSpeechRecognition() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition", e)
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
            
            // Ensure AudioProcessor is stopped during TTS
            audioProcessor?.stopListening()
            
            ttsEngine.speak(response) {
                // Called when TTS finishes
                orchestratorScope.launch {
                    Log.d(TAG, "TTS finished, returning to listening")
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
            // Resume wake word detection
            try { audioProcessor?.startListening() } catch (_: Exception) {}
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
        
        // Start recovery timer for non-idle states to prevent getting stuck
        recoveryJob?.cancel()
        if (newState != AudioState.IDLE && newState != AudioState.LISTENING_FOR_WAKE_WORD) {
            recoveryJob = orchestratorScope.launch {
                delay(15000) // 15 second timeout
                Log.w(TAG, "Recovery timeout triggered from state: $newState")
                if (_state.value == newState) { // Still in same state
                    Log.w(TAG, "System appears stuck in $newState, forcing recovery")
                    try {
                        // Force return to listening
                        speakResponse("Let me restart listening.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during recovery", e)
                        returnToListening()
                    }
                }
            }
        }
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
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
    }
}
