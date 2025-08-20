package com.example.omarassistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.omarassistant.config.ConfigManager
import com.example.omarassistant.service.VoiceAssistantService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Boot receiver to automatically start OMAR assistant when device boots
 * Only starts if continuous listening is enabled in settings
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                startAssistantIfEnabled(context)
            }
        }
    }
    
    /**
     * Start assistant service if continuous listening is enabled
     */
    private fun startAssistantIfEnabled(context: Context) {
        // Use coroutine to read encrypted preferences
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val configManager = ConfigManager.getInstance(context)
                val config = configManager.getConfig()
                
                if (config.enableContinuousListening && config.geminiApiKey.isNotEmpty()) {
                    Log.d(TAG, "Starting OMAR assistant service on boot")
                    
                    val serviceIntent = Intent(context, VoiceAssistantService::class.java).apply {
                        action = VoiceAssistantService.ACTION_START_LISTENING
                    }
                    
                    try {
                        context.startForegroundService(serviceIntent)
                        Log.d(TAG, "OMAR assistant service started successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start service on boot", e)
                    }
                } else {
                    Log.d(TAG, "OMAR assistant auto-start disabled or not configured")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking auto-start configuration", e)
            }
        }
    }
}
