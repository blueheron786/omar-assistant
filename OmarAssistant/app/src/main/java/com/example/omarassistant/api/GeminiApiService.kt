package com.example.omarassistant.api

import android.util.Log
import com.example.omarassistant.model.FunctionCall
import com.example.omarassistant.model.LLMResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Gemini API service for processing voice commands and generating responses
 * Handles both text processing and function calling capabilities
 */
class GeminiApiService(private var apiKey: String) {
    
    companion object {
        private const val TAG = "GeminiApiService"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val MODEL = "gemini-1.5-flash-latest"
        private const val TIMEOUT_SECONDS = 30L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    // System prompt for OMAR assistant
    private val systemPrompt = """
        You are OMAR, a helpful voice assistant. You can:
        
        1. Control smart home devices (lights, thermostat, etc.)
        2. Control phone functions (volume, flashlight, etc.) 
        3. Answer questions and have conversations
        4. Perform calculations and provide information
        
        Available functions:
        - control_light(action: "on"|"off"|"dim", brightness?: 0-100, room?: string)
        - control_thermostat(action: "set"|"increase"|"decrease", temperature?: number)
        - control_volume(action: "set"|"increase"|"decrease", level?: 0-100)
        - control_flashlight(action: "on"|"off")
        - get_weather(location?: string)
        - set_timer(duration_minutes: number, label?: string)
        - play_music(query?: string, action?: "play"|"pause"|"stop"|"next"|"previous")
        
        When the user asks to control something, use the appropriate function. 
        For general conversation, respond naturally without using functions.
        Keep responses concise and friendly. Always respond in the same language as the user's input.
        
        If you're unsure about a command, ask for clarification rather than guessing.
    """.trimIndent()
    
    /**
     * Process user text and get AI response
     */
    suspend fun processText(userText: String): LLMResponse = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Processing text: $userText")
            
            val request = createGeminiRequest(userText)
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "API call failed: ${response.code} - ${response.message}")
                return@withContext LLMResponse(
                    text = "Sorry, I'm having trouble connecting to my brain right now. Please try again.",
                    requiresExecution = false
                )
            }
            
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Log.e(TAG, "Empty response body")
                return@withContext LLMResponse(
                    text = "I didn't get a proper response. Could you try asking again?",
                    requiresExecution = false
                )
            }
            
            Log.d(TAG, "Raw API response: $responseBody")
            return@withContext parseGeminiResponse(responseBody)
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error processing text", e)
            return@withContext LLMResponse(
                text = "I'm having trouble connecting to the internet. Please check your connection and try again.",
                requiresExecution = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing text", e)
            return@withContext LLMResponse(
                text = "Something went wrong while processing your request. Please try again.",
                requiresExecution = false
            )
        }
    }
    
    /**
     * Create HTTP request for Gemini API
     */
    private fun createGeminiRequest(userText: String): Request {
        val functions = listOf(
            createFunctionDefinition("control_light", "Control smart lights",
                mapOf(
                    "action" to mapOf("type" to "string", "enum" to listOf("on", "off", "dim"), "description" to "Light action"),
                    "brightness" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 100, "description" to "Brightness level 0-100"),
                    "room" to mapOf("type" to "string", "description" to "Room name")
                ),
                listOf("action")
            ),
            createFunctionDefinition("control_thermostat", "Control thermostat temperature",
                mapOf(
                    "action" to mapOf("type" to "string", "enum" to listOf("set", "increase", "decrease"), "description" to "Thermostat action"),
                    "temperature" to mapOf("type" to "number", "description" to "Temperature in Fahrenheit")
                ),
                listOf("action")
            ),
            createFunctionDefinition("control_volume", "Control device volume",
                mapOf(
                    "action" to mapOf("type" to "string", "enum" to listOf("set", "increase", "decrease"), "description" to "Volume action"),
                    "level" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 100, "description" to "Volume level 0-100")
                ),
                listOf("action")
            ),
            createFunctionDefinition("control_flashlight", "Control device flashlight",
                mapOf(
                    "action" to mapOf("type" to "string", "enum" to listOf("on", "off"), "description" to "Flashlight action")
                ),
                listOf("action")
            ),
            createFunctionDefinition("get_weather", "Get weather information",
                mapOf(
                    "location" to mapOf("type" to "string", "description" to "Location for weather query")
                ),
                emptyList()
            ),
            createFunctionDefinition("set_timer", "Set a countdown timer",
                mapOf(
                    "duration_minutes" to mapOf("type" to "number", "description" to "Timer duration in minutes"),
                    "label" to mapOf("type" to "string", "description" to "Optional timer label")
                ),
                listOf("duration_minutes")
            )
        )
        
        val requestBody = JsonObject().apply {
            add("contents", gson.toJsonTree(listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(
                        mapOf("text" to "$systemPrompt\n\nUser: $userText")
                    )
                )
            )))
            add("tools", gson.toJsonTree(listOf(
                mapOf("function_declarations" to functions)
            )))
            add("generationConfig", gson.toJsonTree(mapOf(
                "temperature" to 0.7,
                "topK" to 40,
                "topP" to 0.95,
                "maxOutputTokens" to 1024
            )))
        }
        
        val url = "$BASE_URL/models/$MODEL:generateContent?key=$apiKey"
        
        return Request.Builder()
            .url(url)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
    }
    
    /**
     * Create function definition for Gemini API
     */
    private fun createFunctionDefinition(
        name: String,
        description: String,
        parameters: Map<String, Any>,
        required: List<String>
    ): Map<String, Any> {
        return mapOf(
            "name" to name,
            "description" to description,
            "parameters" to mapOf(
                "type" to "object",
                "properties" to parameters,
                "required" to required
            )
        )
    }
    
    /**
     * Parse Gemini API response
     */
    private fun parseGeminiResponse(responseBody: String): LLMResponse {
        try {
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val candidates = jsonResponse.getAsJsonArray("candidates")
            
            if (candidates == null || candidates.size() == 0) {
                return LLMResponse("I couldn't process that request properly. Please try again.")
            }
            
            val candidate = candidates[0].asJsonObject
            val content = candidate.getAsJsonObject("content")
            val parts = content.getAsJsonArray("parts")
            
            if (parts == null || parts.size() == 0) {
                return LLMResponse("I didn't get a proper response. Please try again.")
            }
            
            val part = parts[0].asJsonObject
            
            // Check for function call
            if (part.has("functionCall")) {
                val functionCall = part.getAsJsonObject("functionCall")
                val functionName = functionCall.get("name").asString
                val args = functionCall.getAsJsonObject("args")
                
                val parameters = mutableMapOf<String, Any>()
                args.entrySet().forEach { (key, value) ->
                    parameters[key] = when {
                        value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString
                        value.isJsonPrimitive && value.asJsonPrimitive.isNumber -> value.asNumber
                        value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> value.asBoolean
                        else -> value.toString()
                    }
                }
                
                return LLMResponse(
                    text = "Executing command...",
                    functionCall = FunctionCall(functionName, parameters),
                    requiresExecution = true
                )
            }
            
            // Regular text response
            if (part.has("text")) {
                val text = part.get("text").asString
                return LLMResponse(text = text, requiresExecution = false)
            }
            
            return LLMResponse("I couldn't understand that request. Please try again.")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Gemini response", e)
            return LLMResponse("Sorry, I had trouble understanding the response. Please try again.")
        }
    }
    
    /**
     * Update API key
     */
    fun updateApiKey(newApiKey: String) {
        this.apiKey = newApiKey
        Log.d(TAG, "API key updated")
    }
    
    /**
     * Test API connection
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val testResponse = processText("Hello, are you working?")
            return@withContext testResponse.text.isNotEmpty() && 
                !testResponse.text.contains("trouble connecting", ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            return@withContext false
        }
    }
}
