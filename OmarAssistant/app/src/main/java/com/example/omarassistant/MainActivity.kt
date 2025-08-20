package com.example.omarassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.omarassistant.config.ConfigManager
import com.example.omarassistant.databinding.ActivityMainBinding
import com.example.omarassistant.model.AudioState
import com.example.omarassistant.orchestrator.VoiceAssistantOrchestrator
import com.example.omarassistant.service.VoiceAssistantService
import com.example.omarassistant.ui.SettingsActivity
import kotlinx.coroutines.launch

/**
 * Main activity for OMAR Voice Assistant
 * Provides the primary interface for interacting with the assistant
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var configManager: ConfigManager
    private var orchestrator: VoiceAssistantOrchestrator? = null
    
    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeAssistant()
        } else {
            showPermissionDeniedMessage()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        configManager = ConfigManager.getInstance(this)
        
        setupUI()
        checkPermissionsAndInitialize()
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        orchestrator?.cleanup()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    /**
     * Setup UI components and listeners
     */
    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "OMAR Assistant"
        
        // Start/Stop button
        binding.btnToggleService.setOnClickListener {
            toggleVoiceService()
        }
        
        // Manual text input
        binding.btnSendText.setOnClickListener {
            val text = binding.etTextInput.text.toString().trim()
            if (text.isNotEmpty()) {
                processTextCommand(text)
                binding.etTextInput.text?.clear()
            }
        }
        
        // Test API button
        binding.btnTestApi.setOnClickListener {
            testApiConnection()
        }
        
        // Settings button
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    
    /**
     * Check permissions and initialize if granted
     */
    private fun checkPermissionsAndInitialize() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isEmpty()) {
            initializeAssistant()
        } else {
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }
    
    /**
     * Initialize the voice assistant
     */
    private fun initializeAssistant() {
        lifecycleScope.launch {
            try {
                // Check if this is first run
                if (configManager.isFirstRun()) {
                    showWelcomeMessage()
                }
                
                // Initialize orchestrator for testing
                orchestrator = VoiceAssistantOrchestrator(this@MainActivity).apply {
                    onWakeWordDetected = { 
                        runOnUiThread { 
                            showToast("Wake word detected!")
                            binding.tvStatus.text = "Processing command..."
                        }
                    }
                    onCommandProcessed = { command, response ->
                        runOnUiThread {
                            binding.tvLastCommand.text = "\"${command.originalText}\""
                            binding.tvLastResponse.text = response
                            binding.tvStatus.text = "Listening for 'Omar'..."
                        }
                    }
                    onError = { error ->
                        runOnUiThread {
                            showToast("Error: $error")
                            binding.tvStatus.text = "Error occurred"
                        }
                    }
                }
                
                orchestrator?.initialize()
                
                // Observe state changes
                orchestrator?.state?.let { stateFlow ->
                    lifecycleScope.launch {
                        stateFlow.collect { state ->
                            updateStateDisplay(state)
                        }
                    }
                }
                
                // Observe audio level
                orchestrator?.audioLevel?.let { levelFlow ->
                    lifecycleScope.launch {
                        levelFlow.collect { level ->
                            binding.audioLevelProgress.progress = (level * 100).toInt()
                        }
                    }
                }
                
                updateUI()
                
            } catch (e: Exception) {
                showToast("Failed to initialize assistant: ${e.message}")
            }
        }
    }
    
    /**
     * Toggle voice service on/off
     */
    private fun toggleVoiceService() {
        if (VoiceAssistantService.isRunning) {
            stopVoiceService()
        } else {
            startVoiceService()
        }
    }
    
    /**
     * Start voice assistant service
     */
    private fun startVoiceService() {
        lifecycleScope.launch {
            val config = configManager.getConfig()
            
            if (config.geminiApiKey.isEmpty()) {
                showToast("Please configure Gemini API key in settings first")
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                return@launch
            }
            
            val serviceIntent = Intent(this@MainActivity, VoiceAssistantService::class.java).apply {
                action = VoiceAssistantService.ACTION_START_LISTENING
            }
            
            startForegroundService(serviceIntent)
            updateUI()
        }
    }
    
    /**
     * Stop voice assistant service
     */
    private fun stopVoiceService() {
        val serviceIntent = Intent(this, VoiceAssistantService::class.java).apply {
            action = VoiceAssistantService.ACTION_STOP_LISTENING
        }
        startService(serviceIntent)
        updateUI()
    }
    
    /**
     * Process text command manually
     */
    private fun processTextCommand(text: String) {
        if (VoiceAssistantService.isRunning) {
            // Send to service
            val serviceIntent = Intent(this, VoiceAssistantService::class.java).apply {
                action = VoiceAssistantService.ACTION_PROCESS_TEXT
                putExtra(VoiceAssistantService.EXTRA_TEXT_COMMAND, text)
            }
            startService(serviceIntent)
        } else {
            // Process directly with orchestrator
            lifecycleScope.launch {
                try {
                    orchestrator?.processTextCommand(text)
                } catch (e: Exception) {
                    showToast("Error processing command: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Test API connection
     */
    private fun testApiConnection() {
        binding.btnTestApi.isEnabled = false
        binding.btnTestApi.text = "Testing..."
        
        lifecycleScope.launch {
            try {
                val isConnected = orchestrator?.testApiConnection() ?: false
                
                runOnUiThread {
                    if (isConnected) {
                        showToast("API connection successful!")
                        binding.tvApiStatus.text = "API Status: Connected"
                    } else {
                        showToast("API connection failed. Check your settings.")
                        binding.tvApiStatus.text = "API Status: Failed"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast("API test error: ${e.message}")
                    binding.tvApiStatus.text = "API Status: Error"
                }
            } finally {
                runOnUiThread {
                    binding.btnTestApi.isEnabled = true
                    binding.btnTestApi.text = "Test API"
                }
            }
        }
    }
    
    /**
     * Update UI based on current state
     */
    private fun updateUI() {
        val isRunning = VoiceAssistantService.isRunning
        
        binding.btnToggleService.text = if (isRunning) "Stop Assistant" else "Start Assistant"
        binding.serviceStatus.text = if (isRunning) "Service: Running" else "Service: Stopped"
        
        // Update status text
        if (!isRunning) {
            binding.tvStatus.text = "Assistant stopped"
        }
    }
    
    /**
     * Update state display
     */
    private fun updateStateDisplay(state: AudioState) {
        binding.tvStatus.text = when (state) {
            AudioState.IDLE -> "Assistant ready"
            AudioState.LISTENING_FOR_WAKE_WORD -> "Listening for 'Omar'..."
            AudioState.WAKE_WORD_DETECTED -> "Wake word detected!"
            AudioState.RECORDING_COMMAND -> "Recording command..."
            AudioState.PROCESSING -> "Processing..."
            AudioState.SPEAKING -> "Speaking..."
        }
    }
    
    /**
     * Show welcome message for first time users
     */
    private fun showWelcomeMessage() {
        Toast.makeText(
            this,
            "Welcome to OMAR! Please configure your API key in settings to get started.",
            Toast.LENGTH_LONG
        ).show()
    }
    
    /**
     * Show permission denied message
     */
    private fun showPermissionDeniedMessage() {
        Toast.makeText(
            this,
            "OMAR needs microphone permission to work. Please grant the permission and restart the app.",
            Toast.LENGTH_LONG
        ).show()
    }
    
    /**
     * Show toast message
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
