package com.zayd.assistant.llm

import com.zayd.assistant.toolbox.Tool
import com.zayd.assistant.toolbox.ToolExecutionResult

/**
 * Abstract interface for LLM providers
 * Allows switching between different AI models (Gemini, OpenAI, etc.)
 */
interface LLMProvider {
    
    /**
     * Processes user input and returns a response
     * Can decide to either respond directly or use tools
     */
    suspend fun processInput(
        userInput: String,
        availableTools: List<Tool>,
        conversationHistory: List<ConversationMessage> = emptyList()
    ): LLMResponse
    
    /**
     * Determines if the provider is properly configured (has API key, etc.)
     */
    fun isConfigured(): Boolean
    
    /**
     * Validates the API key by making a test request to the service
     * @return Result.success(true) if valid, Result.failure(exception) if invalid
     */
    suspend fun validateApiKey(): Result<Boolean>
    
    /**
     * Gets the name of this provider
     */
    fun getProviderName(): String
}

/**
 * Represents a message in the conversation history
 */
data class ConversationMessage(
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Role of the message sender
 */
enum class MessageRole {
    USER, ASSISTANT, SYSTEM
}

/**
 * Response from the LLM provider
 */
data class LLMResponse(
    val text: String,
    val shouldUseTools: Boolean = false,
    val toolCalls: List<ToolCall> = emptyList(),
    val confidence: Float = 1.0f
)

/**
 * Represents a tool that the LLM wants to call
 */
data class ToolCall(
    val toolName: String,
    val parameters: Map<String, Any>
)
