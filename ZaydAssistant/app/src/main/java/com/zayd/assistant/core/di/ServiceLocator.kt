package com.zayd.assistant.core.di

import android.content.Context
import com.zayd.assistant.audio.AudioManager
import com.zayd.assistant.audio.VoiceActivityDetector
import com.zayd.assistant.audio.WakeWordDetector
import com.zayd.assistant.core.orchestrator.AssistantOrchestrator
import com.zayd.assistant.core.storage.SecureStorage
import com.zayd.assistant.llm.LLMProvider
import com.zayd.assistant.llm.gemini.GeminiProvider
import com.zayd.assistant.speech.SpeechToTextManager
import com.zayd.assistant.speech.TextToSpeechManager
import com.zayd.assistant.toolbox.ToolboxManager
import com.zayd.assistant.toolbox.tools.SmartLightTool
import com.zayd.assistant.toolbox.tools.FlashlightTool
import com.zayd.assistant.toolbox.tools.PhoneTool

/**
 * Simple service locator for dependency injection
 * Provides singleton instances of core components
 */
object ServiceLocator {
    
    private lateinit var context: Context
    
    // Core components
    private var _secureStorage: SecureStorage? = null
    private var _audioManager: AudioManager? = null
    private var _wakeWordDetector: WakeWordDetector? = null
    private var _voiceActivityDetector: VoiceActivityDetector? = null
    private var _speechToTextManager: SpeechToTextManager? = null
    private var _textToSpeechManager: TextToSpeechManager? = null
    private var _llmProvider: LLMProvider? = null
    private var _toolboxManager: ToolboxManager? = null
    private var _assistantOrchestrator: AssistantOrchestrator? = null
    
    fun initialize(appContext: Context) {
        context = appContext.applicationContext
    }
    
    val secureStorage: SecureStorage
        get() = _secureStorage ?: SecureStorage(context).also { _secureStorage = it }
    
    val audioManager: AudioManager
        get() = _audioManager ?: AudioManager(context).also { _audioManager = it }
    
    val wakeWordDetector: WakeWordDetector
        get() = _wakeWordDetector ?: WakeWordDetector(context, audioManager).also { _wakeWordDetector = it }
    
    val voiceActivityDetector: VoiceActivityDetector
        get() = _voiceActivityDetector ?: VoiceActivityDetector().also { _voiceActivityDetector = it }
    
    val speechToTextManager: SpeechToTextManager
        get() = _speechToTextManager ?: SpeechToTextManager(context).also { _speechToTextManager = it }
    
    val textToSpeechManager: TextToSpeechManager
        get() = _textToSpeechManager ?: TextToSpeechManager(context).also { _textToSpeechManager = it }
    
    val llmProvider: LLMProvider
        get() = _llmProvider ?: createLLMProvider().also { _llmProvider = it }
    
    val toolboxManager: ToolboxManager
        get() = _toolboxManager ?: createToolboxManager().also { _toolboxManager = it }
    
    val assistantOrchestrator: AssistantOrchestrator
        get() = _assistantOrchestrator ?: AssistantOrchestrator(
            wakeWordDetector,
            voiceActivityDetector,
            speechToTextManager,
            llmProvider,
            toolboxManager,
            textToSpeechManager
        ).also { _assistantOrchestrator = it }
    
    private fun createLLMProvider(): LLMProvider {
        // For now, only Gemini is supported, but this can be extended
        return GeminiProvider(secureStorage)
    }
    
    private fun createToolboxManager(): ToolboxManager {
        return ToolboxManager().apply {
            // Register default tools
            registerTool(SmartLightTool())
            registerTool(FlashlightTool(context))
            registerTool(PhoneTool(context))
        }
    }
    
    fun reset() {
        _secureStorage = null
        _audioManager = null
        _wakeWordDetector = null
        _voiceActivityDetector = null
        _speechToTextManager = null
        _textToSpeechManager = null
        _llmProvider = null
        _toolboxManager = null
        _assistantOrchestrator = null
    }
}
