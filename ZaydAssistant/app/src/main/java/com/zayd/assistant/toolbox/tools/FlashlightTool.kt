package com.zayd.assistant.toolbox.tools

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.util.Log
import com.zayd.assistant.toolbox.Tool
import com.zayd.assistant.toolbox.ToolExecutionResult

/**
 * Tool for controlling the device flashlight/torch
 * Allows turning the flashlight on and off via voice commands
 */
class FlashlightTool(private val context: Context) : Tool {
    
    companion object {
        private const val TAG = "FlashlightTool"
    }
    
    override val name: String = "flashlight"
    
    override val description: String = "Controls the device flashlight (turn on/off)"
    
    override val parameters: Map<String, String> = mapOf(
        "action" to "Action to perform: 'on', 'off', or 'toggle'"
    )
    
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    
    private var isFlashlightOn = false
    
    override suspend fun execute(parameters: Map<String, Any>): ToolExecutionResult {
        val action = parameters["action"]?.toString()?.lowercase()
        
        // Check if device has flashlight
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            return ToolExecutionResult(
                success = false,
                message = "This device does not have a flashlight"
            )
        }
        
        return when (action) {
            "on" -> turnOnFlashlight()
            "off" -> turnOffFlashlight()
            "toggle" -> toggleFlashlight()
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
    
    private fun turnOnFlashlight(): ToolExecutionResult {
        return try {
            val cameraId = getCameraId()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, true)
                isFlashlightOn = true
                Log.d(TAG, "Flashlight turned on")
                ToolExecutionResult(
                    success = true,
                    message = "Flashlight turned on",
                    data = mapOf("action" to "on", "state" to true)
                )
            } else {
                ToolExecutionResult(
                    success = false,
                    message = "Could not access camera for flashlight"
                )
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to turn on flashlight", e)
            ToolExecutionResult(
                success = false,
                message = "Failed to turn on flashlight: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error controlling flashlight", e)
            ToolExecutionResult(
                success = false,
                message = "Error controlling flashlight: ${e.message}"
            )
        }
    }
    
    private fun turnOffFlashlight(): ToolExecutionResult {
        return try {
            val cameraId = getCameraId()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, false)
                isFlashlightOn = false
                Log.d(TAG, "Flashlight turned off")
                ToolExecutionResult(
                    success = true,
                    message = "Flashlight turned off",
                    data = mapOf("action" to "off", "state" to false)
                )
            } else {
                ToolExecutionResult(
                    success = false,
                    message = "Could not access camera for flashlight"
                )
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to turn off flashlight", e)
            ToolExecutionResult(
                success = false,
                message = "Failed to turn off flashlight: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error controlling flashlight", e)
            ToolExecutionResult(
                success = false,
                message = "Error controlling flashlight: ${e.message}"
            )
        }
    }
    
    private fun toggleFlashlight(): ToolExecutionResult {
        return if (isFlashlightOn) {
            turnOffFlashlight()
        } else {
            turnOnFlashlight()
        }
    }
    
    private fun getCameraId(): String? {
        return try {
            val cameraIds = cameraManager.cameraIdList
            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE)
                if (flashAvailable == true) {
                    return id
                }
            }
            null
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error getting camera ID", e)
            null
        }
    }
}
