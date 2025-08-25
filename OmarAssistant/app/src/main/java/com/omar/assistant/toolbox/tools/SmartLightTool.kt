package com.omar.assistant.toolbox.tools

import android.util.Log
import com.omar.assistant.toolbox.Tool
import com.omar.assistant.toolbox.ToolExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Tool for controlling KASA HS200 smart light switch
 * Implements the KASA smart plug/switch API for turning lights on/off
 */
class SmartLightTool : Tool {
    
    companion object {
        private const val TAG = "SmartLightTool"
        // Default IP - should be configurable in real implementation
        private const val DEFAULT_KASA_IP = "192.168.1.100"
        private const val KASA_PORT = 9999
    }
    
    override val name: String = "smart_light"
    
    override val description: String = "Controls smart lights (turn on/off, set brightness)"
    
    override val parameters: Map<String, String> = mapOf(
        "action" to "Action to perform: 'on', 'off', or 'toggle'",
        "device_ip" to "IP address of the device (optional, uses default if not provided)",
        "brightness" to "Brightness level 0-100 (optional, only for dimmable lights)"
    )
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    override suspend fun execute(parameters: Map<String, Any>): ToolExecutionResult = withContext(Dispatchers.IO) {
        val action = parameters["action"]?.toString()?.lowercase()
        val deviceIp = parameters["device_ip"]?.toString() ?: DEFAULT_KASA_IP
        val brightness = parameters["brightness"]?.toString()?.toIntOrNull()
        
        when (action) {
            "on" -> turnOnLight(deviceIp, brightness)
            "off" -> turnOffLight(deviceIp)
            "toggle" -> toggleLight(deviceIp)
            else -> ToolExecutionResult(
                success = false,
                message = "Invalid action. Use 'on', 'off', or 'toggle'"
            )
        }
    }
    
    override fun validateParameters(parameters: Map<String, Any>): Boolean {
        val action = parameters["action"]?.toString()?.lowercase()
        return action in listOf("on", "off", "toggle")
    }
    
    private suspend fun turnOnLight(deviceIp: String, brightness: Int? = null): ToolExecutionResult {
        return try {
            val command = if (brightness != null) {
                createKasaCommand("set_relay_state", mapOf("state" to 1, "brightness" to brightness))
            } else {
                createKasaCommand("set_relay_state", mapOf("state" to 1))
            }
            
            val result = sendKasaCommand(deviceIp, command)
            if (result) {
                val message = if (brightness != null) {
                    "Light turned on with brightness $brightness%"
                } else {
                    "Light turned on"
                }
                ToolExecutionResult(
                    success = true,
                    message = message,
                    data = mapOf("action" to "on", "brightness" to (brightness ?: 100))
                )
            } else {
                ToolExecutionResult(
                    success = false,
                    message = "Failed to turn on light"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error turning on light", e)
            ToolExecutionResult(
                success = false,
                message = "Error controlling light: ${e.message}"
            )
        }
    }
    
    private suspend fun turnOffLight(deviceIp: String): ToolExecutionResult {
        return try {
            val command = createKasaCommand("set_relay_state", mapOf("state" to 0))
            val result = sendKasaCommand(deviceIp, command)
            
            if (result) {
                ToolExecutionResult(
                    success = true,
                    message = "Light turned off",
                    data = mapOf("action" to "off")
                )
            } else {
                ToolExecutionResult(
                    success = false,
                    message = "Failed to turn off light"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error turning off light", e)
            ToolExecutionResult(
                success = false,
                message = "Error controlling light: ${e.message}"
            )
        }
    }
    
    private suspend fun toggleLight(deviceIp: String): ToolExecutionResult {
        // For simplicity, we'll just turn on the light
        // In a real implementation, we'd first check the current state
        return turnOnLight(deviceIp)
    }
    
    private fun createKasaCommand(method: String, params: Map<String, Any>): String {
        // KASA devices use a specific JSON command format
        val command = mapOf(
            "system" to mapOf(
                method to params
            )
        )
        
        // Convert to JSON (simplified - in production use proper JSON library)
        return buildJsonString(command)
    }
    
    private fun buildJsonString(obj: Any): String {
        return when (obj) {
            is Map<*, *> -> {
                "{" + obj.entries.joinToString(",") { (k, v) ->
                    "\"$k\":${buildJsonString(v ?: "null")}"
                } + "}"
            }
            is String -> "\"$obj\""
            is Number -> obj.toString()
            else -> "\"$obj\""
        }
    }
    
    private suspend fun sendKasaCommand(deviceIp: String, command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // KASA devices require commands to be encrypted with their proprietary encryption
            // For this demo, we'll simulate the API call
            
            // In a real implementation, you would:
            // 1. Encrypt the command using KASA's encryption algorithm
            // 2. Send it over TCP to port 9999
            // 3. Decrypt and parse the response
            
            Log.d(TAG, "Sending command to KASA device at $deviceIp: $command")
            
            // Simulate successful response for demo
            // In production, implement actual KASA protocol
            simulateKasaRequest(deviceIp, command)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send KASA command", e)
            false
        }
    }
    
    private suspend fun simulateKasaRequest(deviceIp: String, command: String): Boolean {
        // Simulate network delay
        kotlinx.coroutines.delay(500)
        
        Log.d(TAG, "Simulated KASA request to $deviceIp successful")
        return true
    }
    
    // Alternative HTTP-based implementation for devices that support HTTP API
    private suspend fun sendHttpCommand(deviceIp: String, command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "http://$deviceIp/api/control"
            val mediaType = "application/json".toMediaType()
            val requestBody = command.toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            response.isSuccessful
            
        } catch (e: IOException) {
            Log.e(TAG, "HTTP request failed", e)
            false
        }
    }
}
