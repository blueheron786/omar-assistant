package com.omar.assistant.toolbox.tools

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.omar.assistant.nlp.Intent as AssistantIntent
import com.omar.assistant.toolbox.CommandResult
import com.omar.assistant.toolbox.Tool

/**
 * SystemControlTool - Controls system settings and functions
 */
class SystemControlTool(private val context: Context) : Tool {
    
    override val name = "system_control"
    override val description = "Controls system settings and functions"
    override val supportedActions = listOf(
        AssistantIntent.ACTION_TURN_ON,
        AssistantIntent.ACTION_TURN_OFF,
        "open",
        "launch"
    )
    override val supportedEntities = listOf(
        "wifi", "bluetooth", "flashlight", "settings", "camera"
    )
    
    override suspend fun execute(
        action: String,
        entity: String?,
        parameters: Map<String, String>
    ): CommandResult {
        
        return when (action) {
            AssistantIntent.ACTION_TURN_ON -> turnOnSystemFeature(entity)
            AssistantIntent.ACTION_TURN_OFF -> turnOffSystemFeature(entity)
            "open", "launch" -> openSystemApp(entity)
            else -> CommandResult(
                success = false,
                response = "I don't know how to $action $entity"
            )
        }
    }
    
    override fun canHandle(action: String, entity: String?): Boolean {
        return action in supportedActions && entity in supportedEntities
    }
    
    private fun turnOnSystemFeature(entity: String?): CommandResult {
        return when (entity) {
            "wifi" -> {
                openWifiSettings()
                CommandResult(
                    success = true,
                    response = "Opening Wi-Fi settings. You can turn on Wi-Fi from there.",
                    data = mapOf("action" to "open_wifi_settings")
                )
            }
            "bluetooth" -> {
                openBluetoothSettings()
                CommandResult(
                    success = true,
                    response = "Opening Bluetooth settings. You can turn on Bluetooth from there.",
                    data = mapOf("action" to "open_bluetooth_settings")
                )
            }
            "flashlight" -> {
                // Note: Flashlight control requires camera permission and is more complex
                CommandResult(
                    success = false,
                    response = "Flashlight control is not implemented yet for security reasons."
                )
            }
            else -> CommandResult(
                success = false,
                response = "I don't know how to turn on $entity"
            )
        }
    }
    
    private fun turnOffSystemFeature(entity: String?): CommandResult {
        return when (entity) {
            "wifi", "bluetooth" -> {
                CommandResult(
                    success = true,
                    response = "For security reasons, I can only open settings. You'll need to turn off $entity manually.",
                    data = mapOf("action" to "security_limitation")
                )
            }
            else -> CommandResult(
                success = false,
                response = "I don't know how to turn off $entity"
            )
        }
    }
    
    private fun openSystemApp(entity: String?): CommandResult {
        return when (entity) {
            "settings" -> {
                openSystemSettings()
                CommandResult(
                    success = true,
                    response = "Opening system settings",
                    data = mapOf("action" to "open_settings")
                )
            }
            "camera" -> {
                openCamera()
                CommandResult(
                    success = true,
                    response = "Opening camera",
                    data = mapOf("action" to "open_camera")
                )
            }
            else -> CommandResult(
                success = false,
                response = "I don't know how to open $entity"
            )
        }
    }
    
    private fun openWifiSettings() {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings
            openSystemSettings()
        }
    }
    
    private fun openBluetoothSettings() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings
            openSystemSettings()
        }
    }
    
    private fun openSystemSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Handle case where settings can't be opened
        }
    }
    
    private fun openCamera() {
        try {
            val intent = Intent("android.media.action.IMAGE_CAPTURE").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Handle case where camera can't be opened
        }
    }
}
