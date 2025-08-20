package com.example.omarassistant.ui

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.omarassistant.R
import com.example.omarassistant.config.ConfigManager
import com.example.omarassistant.databinding.ActivitySettingsBinding
import com.example.omarassistant.model.AssistantConfig
import kotlinx.coroutines.launch

/**
 * Settings activity for configuring OMAR assistant
 * Provides user-friendly interface for all configuration options
 */
class SettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SettingsActivity"
    }
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var configManager: ConfigManager
    private var currentConfig: AssistantConfig? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        configManager = ConfigManager.getInstance(this)
        
        setupUI()
        loadCurrentSettings()
    }
    
    /**
     * Setup UI components and listeners
     */
    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.settings_title)
            setDisplayHomeAsUpEnabled(true)
        }
        
        // Save button
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
        
        // Reset button
        binding.btnReset.setOnClickListener {
            resetSettings()
        }
        
        // Test API button
        binding.btnTestApi.setOnClickListener {
            testApiConnection()
        }
        
        // Wake word sensitivity slider
        binding.seekWakeWordSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val sensitivity = progress / 100f
                    binding.tvWakeWordSensitivity.text = "Wake Word Sensitivity: ${(sensitivity * 100).toInt()}%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // VAD sensitivity slider
        binding.seekVadSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val sensitivity = progress / 100f
                    binding.tvVadSensitivity.text = "Voice Detection Sensitivity: ${(sensitivity * 100).toInt()}%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Voice volume slider
        binding.seekVoiceVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val volume = progress / 100f
                    binding.tvVoiceVolume.text = "Voice Volume: ${(volume * 100).toInt()}%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    
    /**
     * Load current settings from config
     */
    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            try {
                currentConfig = configManager.getConfig()
                currentConfig?.let { config ->
                    populateUIWithConfig(config)
                }
            } catch (e: Exception) {
                showToast("Error loading settings: ${e.message}")
            }
        }
    }
    
    /**
     * Populate UI with configuration values
     */
    private fun populateUIWithConfig(config: AssistantConfig) {
        // Basic settings
        binding.etWakeWord.setText(config.wakeWord)
        binding.etGeminiApiKey.setText(config.geminiApiKey)
        
        // Language selection
        val languages = arrayOf("en", "ar", "es", "fr", "de")
        val languageIndex = languages.indexOf(config.language).takeIf { it >= 0 } ?: 0
        binding.spinnerLanguage.setSelection(languageIndex)
        
        // Continuous listening
        binding.switchContinuousListening.isChecked = config.enableContinuousListening
        
        // Sensitivity settings
        val wakeWordProgress = (config.wakeWordSensitivity * 100).toInt()
        binding.seekWakeWordSensitivity.progress = wakeWordProgress
        binding.tvWakeWordSensitivity.text = "Wake Word Sensitivity: $wakeWordProgress%"
        
        val vadProgress = (config.vadSensitivity * 100).toInt()
        binding.seekVadSensitivity.progress = vadProgress
        binding.tvVadSensitivity.text = "Voice Detection Sensitivity: $vadProgress%"
        
        val volumeProgress = (config.voiceVolume * 100).toInt()
        binding.seekVoiceVolume.progress = volumeProgress
        binding.tvVoiceVolume.text = "Voice Volume: $volumeProgress%"
        
        // Smart home settings
        binding.etSmartHomeApiUrl.setText(config.smartHomeApiUrl)
        binding.etSmartHomeApiKey.setText(config.smartHomeApiKey)
    }
    
    /**
     * Save current settings
     */
    private fun saveSettings() {
        lifecycleScope.launch {
            try {
                val languages = arrayOf("en", "ar", "es", "fr", "de")
                
                val newConfig = AssistantConfig(
                    wakeWord = binding.etWakeWord.text.toString().trim().lowercase(),
                    geminiApiKey = binding.etGeminiApiKey.text.toString().trim(),
                    language = languages[binding.spinnerLanguage.selectedItemPosition],
                    voiceVolume = binding.seekVoiceVolume.progress / 100f,
                    wakeWordSensitivity = binding.seekWakeWordSensitivity.progress / 100f,
                    vadSensitivity = binding.seekVadSensitivity.progress / 100f,
                    enableContinuousListening = binding.switchContinuousListening.isChecked,
                    smartHomeApiUrl = binding.etSmartHomeApiUrl.text.toString().trim(),
                    smartHomeApiKey = binding.etSmartHomeApiKey.text.toString().trim()
                )
                
                // Validate settings
                if (validateSettings(newConfig)) {
                    configManager.saveConfig(newConfig)
                    currentConfig = newConfig
                    showToast("Settings saved successfully!")
                    
                    // Enable save button feedback
                    binding.btnSave.text = "Saved!"
                    binding.btnSave.postDelayed({
                        binding.btnSave.text = "Save Settings"
                    }, 1500)
                }
                
            } catch (e: Exception) {
                showToast("Error saving settings: ${e.message}")
            }
        }
    }
    
    /**
     * Validate settings before saving
     */
    private fun validateSettings(config: AssistantConfig): Boolean {
        if (config.wakeWord.isEmpty()) {
            showToast("Wake word cannot be empty")
            binding.etWakeWord.requestFocus()
            return false
        }
        
        if (config.wakeWord.length < 2) {
            showToast("Wake word must be at least 2 characters")
            binding.etWakeWord.requestFocus()
            return false
        }
        
        if (config.geminiApiKey.isEmpty()) {
            showToast("Gemini API key is required for the assistant to work")
            binding.etGeminiApiKey.requestFocus()
            return false
        }
        
        // Validate API key format (basic check)
        if (!config.geminiApiKey.startsWith("AIza")) {
            showToast("Invalid Gemini API key format. Should start with 'AIza'")
            binding.etGeminiApiKey.requestFocus()
            return false
        }
        
        return true
    }
    
    /**
     * Reset settings to defaults
     */
    private fun resetSettings() {
        val defaultConfig = AssistantConfig()
        populateUIWithConfig(defaultConfig)
        showToast("Settings reset to defaults")
    }
    
    /**
     * Test API connection with current key
     */
    private fun testApiConnection() {
        val apiKey = binding.etGeminiApiKey.text.toString().trim()
        
        if (apiKey.isEmpty()) {
            showToast("Please enter an API key first")
            return
        }
        
        binding.btnTestApi.isEnabled = false
        binding.btnTestApi.text = "Testing..."
        
        lifecycleScope.launch {
            try {
                // Create temporary API service for testing
                val testService = com.example.omarassistant.api.GeminiApiService(apiKey)
                val isConnected = testService.testConnection()
                
                runOnUiThread {
                    if (isConnected) {
                        showToast("API connection successful!")
                        binding.tvApiStatus.text = "✓ API Key Valid"
                        binding.tvApiStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                    } else {
                        showToast("API connection failed. Please check your key.")
                        binding.tvApiStatus.text = "✗ API Key Invalid"
                        binding.tvApiStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                    }
                }
                
            } catch (e: Exception) {
                runOnUiThread {
                    showToast("API test error: ${e.message}")
                    binding.tvApiStatus.text = "✗ Connection Error"
                    binding.tvApiStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            } finally {
                runOnUiThread {
                    binding.btnTestApi.isEnabled = true
                    binding.btnTestApi.text = "Test API Key"
                }
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    /**
     * Show toast message
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
