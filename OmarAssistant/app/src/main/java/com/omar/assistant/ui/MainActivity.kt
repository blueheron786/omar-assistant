package com.omar.assistant.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.omar.assistant.R
import com.omar.assistant.core.di.ServiceLocator
import com.omar.assistant.core.orchestrator.AssistantState
import com.omar.assistant.databinding.ActivityMainBinding
import com.omar.assistant.service.VoiceAssistantService
import com.omar.assistant.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Main activity for the Omar Assistant app
 * Provides UI controls and manages permissions
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
    }
    
    private lateinit var binding: ActivityMainBinding
    private val assistantOrchestrator by lazy { ServiceLocator.assistantOrchestrator }
    private val secureStorage by lazy { ServiceLocator.secureStorage }
    
    private var isServiceRunning = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkPermissions()
        observeAssistantState()
    }
    
    private fun setupUI() {
        binding.apply {
            // Start/Stop button
            buttonStartStop.setOnClickListener {
                if (isServiceRunning) {
                    stopVoiceAssistant()
                } else {
                    startVoiceAssistant()
                }
            }
            
            // Manual input button
            buttonManualInput.setOnClickListener {
                val input = editTextManualInput.text.toString().trim()
                if (input.isNotEmpty()) {
                    processManualInput(input)
                    editTextManualInput.text.clear()
                }
            }
            
            // Test TTS button
            buttonTestTts.setOnClickListener {
                testTextToSpeech()
            }
        }
        
        updateUI(AssistantState.IDLE)
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        Dexter.withContext(this)
            .withPermissions(permissions)
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    if (report.areAllPermissionsGranted()) {
                        Log.d(TAG, "All permissions granted")
                        checkConfiguration()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Permissions are required for the assistant to work",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                
                override fun onPermissionRationaleShouldBeShown(
                    permissions: List<PermissionRequest>,
                    token: PermissionToken
                ) {
                    token.continuePermissionRequest()
                }
            })
            .check()
    }
    
    private fun checkConfiguration() {
        if (!secureStorage.hasGeminiApiKey()) {
            Toast.makeText(
                this,
                "Please configure your API key in settings",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun startVoiceAssistant() {
        if (!secureStorage.hasGeminiApiKey()) {
            Toast.makeText(this, "Please configure API key first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Disable start button and show validation progress
        binding.buttonStartStop.isEnabled = false
        binding.buttonStartStop.text = "Validating..."
        
        lifecycleScope.launch {
            try {
                val llmProvider = ServiceLocator.llmProvider
                val result = llmProvider.validateApiKey()
                
                result.fold(
                    onSuccess = { isValid ->
                        if (isValid) {
                            // API key is valid, start the service
                            VoiceAssistantService.startService(this@MainActivity)
                            isServiceRunning = true
                            updateUI(AssistantState.LISTENING_FOR_WAKE_WORD)
                            Toast.makeText(this@MainActivity, "Voice assistant started", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "❌ API key validation failed", Toast.LENGTH_LONG).show()
                            binding.buttonStartStop.isEnabled = true
                            binding.buttonStartStop.text = "Start Assistant"
                        }
                    },
                    onFailure = { exception ->
                        val errorMessage = when {
                            exception.message?.contains("API_KEY_INVALID") == true -> "Invalid API key"
                            exception.message?.contains("PERMISSION_DENIED") == true -> "API key lacks necessary permissions"
                            exception.message?.contains("QUOTA_EXCEEDED") == true -> "API quota exceeded"
                            exception.message?.contains("No API key") == true -> "No API key configured"
                            else -> "Validation failed: ${exception.message}"
                        }
                        Toast.makeText(this@MainActivity, "❌ Cannot start: $errorMessage", Toast.LENGTH_LONG).show()
                        binding.buttonStartStop.isEnabled = true
                        binding.buttonStartStop.text = "Start Assistant"
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "❌ Failed to validate API key: ${e.message}", Toast.LENGTH_LONG).show()
                binding.buttonStartStop.isEnabled = true
                binding.buttonStartStop.text = "Start Assistant"
            }
        }
    }
    
    private fun stopVoiceAssistant() {
        VoiceAssistantService.stopService(this)
        isServiceRunning = false
        updateUI(AssistantState.IDLE)
    }
    
    private fun processManualInput(input: String) {
        lifecycleScope.launch {
            try {
                assistantOrchestrator.processDirectInput(input)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing manual input", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error processing input: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun testTextToSpeech() {
        lifecycleScope.launch {
            try {
                val ttsManager = ServiceLocator.textToSpeechManager
                if (!ttsManager.initialize()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to initialize text-to-speech",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                
                ttsManager.speak("Hello! I am Omar, your voice assistant.")
            } catch (e: Exception) {
                Log.e(TAG, "Error testing TTS", e)
                Toast.makeText(this@MainActivity, "TTS test failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun observeAssistantState() {
        lifecycleScope.launch {
            assistantOrchestrator.state.collect { state ->
                updateUI(state)
            }
        }
        
        lifecycleScope.launch {
            assistantOrchestrator.lastResponse.collect { response ->
                response?.let {
                    binding.textViewLastResponse.text = "Last response: $it"
                }
            }
        }
    }
    
    private fun updateUI(state: AssistantState) {
        binding.apply {
            textViewStatus.text = "Status: ${state.name}"
            
            when (state) {
                AssistantState.IDLE -> {
                    buttonStartStop.text = "Start Assistant"
                    buttonStartStop.isEnabled = true
                    progressBar.visibility = android.view.View.GONE
                }
                AssistantState.LISTENING_FOR_WAKE_WORD -> {
                    buttonStartStop.text = "Stop Assistant"
                    buttonStartStop.isEnabled = true
                    progressBar.visibility = android.view.View.VISIBLE
                }
                AssistantState.LISTENING_FOR_COMMAND -> {
                    progressBar.visibility = android.view.View.VISIBLE
                }
                AssistantState.PROCESSING -> {
                    progressBar.visibility = android.view.View.VISIBLE
                }
                AssistantState.SPEAKING -> {
                    progressBar.visibility = android.view.View.VISIBLE
                }
                AssistantState.STOPPING -> {
                    buttonStartStop.isEnabled = false
                    progressBar.visibility = android.view.View.VISIBLE
                }
                AssistantState.ERROR -> {
                    buttonStartStop.text = "Start Assistant"
                    buttonStartStop.isEnabled = true
                    progressBar.visibility = android.view.View.GONE
                    Toast.makeText(this@MainActivity, "Assistant error occurred", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
    
    override fun onDestroy() {
        super.onDestroy()
        if (isServiceRunning) {
            stopVoiceAssistant()
        }
    }
}
