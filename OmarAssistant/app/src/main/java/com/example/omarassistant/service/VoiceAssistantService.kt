package com.example.omarassistant.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.omarassistant.MainActivity
import com.example.omarassistant.R
import com.example.omarassistant.model.AudioState
import com.example.omarassistant.orchestrator.VoiceAssistantOrchestrator
import kotlinx.coroutines.*

/**
 * Background service for OMAR voice assistant
 * Handles continuous listening and voice processing in the background
 */
class VoiceAssistantService : Service() {
    
    companion object {
        private const val TAG = "VoiceAssistantService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "omar_voice_assistant"
        
        // Service actions
        const val ACTION_START_LISTENING = "com.example.omarassistant.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.example.omarassistant.STOP_LISTENING"
        const val ACTION_PROCESS_TEXT = "com.example.omarassistant.PROCESS_TEXT"
        const val EXTRA_TEXT_COMMAND = "text_command"
        
        // Service state
        var isRunning = false
            private set
    }
    
    private lateinit var orchestrator: VoiceAssistantOrchestrator
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var currentState = AudioState.IDLE
    private var notificationManager: NotificationManager? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Initialize orchestrator
        orchestrator = VoiceAssistantOrchestrator(this).apply {
            onWakeWordDetected = { onWakeWordDetected() }
            onCommandProcessed = { command, response -> onCommandProcessed(command.originalText, response) }
            onError = { error -> onError(error) }
        }
        
        // Setup notification channel
        createNotificationChannel()
        
        // Initialize orchestrator
        serviceScope.launch {
            try {
                orchestrator.initialize()
                Log.d(TAG, "Orchestrator initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize orchestrator", e)
                stopSelf()
            }
        }
        
        // Observe state changes
        serviceScope.launch {
            orchestrator.state.collect { state ->
                currentState = state
                updateNotification()
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP_LISTENING -> stopListening()
            ACTION_PROCESS_TEXT -> {
                val text = intent.getStringExtra(EXTRA_TEXT_COMMAND)
                if (!text.isNullOrBlank()) {
                    processTextCommand(text)
                }
            }
            else -> startListening() // Default action
        }
        
        return START_STICKY // Restart if killed
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        
        isRunning = false
        orchestrator.cleanup()
        serviceScope.cancel()
        
        // Remove notification
        notificationManager?.cancel(NOTIFICATION_ID)
    }
    
    /**
     * Start voice listening
     */
    private fun startListening() {
        if (isRunning) return
        
        serviceScope.launch {
            try {
                // Start as foreground service
                startForeground(NOTIFICATION_ID, createNotification())
                isRunning = true
                
                // Start orchestrator
                orchestrator.start()
                
                Log.d(TAG, "Started listening for wake word")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting listening", e)
                stopSelf()
            }
        }
    }
    
    /**
     * Stop voice listening
     */
    private fun stopListening() {
        serviceScope.launch {
            orchestrator.stop()
            isRunning = false
            
            // Stop foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            
            stopSelf()
            Log.d(TAG, "Stopped listening")
        }
    }
    
    /**
     * Process text command
     */
    private fun processTextCommand(text: String) {
        serviceScope.launch {
            try {
                orchestrator.processTextCommand(text)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing text command: $text", e)
            }
        }
    }
    
    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OMAR Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "OMAR voice assistant background service"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create service notification
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, VoiceAssistantService::class.java).apply {
            action = ACTION_STOP_LISTENING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val statusText = when (currentState) {
            AudioState.IDLE -> "Idle"
            AudioState.LISTENING_FOR_WAKE_WORD -> "Listening for 'Omar'..."
            AudioState.WAKE_WORD_DETECTED -> "Wake word detected!"
            AudioState.RECORDING_COMMAND -> "Recording command..."
            AudioState.PROCESSING -> "Processing..."
            AudioState.SPEAKING -> "Speaking response..."
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OMAR Assistant")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_mic) // You'll need to add this icon
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    /**
     * Update notification with current state
     */
    private fun updateNotification() {
        if (isRunning) {
            val notification = createNotification()
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }
    
    /**
     * Handle wake word detection
     */
    private fun onWakeWordDetected() {
        Log.d(TAG, "Wake word detected in service")
        // Could send broadcast or update UI here
    }
    
    /**
     * Handle command processing completion
     */
    private fun onCommandProcessed(command: String, response: String) {
        Log.d(TAG, "Command processed - Command: '$command', Response: '$response'")
        // Could log to history, send broadcast, etc.
    }
    
    /**
     * Handle errors
     */
    private fun onError(error: String) {
        Log.e(TAG, "Orchestrator error: $error")
        
        // Show error notification
        val errorNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OMAR Assistant Error")
            .setContentText(error)
            .setSmallIcon(R.drawable.ic_error) // You'll need to add this icon
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        notificationManager?.notify(NOTIFICATION_ID + 1, errorNotification)
    }
}
