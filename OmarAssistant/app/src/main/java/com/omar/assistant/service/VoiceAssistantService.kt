package com.omar.assistant.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.omar.assistant.R
import com.omar.assistant.core.di.ServiceLocator
import com.omar.assistant.core.orchestrator.AssistantOrchestrator
import com.omar.assistant.core.orchestrator.AssistantState
import com.omar.assistant.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Foreground service for continuous voice assistant operation
 * Keeps the assistant running in the background for wake word detection
 */
class VoiceAssistantService : Service() {
    
    companion object {
        private const val TAG = "VoiceAssistantService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "omar_assistant_channel"
        
        const val ACTION_START_ASSISTANT = "START_ASSISTANT"
        const val ACTION_STOP_ASSISTANT = "STOP_ASSISTANT"
        
        fun startService(context: Context) {
            val intent = Intent(context, VoiceAssistantService::class.java).apply {
                action = ACTION_START_ASSISTANT
            }
            context.startForegroundService(intent)
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, VoiceAssistantService::class.java).apply {
                action = ACTION_STOP_ASSISTANT
            }
            context.startService(intent)
        }
    }
    
    private lateinit var assistantOrchestrator: AssistantOrchestrator
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stateObserverJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Voice Assistant Service created")
        
        // Initialize assistant orchestrator
        assistantOrchestrator = ServiceLocator.assistantOrchestrator
        
        // Create notification channel
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ASSISTANT -> {
                startAssistant()
            }
            ACTION_STOP_ASSISTANT -> {
                stopAssistant()
            }
        }
        
        return START_STICKY // Restart if killed by system
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startAssistant() {
        Log.d(TAG, "Starting voice assistant")
        
        // Start foreground service
        val notification = createNotification("Starting...", false)
        startForeground(NOTIFICATION_ID, notification)
        
        serviceScope.launch {
            try {
                // Start the assistant
                assistantOrchestrator.start()
                
                // Observe state changes to update notification
                observeAssistantState()
                
                Log.d(TAG, "Voice assistant started successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start voice assistant", e)
                stopSelf()
            }
        }
    }
    
    private fun stopAssistant() {
        Log.d(TAG, "Stopping voice assistant")
        
        serviceScope.launch {
            try {
                assistantOrchestrator.stop()
                stopForeground(true)
                stopSelf()
                
                Log.d(TAG, "Voice assistant stopped")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping voice assistant", e)
            }
        }
    }
    
    private fun observeAssistantState() {
        stateObserverJob = serviceScope.launch {
            assistantOrchestrator.state.collect { state ->
                updateNotification(state)
            }
        }
    }
    
    private fun updateNotification(state: AssistantState) {
        val (statusText, isListening) = when (state) {
            AssistantState.IDLE -> "Idle" to false
            AssistantState.LISTENING_FOR_WAKE_WORD -> "Listening for wake word..." to true
            AssistantState.LISTENING_FOR_COMMAND -> "Listening for command..." to true
            AssistantState.PROCESSING -> "Processing..." to false
            AssistantState.SPEAKING -> "Speaking..." to false
            AssistantState.STOPPING -> "Stopping..." to false
            AssistantState.ERROR -> "Error" to false
        }
        
        val notification = createNotification(statusText, isListening)
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun createNotification(statusText: String, isListening: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, VoiceAssistantService::class.java).apply {
            action = ACTION_STOP_ASSISTANT
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val iconRes = if (isListening) R.drawable.ic_mic_on else R.drawable.ic_mic_off
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Omar Assistant")
            .setContentText(statusText)
            .setSmallIcon(iconRes)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Omar Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Voice assistant service notifications"
                setShowBadge(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Voice Assistant Service destroyed")
        
        // Clean up
        stateObserverJob?.cancel()
        serviceScope.cancel()
        assistantOrchestrator.release()
    }
}
