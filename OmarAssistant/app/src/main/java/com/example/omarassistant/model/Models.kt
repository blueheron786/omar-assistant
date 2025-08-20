package com.example.omarassistant.model

/**
 * Represents a voice command processed by the assistant
 */
data class VoiceCommand(
    val originalText: String,
    val intent: String,
    val confidence: Float,
    val parameters: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Response from the LLM service
 */
data class LLMResponse(
    val text: String,
    val functionCall: FunctionCall? = null,
    val requiresExecution: Boolean = false
)

/**
 * Function call information from LLM
 */
data class FunctionCall(
    val name: String,
    val parameters: Map<String, Any>
)

/**
 * Result of executing a toolbox function
 */
data class ExecutionResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)

/**
 * Audio processing states
 */
enum class AudioState {
    IDLE,
    LISTENING_FOR_WAKE_WORD,
    WAKE_WORD_DETECTED,
    RECORDING_COMMAND,
    PROCESSING,
    SPEAKING
}

/**
 * Assistant configuration
 */
data class AssistantConfig(
    val wakeWord: String = "omar",
    val geminiApiKey: String = "",
    val language: String = "en",
    val voiceVolume: Float = 1.0f,
    val wakeWordSensitivity: Float = 0.7f,
    val vadSensitivity: Float = 0.5f,
    val enableContinuousListening: Boolean = true,
    val smartHomeApiUrl: String = "",
    val smartHomeApiKey: String = ""
)
