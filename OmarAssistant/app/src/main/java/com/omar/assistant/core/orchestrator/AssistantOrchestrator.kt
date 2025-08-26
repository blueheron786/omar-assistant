package com.omar.assistant.core.orchestrator

import android.util.Log
import com.omar.assistant.audio.VoiceActivityDetector
import com.omar.assistant.audio.WakeWordDetector
import com.omar.assistant.audio.WakeWordResult
import com.omar.assistant.llm.ConversationMessage
import com.omar.assistant.llm.LLMProvider
import com.omar.assistant.llm.MessageRole
import com.omar.assistant.speech.SpeechToTextManager
import com.omar.assistant.speech.TextToSpeechManager
import com.omar.assistant.toolbox.ToolboxManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Main orchestrator that coordinates all components of the voice assistant
 * Handles the complete flow from wake word detection to response generation
 */
class AssistantOrchestrator(
    private val wakeWordDetector: WakeWordDetector,
    private val voiceActivityDetector: VoiceActivityDetector,
    private val speechToTextManager: SpeechToTextManager,
    private val llmProvider: LLMProvider,
    private val toolboxManager: ToolboxManager,
    private val textToSpeechManager: TextToSpeechManager
) {
    
    companion object {
        private const val TAG = "AssistantOrchestrator"
        private const val MAX_CONVERSATION_HISTORY = 10
    }
    
    private val orchestratorScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State management
    private val _state = MutableStateFlow(AssistantState.IDLE)
    val state: StateFlow<AssistantState> = _state.asStateFlow()
    
    private val _lastResponse = MutableStateFlow<String?>(null)
    val lastResponse: StateFlow<String?> = _lastResponse.asStateFlow()
    
    // Conversation history
    private val conversationHistory = mutableListOf<ConversationMessage>()
    
    // Jobs for managing async operations
    private var wakeWordJob: Job? = null
    private var processingJob: Job? = null
    
    /**
     * Starts the assistant - begins listening for wake words
     */
    suspend fun start() {
        if (_state.value != AssistantState.IDLE) {
            Log.w(TAG, "Assistant already running")
            return
        }
        
        try {
            // Initialize TTS
            if (!textToSpeechManager.initialize()) {
                Log.e(TAG, "Failed to initialize TTS")
                return
            }
            
            // Initialize wake word detection
            if (!wakeWordDetector.initialize()) {
                Log.e(TAG, "Failed to initialize wake word detector")
                // Fallback to simple detection
                startSimpleWakeWordDetection()
            } else {
                startWakeWordDetection()
            }
            
            _state.value = AssistantState.LISTENING_FOR_WAKE_WORD
            Log.d(TAG, "Assistant started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start assistant", e)
            _state.value = AssistantState.ERROR
        }
    }
    
    /**
     * Stops the assistant
     */
    fun stop() {
        orchestratorScope.launch {
            try {
                _state.value = AssistantState.STOPPING
                
                // Stop all ongoing operations
                wakeWordJob?.cancel()
                processingJob?.cancel()
                
                // Stop components
                wakeWordDetector.stopListening()
                speechToTextManager.stopListening()
                textToSpeechManager.stop()
                
                _state.value = AssistantState.IDLE
                Log.d(TAG, "Assistant stopped")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping assistant", e)
                _state.value = AssistantState.ERROR
            }
        }
    }
    
    /**
     * Processes user input directly (bypassing wake word detection)
     */
    suspend fun processDirectInput(userInput: String) {
        if (_state.value == AssistantState.PROCESSING) {
            Log.w(TAG, "Already processing input")
            return
        }
        
        processingJob = orchestratorScope.launch {
            try {
                processUserInput(userInput)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing direct input", e)
                handleError("I encountered an error processing your request.")
            }
        }
    }
    
    private fun startWakeWordDetection() {
        wakeWordJob = orchestratorScope.launch {
            try {
                wakeWordDetector.wakeWordDetected.collect { wakeWordResult ->
                    handleWakeWordDetected(wakeWordResult)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in wake word detection", e)
                // Restart wake word detection
                delay(1000)
                if (_state.value == AssistantState.LISTENING_FOR_WAKE_WORD) {
                    startWakeWordDetection()
                }
            }
        }
        
        wakeWordDetector.startListening()
    }
    
    private fun startSimpleWakeWordDetection() {
        wakeWordJob = orchestratorScope.launch {
            try {
                wakeWordDetector.wakeWordDetected.collect { wakeWordResult ->
                    handleWakeWordDetected(wakeWordResult)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in simple wake word detection", e)
            }
        }
        
        wakeWordDetector.startSimpleWakeWordDetection()
    }
    
    private suspend fun handleWakeWordDetected(wakeWordResult: WakeWordResult) {
        Log.d(TAG, "Wake word detected: ${wakeWordResult.keyword}")
        
        _state.value = AssistantState.LISTENING_FOR_COMMAND
        
        // IMPORTANT: Stop wake word detection to free up the microphone
        wakeWordDetector.stopListening()
        
        // Longer delay to ensure microphone is completely released
        // This is critical for Android's SpeechRecognizer to work properly
        delay(500)
        
        try {
            // Start speech recognition (now microphone should be available)
            val userInput = speechToTextManager.transcribeAudio()
            
            if (!userInput.isNullOrBlank()) {
                Log.d(TAG, "User input received: $userInput")
                processUserInput(userInput)
            } else {
                Log.w(TAG, "No speech detected")
                handleError("I didn't catch that. Could you please repeat?")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing speech", e)
            handleError("I had trouble understanding you. Please try again.")
        }
        
        // Return to wake word listening - restart wake word detection
        _state.value = AssistantState.LISTENING_FOR_WAKE_WORD
        
        // Add delay before restarting wake word detection
        delay(200)
        wakeWordDetector.startListening()
    }
    
    private suspend fun processUserInput(userInput: String) {
        _state.value = AssistantState.PROCESSING
        
        try {
            // Add user message to conversation history
            addToConversationHistory(MessageRole.USER, userInput)
            
            // Get response from LLM
            val llmResponse = llmProvider.processInput(
                userInput = userInput,
                availableTools = toolboxManager.getAllTools(),
                conversationHistory = conversationHistory
            )
            
            var finalResponse = llmResponse.text
            
            Log.d(TAG, "LLM response received. Should use tools: ${llmResponse.shouldUseTools}, Tool calls: ${llmResponse.toolCalls.size}")
            
            // Execute tools if requested
            if (llmResponse.shouldUseTools && llmResponse.toolCalls.isNotEmpty()) {
                Log.d(TAG, "Executing ${llmResponse.toolCalls.size} tool(s)")
                for (toolCall in llmResponse.toolCalls) {
                    Log.d(TAG, "Executing tool: ${toolCall.toolName} with parameters: ${toolCall.parameters}")
                    val toolResult = toolboxManager.executeTool(
                        toolCall.toolName,
                        toolCall.parameters
                    )
                    
                    if (toolResult.success) {
                        finalResponse = toolResult.message
                        Log.d(TAG, "Tool executed successfully: ${toolCall.toolName} - ${toolResult.message}")
                    } else {
                        finalResponse = "I tried to ${toolCall.toolName} but encountered an issue: ${toolResult.message}"
                        Log.e(TAG, "Tool execution failed: ${toolCall.toolName} - ${toolResult.message}")
                    }
                }
            } else {
                Log.d(TAG, "No tools to execute, using LLM response as-is")
            }
            
            // Add assistant response to conversation history
            addToConversationHistory(MessageRole.ASSISTANT, finalResponse)
            
            // Speak the response
            _state.value = AssistantState.SPEAKING
            val speechSuccess = textToSpeechManager.speak(finalResponse)
            
            if (speechSuccess) {
                _lastResponse.value = finalResponse
                Log.d(TAG, "Response delivered: $finalResponse")
            } else {
                Log.e(TAG, "Failed to speak response")
            }
            
            // Return to wake word listening
            _state.value = AssistantState.LISTENING_FOR_WAKE_WORD
            wakeWordDetector.startListening()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing user input", e)
            handleError("I'm sorry, I encountered an error while processing your request.")
            
            // Return to wake word listening even after error
            _state.value = AssistantState.LISTENING_FOR_WAKE_WORD
            wakeWordDetector.startListening()
        }
    }
    
    private suspend fun handleError(errorMessage: String) {
        _state.value = AssistantState.SPEAKING
        _lastResponse.value = errorMessage
        textToSpeechManager.speak(errorMessage)
        
        // Return to wake word listening after error
        _state.value = AssistantState.LISTENING_FOR_WAKE_WORD
        wakeWordDetector.startListening()
    }
    
    private fun addToConversationHistory(role: MessageRole, content: String) {
        conversationHistory.add(ConversationMessage(role, content))
        
        // Keep only recent conversation history
        if (conversationHistory.size > MAX_CONVERSATION_HISTORY) {
            conversationHistory.removeAt(0)
        }
    }
    
    /**
     * Gets the current conversation history
     */
    fun getConversationHistory(): List<ConversationMessage> = conversationHistory.toList()
    
    /**
     * Clears the conversation history
     */
    fun clearConversationHistory() {
        conversationHistory.clear()
        Log.d(TAG, "Conversation history cleared")
    }
    
    /**
     * Gets the current state of the assistant
     */
    fun getCurrentState(): AssistantState = _state.value
    
    /**
     * Releases all resources
     */
    fun release() {
        orchestratorScope.cancel()
        wakeWordDetector.release()
        speechToTextManager.release()
        textToSpeechManager.release()
        Log.d(TAG, "Assistant resources released")
    }
}

/**
 * Possible states of the voice assistant
 */
enum class AssistantState {
    IDLE,
    LISTENING_FOR_WAKE_WORD,
    LISTENING_FOR_COMMAND,
    PROCESSING,
    SPEAKING,
    STOPPING,
    ERROR
}
