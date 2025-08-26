package com.omar.assistant.core.di

import android.content.Context
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Unit tests for ServiceLocator
 * Tests dependency injection, singleton behavior, and service initialization
 */
@RunWith(RobolectricTestRunner::class)
class ServiceLocatorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ServiceLocator.initialize(context)
        ServiceLocator.reset() // Start with clean state
    }

    @Test
    fun `test initialization with context`() {
        ServiceLocator.initialize(context)
        
        // Should not throw exception
        assertNotNull(context)
    }

    @Test
    fun `test secureStorage singleton behavior`() {
        ServiceLocator.initialize(context)
        
        val storage1 = ServiceLocator.secureStorage
        val storage2 = ServiceLocator.secureStorage
        
        assertSame("SecureStorage should be singleton", storage1, storage2)
    }

    @Test
    fun `test audioManager singleton behavior`() {
        ServiceLocator.initialize(context)
        
        val audio1 = ServiceLocator.audioManager
        val audio2 = ServiceLocator.audioManager
        
        assertSame("AudioManager should be singleton", audio1, audio2)
    }

    @Test
    fun `test wakeWordDetector singleton behavior`() {
        ServiceLocator.initialize(context)
        
        val detector1 = ServiceLocator.wakeWordDetector
        val detector2 = ServiceLocator.wakeWordDetector
        
        assertSame("WakeWordDetector should be singleton", detector1, detector2)
    }

    @Test
    fun `test voiceActivityDetector singleton behavior`() {
        ServiceLocator.initialize(context)
        
        val detector1 = ServiceLocator.voiceActivityDetector
        val detector2 = ServiceLocator.voiceActivityDetector
        
        assertSame("VoiceActivityDetector should be singleton", detector1, detector2)
    }

    @Test
    fun `test speechToTextManager singleton behavior`() {
        ServiceLocator.initialize(context)
        
        val manager1 = ServiceLocator.speechToTextManager
        val manager2 = ServiceLocator.speechToTextManager
        
        assertSame("SpeechToTextManager should be singleton", manager1, manager2)
    }

    @Test
    fun `test textToSpeechManager singleton behavior`() {
        ServiceLocator.initialize(context)
        
        val manager1 = ServiceLocator.textToSpeechManager
        val manager2 = ServiceLocator.textToSpeechManager
        
        assertSame("TextToSpeechManager should be singleton", manager1, manager2)
    }

    @Test
    fun `test llmProvider singleton behavior`() {
        ServiceLocator.initialize(context)
        
        val provider1 = ServiceLocator.llmProvider
        val provider2 = ServiceLocator.llmProvider
        
        assertSame("LLMProvider should be singleton", provider1, provider2)
    }

    @Test
    fun `test toolboxManager singleton behavior`() {
        ServiceLocator.initialize(context)
        
        val manager1 = ServiceLocator.toolboxManager
        val manager2 = ServiceLocator.toolboxManager
        
        assertSame("ToolboxManager should be singleton", manager1, manager2)
    }

    @Test
    fun `test assistantOrchestrator singleton behavior`() {
        ServiceLocator.initialize(context)
        
        val orchestrator1 = ServiceLocator.assistantOrchestrator
        val orchestrator2 = ServiceLocator.assistantOrchestrator
        
        assertSame("AssistantOrchestrator should be singleton", orchestrator1, orchestrator2)
    }

    @Test
    fun `test reset clears all instances`() {
        ServiceLocator.initialize(context)
        
        // Get instances
        val storage1 = ServiceLocator.secureStorage
        val audio1 = ServiceLocator.audioManager
        val llm1 = ServiceLocator.llmProvider
        
        // Reset
        ServiceLocator.reset()
        
        // Get new instances
        val storage2 = ServiceLocator.secureStorage
        val audio2 = ServiceLocator.audioManager
        val llm2 = ServiceLocator.llmProvider
        
        // Should be different instances
        assertNotSame("SecureStorage should be new instance after reset", storage1, storage2)
        assertNotSame("AudioManager should be new instance after reset", audio1, audio2)
        assertNotSame("LLMProvider should be new instance after reset", llm1, llm2)
    }

    @Test
    fun `test toolboxManager has default tools registered`() {
        ServiceLocator.initialize(context)
        
        val toolboxManager = ServiceLocator.toolboxManager
        val tools = toolboxManager.getAllTools()
        
        assertTrue("ToolboxManager should have tools registered", tools.isNotEmpty())
        
        // Check for expected default tools
        val toolNames = tools.map { it.name }
        assertTrue("Should have SmartLightTool", toolNames.any { it.contains("light", ignoreCase = true) })
        assertTrue("Should have FlashlightTool", toolNames.any { it.contains("flashlight", ignoreCase = true) })
        assertTrue("Should have PhoneTool", toolNames.any { it.contains("phone", ignoreCase = true) })
    }

    @Test
    fun `test llmProvider is GeminiProvider`() {
        ServiceLocator.initialize(context)
        
        val llmProvider = ServiceLocator.llmProvider
        
        assertEquals("Gemini", llmProvider.getProviderName())
    }

    @Test
    fun `test assistantOrchestrator dependencies are wired correctly`() {
        ServiceLocator.initialize(context)
        
        val orchestrator = ServiceLocator.assistantOrchestrator
        
        // Verify that orchestrator doesn't crash when accessing its dependencies
        assertNotNull("AssistantOrchestrator should be created successfully", orchestrator)
        
        // Test that all dependencies are properly injected by accessing the state
        val initialState = orchestrator.state.value
        assertNotNull("AssistantOrchestrator should have valid initial state", initialState)
    }

    @Test
    fun `test dependency chain initialization`() {
        ServiceLocator.initialize(context)
        
        // Access orchestrator first (depends on all other services)
        val orchestrator = ServiceLocator.assistantOrchestrator
        
        // Verify all dependencies are available
        assertNotNull(ServiceLocator.wakeWordDetector)
        assertNotNull(ServiceLocator.voiceActivityDetector)
        assertNotNull(ServiceLocator.speechToTextManager)
        assertNotNull(ServiceLocator.llmProvider)
        assertNotNull(ServiceLocator.toolboxManager)
        assertNotNull(ServiceLocator.textToSpeechManager)
        
        // Verify orchestrator is properly initialized
        assertNotNull(orchestrator)
    }

    @Test
    fun `test multiple reset calls`() {
        ServiceLocator.initialize(context)
        
        val storage1 = ServiceLocator.secureStorage
        
        ServiceLocator.reset()
        ServiceLocator.reset() // Second reset should not crash
        
        val storage2 = ServiceLocator.secureStorage
        
        assertNotSame("Multiple resets should still create new instances", storage1, storage2)
    }

    @Test
    fun `test service creation order independence`() {
        ServiceLocator.initialize(context)
        
        // Access services in different orders to ensure no dependency issues
        val order1 = listOf(
            { ServiceLocator.textToSpeechManager },
            { ServiceLocator.llmProvider },
            { ServiceLocator.wakeWordDetector },
            { ServiceLocator.speechToTextManager }
        )
        
        ServiceLocator.reset()
        
        val order2 = listOf(
            { ServiceLocator.speechToTextManager },
            { ServiceLocator.wakeWordDetector },
            { ServiceLocator.llmProvider },
            { ServiceLocator.textToSpeechManager }
        )
        
        // Both orders should work without crashing
        order1.forEach { getter ->
            assertNotNull(getter())
        }
        
        ServiceLocator.reset()
        
        order2.forEach { getter ->
            assertNotNull(getter())
        }
    }

    @Test
    fun `test context dependency injection`() {
        ServiceLocator.initialize(context)
        
        // Services that require context should receive it properly
        val audioManager = ServiceLocator.audioManager
        val speechToText = ServiceLocator.speechToTextManager
        val textToSpeech = ServiceLocator.textToSpeechManager
        
        // These should not crash when accessed (indicating proper context injection)
        assertNotNull(audioManager)
        assertNotNull(speechToText)
        assertNotNull(textToSpeech)
    }

    @Test
    fun `test lazy initialization behavior`() {
        ServiceLocator.initialize(context)
        
        // Services should only be created when first accessed
        // This is tested implicitly by the singleton behavior tests
        // and by verifying that reset() clears instances
        
        ServiceLocator.reset()
        
        // After reset, new instances should be created on access
        val storage = ServiceLocator.secureStorage
        assertNotNull(storage)
        
        // Second access should return same instance
        val storage2 = ServiceLocator.secureStorage
        assertSame(storage, storage2)
    }
}
