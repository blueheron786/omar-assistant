package com.omar.assistant.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.omar.assistant.core.di.ServiceLocator
import com.omar.assistant.databinding.ActivitySettingsBinding

/**
 * Settings activity for configuring the voice assistant
 * Allows setting API keys and other configuration options
 */
class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private val secureStorage by lazy { ServiceLocator.secureStorage }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupActionBar()
        setupUI()
        loadCurrentSettings()
    }
    
    private fun setupActionBar() {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Settings"
        }
    }
    
    private fun setupUI() {
        binding.apply {
            // Save button
            buttonSave.setOnClickListener {
                saveSettings()
            }
            
            // Clear data button
            buttonClearData.setOnClickListener {
                clearAllData()
            }
            
            // Test connection button
            buttonTestConnection.setOnClickListener {
                testConnection()
            }
        }
    }
    
    private fun loadCurrentSettings() {
        binding.apply {
            // Load current API key (masked for security)
            val currentApiKey = secureStorage.getGeminiApiKey()
            if (!currentApiKey.isNullOrBlank()) {
                editTextApiKey.setText("****" + currentApiKey.takeLast(4))
            }
            
            // Load other settings
            val selectedProvider = secureStorage.getSelectedLLMProvider()
            when (selectedProvider) {
                "gemini" -> radioButtonGemini.isChecked = true
                else -> radioButtonGemini.isChecked = true // Default to Gemini
            }
            
            val wakeWordSensitivity = secureStorage.getWakeWordSensitivity()
            seekBarWakeWordSensitivity.progress = (wakeWordSensitivity * 100).toInt()
            textViewWakeWordSensitivity.text = "Wake Word Sensitivity: ${(wakeWordSensitivity * 100).toInt()}%"
            
            val vadSensitivity = secureStorage.getVADSensitivity()
            seekBarVadSensitivity.progress = (vadSensitivity * 100).toInt()
            textViewVadSensitivity.text = "Voice Activity Sensitivity: ${(vadSensitivity * 100).toInt()}%"
            
            // Setup seekbar listeners
            seekBarWakeWordSensitivity.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    textViewWakeWordSensitivity.text = "Wake Word Sensitivity: $progress%"
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
            
            seekBarVadSensitivity.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    textViewVadSensitivity.text = "Voice Activity Sensitivity: $progress%"
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })
        }
    }
    
    private fun saveSettings() {
        binding.apply {
            try {
                // Save API key (only if not masked)
                val apiKeyText = editTextApiKey.text.toString().trim()
                if (apiKeyText.isNotEmpty() && !apiKeyText.startsWith("****")) {
                    secureStorage.setGeminiApiKey(apiKeyText)
                }
                
                // Save LLM provider selection
                val selectedProvider = when {
                    radioButtonGemini.isChecked -> "gemini"
                    else -> "gemini" // Default
                }
                secureStorage.setSelectedLLMProvider(selectedProvider)
                
                // Save sensitivity settings
                val wakeWordSensitivity = seekBarWakeWordSensitivity.progress / 100.0f
                secureStorage.setWakeWordSensitivity(wakeWordSensitivity)
                
                val vadSensitivity = seekBarVadSensitivity.progress / 100.0f
                secureStorage.setVADSensitivity(vadSensitivity)
                
                Toast.makeText(this@SettingsActivity, "Settings saved successfully", Toast.LENGTH_SHORT).show()
                finish()
                
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Error saving settings: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun clearAllData() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Clear All Data")
            .setMessage("This will remove all saved settings including API keys. Are you sure?")
            .setPositiveButton("Yes") { _, _ ->
                secureStorage.clearAll()
                loadCurrentSettings()
                Toast.makeText(this, "All data cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun testConnection() {
        // First save the API key if it's been entered
        val apiKeyText = binding.editTextApiKey.text.toString().trim()
        if (apiKeyText.isNotEmpty() && !apiKeyText.startsWith("****")) {
            secureStorage.setGeminiApiKey(apiKeyText)
        }
        
        // Check if we have an API key to test
        if (!secureStorage.hasGeminiApiKey()) {
            Toast.makeText(this, "Please enter an API key first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Disable the test button and show progress
        binding.buttonTestConnection.isEnabled = false
        binding.buttonTestConnection.text = "Testing..."
        
        // Test the API key using coroutines
        lifecycleScope.launch {
            try {
                val llmProvider = ServiceLocator.llmProvider
                val result = llmProvider.validateApiKey()
                
                result.fold(
                    onSuccess = { isValid ->
                        if (isValid) {
                            Toast.makeText(this@SettingsActivity, "✅ API key is valid and working!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@SettingsActivity, "❌ API key validation failed", Toast.LENGTH_LONG).show()
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
                        Toast.makeText(this@SettingsActivity, "❌ $errorMessage", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "❌ Connection test failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                // Re-enable the test button
                binding.buttonTestConnection.isEnabled = true
                binding.buttonTestConnection.text = "Test Connection"
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
