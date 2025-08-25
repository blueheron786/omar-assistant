package com.omar.assistant.llm.gemini

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.omar.assistant.core.storage.SecureStorage
import com.omar.assistant.llm.*
import com.omar.assistant.toolbox.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini AI provider implementation
 * Integrates with Google's Gemini Pro model
 */
class GeminiProvider(private val secureStorage: SecureStorage) : LLMProvider {
    
    companion object {
        private const val TAG = "GeminiProvider"
        private const val MODEL_NAME = "gemini-1.5-flash"
    }
    
    private var generativeModel: GenerativeModel? = null
    
    private fun initializeModel() {
        val apiKey = secureStorage.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            Log.w(TAG, "No Gemini API key found")
            return
        }
        
        try {
            generativeModel = GenerativeModel(
                modelName = MODEL_NAME,
                apiKey = apiKey,
                safetySettings = listOf(
                    SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.MEDIUM_AND_ABOVE),
                    SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.MEDIUM_AND_ABOVE),
                    SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
                    SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE),
                )
            )
            Log.d(TAG, "Gemini model initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Gemini model", e)
        }
    }
    
    override suspend fun processInput(
        userInput: String,
        availableTools: List<Tool>,
        conversationHistory: List<ConversationMessage>
    ): LLMResponse = withContext(Dispatchers.IO) {
        
        if (generativeModel == null) {
            initializeModel()
        }
        
        if (generativeModel == null) {
            return@withContext LLMResponse(
                text = "I'm sorry, I'm not properly configured. Please check the settings.",
                shouldUseTools = false
            )
        }
        
        try {
            val systemPrompt = buildSystemPrompt(availableTools)
            val fullPrompt = buildFullPrompt(systemPrompt, userInput, conversationHistory, availableTools)
            
            Log.d(TAG, "Sending prompt to Gemini: ${fullPrompt.take(200)}...")
            
            val response = generativeModel!!.generateContent(fullPrompt)
            val responseText = response.text ?: "I didn't understand that. Could you please rephrase?"
            
            Log.d(TAG, "Gemini response: $responseText")
            
            // Parse the response to determine if tools should be used
            val parsedResponse = parseResponse(responseText, availableTools)
            
            return@withContext parsedResponse
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing input with Gemini", e)
            return@withContext LLMResponse(
                text = "I'm experiencing some technical difficulties right now. Please try again in a moment.",
                shouldUseTools = false
            )
        }
    }
    
    private fun buildSystemPrompt(availableTools: List<Tool>): String {
        val toolDescriptions = availableTools.joinToString("\n") { tool ->
            "- ${tool.name}: ${tool.description}\n  Parameters: ${tool.parameters.keys.joinToString(", ")}"
        }
        
        return """
            You are OMAR, a helpful voice assistant. You can respond to user queries directly or use available tools when appropriate.
            
            Available tools:
            $toolDescriptions
            
            When you want to use a tool, respond in this format:
            USE_TOOL: tool_name
            PARAMETERS: {"param1": "value1", "param2": "value2"}
            REASON: Brief explanation of why you're using this tool
            
            Otherwise, respond normally to have a conversation with the user.
            
            Keep responses concise and conversational, as they will be spoken aloud.
            Be helpful, friendly, and efficient.
        """.trimIndent()
    }
    
    private fun buildFullPrompt(
        systemPrompt: String,
        userInput: String,
        conversationHistory: List<ConversationMessage>,
        availableTools: List<Tool>
    ): String {
        val historyText = if (conversationHistory.isNotEmpty()) {
            "Recent conversation:\n" + conversationHistory.takeLast(5).joinToString("\n") { message ->
                "${message.role}: ${message.content}"
            } + "\n\n"
        } else ""
        
        return """
            $systemPrompt
            
            $historyText
            User: $userInput
            
            Assistant:
        """.trimIndent()
    }
    
    private fun parseResponse(responseText: String, availableTools: List<Tool>): LLMResponse {
        val lines = responseText.lines()
        
        // Check if response indicates tool usage
        val toolLine = lines.find { it.startsWith("USE_TOOL:") }
        val parametersLine = lines.find { it.startsWith("PARAMETERS:") }
        val reasonLine = lines.find { it.startsWith("REASON:") }
        
        if (toolLine != null && parametersLine != null) {
            val toolName = toolLine.substringAfter("USE_TOOL:").trim()
            val parametersJson = parametersLine.substringAfter("PARAMETERS:").trim()
            val reason = reasonLine?.substringAfter("REASON:")?.trim() ?: "Using $toolName"
            
            try {
                // Simple JSON parsing for parameters
                val parameters = parseSimpleJson(parametersJson)
                
                return LLMResponse(
                    text = reason,
                    shouldUseTools = true,
                    toolCalls = listOf(ToolCall(toolName, parameters))
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse tool parameters", e)
            }
        }
        
        // Regular conversational response
        return LLMResponse(
            text = responseText,
            shouldUseTools = false
        )
    }
    
    private fun parseSimpleJson(jsonString: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        
        // Simple JSON parsing (for production, use a proper JSON library)
        val cleaned = jsonString.trim().removeSurrounding("{", "}")
        if (cleaned.isBlank()) return result
        
        val pairs = cleaned.split(",")
        for (pair in pairs) {
            val keyValue = pair.split(":", limit = 2)
            if (keyValue.size == 2) {
                val key = keyValue[0].trim().removeSurrounding("\"")
                val value = keyValue[1].trim().removeSurrounding("\"")
                result[key] = value
            }
        }
        
        return result
    }
    
    override suspend fun validateApiKey(): Result<Boolean> = withContext(Dispatchers.IO) {
        val apiKey = secureStorage.getGeminiApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(Exception("No API key configured"))
        }
        
        try {
            // Create a test model instance
            val testModel = GenerativeModel(
                modelName = MODEL_NAME,
                apiKey = apiKey,
                safetySettings = listOf(
                    SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.MEDIUM_AND_ABOVE),
                    SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.MEDIUM_AND_ABOVE),
                    SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
                    SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE),
                )
            )
            
            // Send a simple test request
            Log.d(TAG, "Validating Gemini API key...")
            val response = testModel.generateContent("Hello, please respond with just 'OK' to confirm you're working.")
            
            val responseText = response.text?.trim()
            if (!responseText.isNullOrBlank()) {
                Log.d(TAG, "API key validation successful. Response: $responseText")
                return@withContext Result.success(true)
            } else {
                Log.w(TAG, "API key validation failed: Empty response")
                return@withContext Result.failure(Exception("Invalid response from Gemini API"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "API key validation failed", e)
            return@withContext Result.failure(e)
        }
    }
    
    override fun isConfigured(): Boolean {
        return !secureStorage.getGeminiApiKey().isNullOrBlank()
    }
    
    override fun getProviderName(): String = "Gemini"
}
