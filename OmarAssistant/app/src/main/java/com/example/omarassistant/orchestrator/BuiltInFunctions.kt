package com.example.omarassistant.orchestrator

import android.content.Context
import android.media.AudioManager
import android.hardware.camera2.CameraManager
import android.util.Log
import com.example.omarassistant.model.ExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Smart light control function
 * Supports generic REST API calls for smart home light control
 */
class LightControlFunction : ToolboxFunction() {
    override val name = "control_light"
    override val description = "Control smart home lights"
    
    companion object {
        private const val TAG = "LightControl"
        // Generic smart home API endpoint (configurable in settings)
        private const val DEFAULT_API_URL = "http://192.168.1.100:8080/api/lights"
    }
    
    override suspend fun execute(parameters: Map<String, Any>, context: Context): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            val action = parameters["action"] as? String ?: "on"
            val brightness = parameters["brightness"] as? Number
            val room = parameters["room"] as? String ?: "living room"
            
            Log.d(TAG, "Controlling light - Action: $action, Room: $room, Brightness: $brightness")
            
            // Create API request
            val requestBody = JSONObject().apply {
                put("action", action)
                put("room", room)
                brightness?.let { put("brightness", it.toInt()) }
            }
            
            // Simulate API call (replace with actual smart home API)
            val success = simulateSmartHomeCall("lights", requestBody.toString())
            
            val message = when {
                success && action == "on" -> {
                    if (brightness != null) {
                        "Turned on the $room light at ${brightness}% brightness"
                    } else {
                        "Turned on the $room light"
                    }
                }
                success && action == "off" -> "Turned off the $room light"
                success && action == "dim" -> "Dimmed the $room light to ${brightness ?: 50}%"
                else -> "I couldn't control the $room light right now. Please check if it's connected."
            }
            
            ExecutionResult(success = success, message = message)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error controlling light", e)
            ExecutionResult(
                success = false,
                message = "I had trouble controlling the light. Please try again."
            )
        }
    }
    
    private suspend fun simulateSmartHomeCall(device: String, payload: String): Boolean {
        // In a real implementation, this would make an HTTP call to your smart home hub
        // For demo purposes, we'll simulate a successful response
        return withContext(Dispatchers.IO) {
            try {
                // Simulate network delay
                kotlinx.coroutines.delay(500)
                
                // Simulate 90% success rate
                (0..9).random() < 9
            } catch (e: Exception) {
                false
            }
        }
    }
}

/**
 * Thermostat control function
 */
class ThermostatControlFunction : ToolboxFunction() {
    override val name = "control_thermostat"
    override val description = "Control smart thermostat temperature"
    
    override suspend fun execute(parameters: Map<String, Any>, context: Context): ExecutionResult {
        try {
            val action = parameters["action"] as? String ?: "set"
            val temperature = parameters["temperature"] as? Number
            
            val message = when (action) {
                "set" -> {
                    temperature?.let {
                        "Set thermostat to ${it.toInt()}°F"
                    } ?: "I need to know what temperature to set"
                }
                "increase" -> "Increased temperature by ${temperature?.toInt() ?: 2}°F"
                "decrease" -> "Decreased temperature by ${temperature?.toInt() ?: 2}°F"
                else -> "I'm not sure how to $action the thermostat"
            }
            
            return ExecutionResult(success = true, message = message)
            
        } catch (e: Exception) {
            return ExecutionResult(
                success = false,
                message = "I couldn't control the thermostat right now"
            )
        }
    }
}

/**
 * Volume control function
 */
class VolumeControlFunction : ToolboxFunction() {
    override val name = "control_volume"
    override val description = "Control device volume"
    
    override suspend fun execute(parameters: Map<String, Any>, context: Context): ExecutionResult {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val action = parameters["action"] as? String ?: "set"
            val level = parameters["level"] as? Number
            
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            
            val newVolume = when (action) {
                "set" -> {
                    level?.let { (it.toFloat() / 100f * maxVolume).toInt() } ?: currentVolume
                }
                "increase" -> {
                    val increment = level?.toInt() ?: 10
                    (currentVolume + (increment * maxVolume / 100)).coerceAtMost(maxVolume)
                }
                "decrease" -> {
                    val decrement = level?.toInt() ?: 10
                    (currentVolume - (decrement * maxVolume / 100)).coerceAtLeast(0)
                }
                else -> currentVolume
            }
            
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
            
            val percentage = (newVolume.toFloat() / maxVolume * 100).toInt()
            val message = when (action) {
                "set" -> "Set volume to $percentage%"
                "increase" -> "Increased volume to $percentage%"
                "decrease" -> "Decreased volume to $percentage%"
                else -> "Volume is at $percentage%"
            }
            
            return ExecutionResult(success = true, message = message)
            
        } catch (e: Exception) {
            Log.e("VolumeControl", "Error controlling volume", e)
            return ExecutionResult(
                success = false,
                message = "I couldn't adjust the volume right now"
            )
        }
    }
}

/**
 * Flashlight control function
 */
class FlashlightControlFunction : ToolboxFunction() {
    override val name = "control_flashlight"
    override val description = "Control device flashlight"
    
    override suspend fun execute(parameters: Map<String, Any>, context: Context): ExecutionResult {
        try {
            val action = parameters["action"] as? String ?: "on"
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId == null) {
                return ExecutionResult(
                    success = false,
                    message = "This device doesn't have a flashlight"
                )
            }
            
            val turnOn = action == "on"
            cameraManager.setTorchMode(cameraId, turnOn)
            
            val message = if (turnOn) "Turned on flashlight" else "Turned off flashlight"
            return ExecutionResult(success = true, message = message)
            
        } catch (e: Exception) {
            Log.e("FlashlightControl", "Error controlling flashlight", e)
            return ExecutionResult(
                success = false,
                message = "I couldn't control the flashlight. You might need to grant camera permission."
            )
        }
    }
}

/**
 * Weather function (simulated)
 */
class WeatherFunction : ToolboxFunction() {
    override val name = "get_weather"
    override val description = "Get weather information"
    
    override suspend fun execute(parameters: Map<String, Any>, context: Context): ExecutionResult {
        try {
            val location = parameters["location"] as? String ?: "your area"
            
            // Simulate weather API response
            val temperature = (65..85).random()
            val conditions = listOf("sunny", "partly cloudy", "cloudy", "rainy").random()
            
            val message = "The weather in $location is $temperature°F and $conditions"
            return ExecutionResult(success = true, message = message)
            
        } catch (e: Exception) {
            return ExecutionResult(
                success = false,
                message = "I couldn't get the weather information right now"
            )
        }
    }
}

/**
 * Timer function (simulated)
 */
class TimerFunction : ToolboxFunction() {
    override val name = "set_timer"
    override val description = "Set a countdown timer"
    
    override suspend fun execute(parameters: Map<String, Any>, context: Context): ExecutionResult {
        try {
            val duration = parameters["duration_minutes"] as? Number ?: 5
            val label = parameters["label"] as? String
            
            val minutes = duration.toInt()
            val message = if (label != null) {
                "Set a $minutes minute timer for $label"
            } else {
                "Set a $minutes minute timer"
            }
            
            // In a real implementation, you would create an actual timer/alarm
            return ExecutionResult(success = true, message = message)
            
        } catch (e: Exception) {
            return ExecutionResult(
                success = false,
                message = "I couldn't set the timer right now"
            )
        }
    }
}
