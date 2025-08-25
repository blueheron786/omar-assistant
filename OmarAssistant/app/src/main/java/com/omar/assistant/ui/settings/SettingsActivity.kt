package com.omar.assistant.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
        // Simple validation
        val apiKey = binding.editTextApiKey.text.toString().trim()
        if (apiKey.isEmpty() || apiKey.startsWith("****")) {
            if (!secureStorage.hasGeminiApiKey()) {
                Toast.makeText(this, "Please enter an API key first", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        // For now, just show success if API key is present
        if (secureStorage.hasGeminiApiKey() || (!apiKey.isEmpty() && !apiKey.startsWith("****"))) {
            Toast.makeText(this, "API key format looks valid", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Please enter a valid API key", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
