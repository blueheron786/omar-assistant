package com.omar.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.omar.assistant.databinding.ActivityMainBinding
import com.omar.assistant.service.VoiceAssistantService
import com.omar.assistant.core.OmarAssistant
import com.omar.assistant.core.AssistantState
import kotlinx.coroutines.launch

/**
 * MainActivity - Main UI for the Omar Voice Assistant
 * 
 * This activity provides the main interface for the voice assistant,
 * handles permissions, and manages the voice assistant service.
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var omarAssistant: OmarAssistant
    private var isListening = false
    
    // Permission launcher for microphone access
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            initializeAssistant()
        } else {
            showPermissionDeniedMessage()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkPermissionsAndInitialize()
    }
    
    private fun setupUI() {
        binding.btnStartStop.setOnClickListener {
            toggleListening()
        }
        
        binding.btnSettings.setOnClickListener {
            // TODO: Open settings activity
            showToast("Settings coming soon!")
        }
        
        // Setup debug input (only visible in debug builds)
        setupDebugInput()
    }
    
    private fun setupDebugInput() {
        // Show debug section (can be toggled in production)
        binding.debugInputSection.visibility = View.VISIBLE
        
        binding.btnDebugSend.setOnClickListener {
            val debugText = binding.etDebugInput.text.toString().trim()
            if (debugText.isNotEmpty()) {
                processDebugInput(debugText)
                binding.etDebugInput.text.clear()
            }
        }
        
        // Handle enter key in debug input
        binding.etDebugInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                binding.btnDebugSend.performClick()
                true
            } else {
                false
            }
        }
    }
    
    private fun processDebugInput(text: String) {
        if (!::omarAssistant.isInitialized) {
            showToast("Assistant not initialized")
            return
        }
        
        lifecycleScope.launch {
            try {
                // Add debug command to history
                updateCommandHistory("(Debug) $text")
                
                // Process the text as if it came from speech recognition
                omarAssistant.processDebugCommand(text)
                
                showToast("Debug command processed: $text")
            } catch (e: Exception) {
                showToast("Error processing debug command: ${e.message}")
            }
        }
    }
    
    private fun checkPermissionsAndInitialize() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                initializeAssistant()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    
    private fun initializeAssistant() {
        omarAssistant = OmarAssistant(this)
        
        // Observe assistant state changes
        lifecycleScope.launch {
            omarAssistant.stateFlow.collect { state ->
                updateUI(state)
            }
        }
        
        // Observe command results
        lifecycleScope.launch {
            omarAssistant.commandFlow.collect { command ->
                updateCommandHistory(command)
            }
        }
        
        // Observe responses
        lifecycleScope.launch {
            omarAssistant.responseFlow.collect { response ->
                updateLastCommand(response)
            }
        }
    }
    
    private fun toggleListening() {
        if (isListening) {
            stopListening()
        } else {
            startListening()
        }
    }
    
    private fun startListening() {
        lifecycleScope.launch {
            try {
                omarAssistant.startListening()
                isListening = true
                updateStartStopButton()
                
                // Start foreground service for continuous listening
                val serviceIntent = Intent(this@MainActivity, VoiceAssistantService::class.java)
                ContextCompat.startForegroundService(this@MainActivity, serviceIntent)
                
            } catch (e: Exception) {
                showToast("Failed to start listening: ${e.message}")
            }
        }
    }
    
    private fun stopListening() {
        lifecycleScope.launch {
            omarAssistant.stopListening()
            isListening = false
            updateStartStopButton()
            
            // Stop foreground service
            val serviceIntent = Intent(this@MainActivity, VoiceAssistantService::class.java)
            stopService(serviceIntent)
        }
    }
    
    private fun updateUI(state: AssistantState) {
        runOnUiThread {
            when (state) {
                AssistantState.IDLE -> {
                    binding.tvStatus.text = getString(R.string.assistant_ready)
                    binding.statusIndicator.setBackgroundResource(R.drawable.circle_indicator)
                }
                AssistantState.LISTENING_FOR_WAKE_WORD -> {
                    binding.tvStatus.text = "Listening for 'Omar'..."
                    updateStatusIndicatorColor(R.color.status_listening)
                }
                AssistantState.WAKE_WORD_DETECTED -> {
                    binding.tvStatus.text = getString(R.string.wake_word_detected)
                    updateStatusIndicatorColor(R.color.omar_accent)
                }
                AssistantState.LISTENING_FOR_COMMAND -> {
                    binding.tvStatus.text = getString(R.string.listening_for_command)
                    updateStatusIndicatorColor(R.color.status_listening)
                }
                AssistantState.PROCESSING_COMMAND -> {
                    binding.tvStatus.text = getString(R.string.processing_command)
                    updateStatusIndicatorColor(R.color.status_processing)
                }
                AssistantState.SPEAKING_RESPONSE -> {
                    binding.tvStatus.text = "Speaking response..."
                    updateStatusIndicatorColor(R.color.omar_secondary)
                }
            }
        }
    }
    
    private fun updateStatusIndicatorColor(colorRes: Int) {
        // Create a copy of the circle indicator with new color
        val color = ContextCompat.getColor(this, colorRes)
        binding.statusIndicator.setBackgroundColor(color)
    }
    
    private fun updateStartStopButton() {
        binding.btnStartStop.text = if (isListening) {
            getString(R.string.stop_listening)
        } else {
            getString(R.string.start_listening)
        }
    }
    
    private fun updateCommandHistory(command: String) {
        runOnUiThread {
            val currentText = binding.tvCommandHistory.text.toString()
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val newEntry = "[$timestamp] You: $command\n"
            binding.tvCommandHistory.text = currentText + newEntry
        }
    }
    
    private fun updateLastCommand(response: String) {
        runOnUiThread {
            binding.tvLastCommand.text = response
            
            // Also add to command history
            val currentText = binding.tvCommandHistory.text.toString()
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            val newEntry = "[$timestamp] Omar: $response\n"
            binding.tvCommandHistory.text = currentText + newEntry
        }
    }
    
    private fun showPermissionDeniedMessage() {
        showToast(getString(R.string.permission_required))
        binding.tvStatus.text = "Microphone permission required"
        binding.btnStartStop.text = getString(R.string.grant_permission)
        binding.btnStartStop.setOnClickListener {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::omarAssistant.isInitialized) {
            lifecycleScope.launch {
                omarAssistant.cleanup()
            }
        }
    }
}
