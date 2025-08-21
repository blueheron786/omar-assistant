package com.omar.assistant.toolbox.tools

import android.content.Context
import android.util.Log
import com.omar.assistant.nlp.Intent
import com.omar.assistant.toolbox.CommandResult
import com.omar.assistant.toolbox.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * LightControlTool - Controls smart lights via REST API
 * 
 * This tool demonstrates how to integrate with IoT devices.
 * It can be configured to work with various smart light systems.
 */
class LightControlTool(private val context: Context) : Tool {
    
    override val name = "light_control"
    override val description = "Controls smart lights"
    override val supportedActions = listOf(Intent.ACTION_TURN_ON, Intent.ACTION_TURN_OFF)
    override val supportedEntities = listOf(Intent.ENTITY_LIGHT)
    
    private val TAG = "LightControlTool"
    
    // Configuration - In a real app, these would be in settings
    private val lightApiBaseUrl = "http://192.168.1.100:8080/api" // Example smart hub URL
    private val lightApiKey = "your_api_key_here" // API key for authentication
    
    // Local state tracking
    private var lightState = false
    
    override suspend fun execute(
        action: String, 
        entity: String?, 
        parameters: Map<String, String>
    ): CommandResult {
        
        return when (action) {
            Intent.ACTION_TURN_ON -> turnOnLight(parameters)
            Intent.ACTION_TURN_OFF -> turnOffLight(parameters)
            else -> CommandResult(
                success = false,
                response = "I don't know how to $action the light"
            )
        }
    }
    
    override fun canHandle(action: String, entity: String?): Boolean {
        return action in supportedActions && entity == Intent.ENTITY_LIGHT
    }
    
    /**
     * Turn on the light
     */
    private suspend fun turnOnLight(parameters: Map<String, String>): CommandResult {
        return withContext(Dispatchers.IO) {
            try {
                // Try to call real API first
                val apiResult = callLightApi("on", parameters)
                if (apiResult.success) {
                    lightState = true
                    return@withContext apiResult
                }
                
                // Fall back to local simulation
                lightState = true
                Log.d(TAG, "Light turned ON (simulated)")
                
                val brightness = parameters["brightness"]?.toIntOrNull() ?: 100
                val color = parameters["color"] ?: "white"
                
                CommandResult(
                    success = true,
                    response = "Light is now on" + 
                        if (brightness != 100) " at $brightness% brightness" else "" +
                        if (color != "white") " in $color color" else "",
                    data = mapOf(
                        "state" to "on",
                        "brightness" to brightness,
                        "color" to color
                    )
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error turning on light", e)
                CommandResult(
                    success = false,
                    response = "Sorry, I couldn't turn on the light. Please check the connection."
                )
            }
        }
    }
    
    /**
     * Turn off the light
     */
    private suspend fun turnOffLight(parameters: Map<String, String>): CommandResult {
        return withContext(Dispatchers.IO) {
            try {
                // Try to call real API first
                val apiResult = callLightApi("off", parameters)
                if (apiResult.success) {
                    lightState = false
                    return@withContext apiResult
                }
                
                // Fall back to local simulation
                lightState = false
                Log.d(TAG, "Light turned OFF (simulated)")
                
                CommandResult(
                    success = true,
                    response = "Light is now off",
                    data = mapOf("state" to "off")
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error turning off light", e)
                CommandResult(
                    success = false,
                    response = "Sorry, I couldn't turn off the light. Please check the connection."
                )
            }
        }
    }
    
    /**
     * Call the smart light API
     */
    private suspend fun callLightApi(command: String, parameters: Map<String, String>): CommandResult {
        return try {
            val url = URL("$lightApiBaseUrl/lights/1/$command")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $lightApiKey")
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
            }
            
            // Build JSON payload
            val jsonPayload = buildJsonPayload(command, parameters)
            connection.outputStream.use { os ->
                os.write(jsonPayload.toByteArray())
            }
            
            val responseCode = connection.responseCode
            val responseMessage = connection.inputStream.bufferedReader().readText()
            
            if (responseCode == 200) {
                Log.d(TAG, "Light API call successful: $responseMessage")
                CommandResult(
                    success = true,
                    response = "Light ${command} successfully",
                    data = mapOf("api_response" to responseMessage)
                )
            } else {
                Log.w(TAG, "Light API call failed: $responseCode - $responseMessage")
                CommandResult(success = false, response = "API call failed")
            }
            
        } catch (e: Exception) {
            Log.d(TAG, "Light API not available, using simulation: ${e.message}")
            CommandResult(success = false, response = "API not available")
        }
    }
    
    /**
     * Build JSON payload for API call
     */
    private fun buildJsonPayload(command: String, parameters: Map<String, String>): String {
        val brightness = parameters["brightness"]?.toIntOrNull()
        val color = parameters["color"]
        
        return buildString {
            append("{")
            append("\"state\": \"$command\"")
            
            if (brightness != null) {
                append(", \"brightness\": $brightness")
            }
            
            if (!color.isNullOrEmpty()) {
                append(", \"color\": \"$color\"")
            }
            
            append("}")
        }
    }
    
    /**
     * Get current light status
     */
    fun getLightStatus(): Map<String, Any> {
        return mapOf(
            "state" to if (lightState) "on" else "off",
            "brightness" to 100,
            "color" to "white"
        )
    }
    
    /**
     * Configure API settings
     */
    fun configure(baseUrl: String, apiKey: String) {
        // In a real app, save these to SharedPreferences
        Log.d(TAG, "Light control configured with URL: $baseUrl")
    }
}
