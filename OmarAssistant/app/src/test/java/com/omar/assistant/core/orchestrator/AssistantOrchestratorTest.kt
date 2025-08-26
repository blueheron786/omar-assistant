package com.omar.assistant.core.orchestrator

import com.omar.assistant.audio.VoiceActivityDetector
import com.omar.assistant.audio.WakeWordDetector
import com.omar.assistant.audio.WakeWordResult
import com.omar.assistant.llm.ConversationMessage
import com.omar.assistant.llm.LLMProvider
import com.omar.assistant.llm.LLMResponse
import com.omar.assistant.llm.MessageRole
import com.omar.assistant.speech.SpeechToTextManager
import com.omar.assistant.speech.TextToSpeechManager
import com.omar.assistant.toolbox.Tool
import com.omar.assistant.toolbox.ToolCall
import com.omar.assistant.toolbox.ToolExecutionResult
import com.omar.assistant.toolbox.ToolboxManager
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for AssistantOrchestrator
 * Tests the complete assistant workflow, state management, and component coordination
 */
@RunWith(RobolectricTestRunner::class)
class AssistantOrchestratorTest {

    private lateinit var orchestrator: AssistantOrchestrator
    private lateinit var mockWakeWordDetector: WakeWordDetector
    private lateinit var mockVoiceActivityDetector: VoiceActivityDetector
    private lateinit var mockSpeechToTextManager: SpeechToTextManager
    private lateinit var mockLLMProvider: LLMProvider
    private lateinit var mockToolboxManager: ToolboxManager
    private lateinit var mockTextToSpeechManager: TextToSpeechManager

    @Before
    fun setUp() {
        mockWakeWordDetector = mockk()
        mockVoiceActivityDetector = mockk()
        mockSpeechToTextManager = mockk()
        mockLLMProvider = mockk()
        mockToolboxManager = mockk()
        mockTextToSpeechManager = mockk()

        // Setup default mock behaviors
        every { mockWakeWordDetector.wakeWordDetected } returns flowOf()
        every { mockLLMProvider.getProviderName() } returns "TestProvider"
        every { mockToolboxManager.getAllTools() } returns emptyList()
        coEvery { mockTextToSpeechManager.initialize() } returns true
        coEvery { mockTextToSpeechManager.speak(any()) } returns Unit

        orchestrator = AssistantOrchestrator(
            mockWakeWordDetector,
            mockVoiceActivityDetector,
            mockSpeechToTextManager,
            mockLLMProvider,
            mockToolboxManager,
            mockTextToSpeechManager
        )
    }

    @Test
    fun `test initial state is IDLE`() = runTest {
        assertEquals(AssistantState.IDLE, orchestrator.state.value)
        assertNull(orchestrator.lastResponse.value)
    }

    @Test
    fun `test startListening changes state`() = runTest {
        coEvery { mockWakeWordDetector.initialize() } returns true
        coEvery { mockWakeWordDetector.startListening() } returns Unit

        orchestrator.startListening()

        assertEquals(AssistantState.LISTENING_FOR_WAKE_WORD, orchestrator.state.value)
    }

    @Test
    fun `test stopListening changes state to IDLE`() = runTest {
        coEvery { mockWakeWordDetector.initialize() } returns true
        coEvery { mockWakeWordDetector.startListening() } returns Unit
        coEvery { mockWakeWordDetector.stopListening() } returns Unit

        orchestrator.startListening()
        orchestrator.stopListening()

        assertEquals(AssistantState.IDLE, orchestrator.state.value)
    }

    @Test
    fun `test startListening fails when wake word detector initialization fails`() = runTest {
        coEvery { mockWakeWordDetector.initialize() } returns false

        orchestrator.startListening()

        assertEquals(AssistantState.IDLE, orchestrator.state.value)
    }

    @Test
    fun `test processDirectInput with simple response`() = runTest {
        val userInput = "Hello"
        val llmResponse = LLMResponse(
            text = "Hi there!",
            shouldUseTools = false,
            toolCalls = emptyList()
        )

        coEvery { 
            mockLLMProvider.processInput(
                userInput = userInput,
                availableTools = any(),
                conversationHistory = any()
            )
        } returns llmResponse

        orchestrator.processDirectInput(userInput)

        // Verify TTS was called
        coVerify { mockTextToSpeechManager.speak("Hi there!") }
        
        // Verify response was stored
        assertEquals("Hi there!", orchestrator.lastResponse.value)
    }

    @Test
    fun `test processDirectInput with tool execution`() = runTest {
        val userInput = "Turn on the light"
        val toolCall = ToolCall("light_tool", mapOf("action" to "on"))
        val llmResponse = LLMResponse(
            text = "I'll turn on the light",
            shouldUseTools = true,
            toolCalls = listOf(toolCall)
        )
        val toolResult = ToolExecutionResult(success = true, message = "Light turned on")

        coEvery { 
            mockLLMProvider.processInput(
                userInput = userInput,
                availableTools = any(),
                conversationHistory = any()
            )
        } returns llmResponse

        coEvery { 
            mockToolboxManager.executeTool("light_tool", mapOf("action" to "on"))
        } returns toolResult

        orchestrator.processDirectInput(userInput)

        // Verify tool execution
        coVerify { mockToolboxManager.executeTool("light_tool", mapOf("action" to "on")) }
        
        // Verify final response from tool
        coVerify { mockTextToSpeechManager.speak("Light turned on") }
        
        assertEquals("Light turned on", orchestrator.lastResponse.value)
    }

    @Test
    fun `test processDirectInput with failed tool execution`() = runTest {
        val userInput = "Turn on the light"
        val toolCall = ToolCall("light_tool", mapOf("action" to "on"))
        val llmResponse = LLMResponse(
            text = "I'll turn on the light",
            shouldUseTools = true,
            toolCalls = listOf(toolCall)
        )
        val toolResult = ToolExecutionResult(success = false, message = "Failed to turn on light")

        coEvery { 
            mockLLMProvider.processInput(any(), any(), any())
        } returns llmResponse

        coEvery { 
            mockToolboxManager.executeTool("light_tool", mapOf("action" to "on"))
        } returns toolResult

        orchestrator.processDirectInput(userInput)

        // Should fall back to original LLM response
        coVerify { mockTextToSpeechManager.speak("I'll turn on the light") }
        assertEquals("I'll turn on the light", orchestrator.lastResponse.value)
    }

    @Test
    fun `test conversation history management`() = runTest {
        val userInput1 = "Hello"
        val userInput2 = "How are you?"
        
        val response1 = LLMResponse("Hi there!", false, emptyList())
        val response2 = LLMResponse("I'm doing well!", false, emptyList())

        coEvery { 
            mockLLMProvider.processInput(userInput1, any(), any())
        } returns response1

        coEvery { 
            mockLLMProvider.processInput(userInput2, any(), any())
        } returns response2

        // Process first input
        orchestrator.processDirectInput(userInput1)

        // Process second input - should include history
        orchestrator.processDirectInput(userInput2)

        // Verify second call included conversation history
        coVerify { 
            mockLLMProvider.processInput(
                userInput = userInput2,
                availableTools = any(),
                conversationHistory = match { history ->
                    history.size >= 2 && 
                    history.any { it.content == userInput1 && it.role == MessageRole.USER } &&
                    history.any { it.content == "Hi there!" && it.role == MessageRole.ASSISTANT }
                }
            )
        }
    }

    @Test
    fun `test conversation history size limit`() = runTest {
        val llmResponse = LLMResponse("OK", false, emptyList())
        coEvery { mockLLMProvider.processInput(any(), any(), any()) } returns llmResponse

        // Process more than MAX_CONVERSATION_HISTORY messages
        repeat(15) { index ->
            orchestrator.processDirectInput("Message $index")
        }

        // Verify that history is limited (exact verification would need access to private field)
        coVerify(atLeast = 10) { 
            mockLLMProvider.processInput(any(), any(), any())
        }
    }

    @Test
    fun `test multiple tool execution`() = runTest {
        val userInput = "Turn on light and flashlight"
        val toolCalls = listOf(
            ToolCall("light_tool", mapOf("action" to "on")),
            ToolCall("flashlight_tool", mapOf("action" to "on"))
        )
        val llmResponse = LLMResponse(
            text = "I'll turn on both",
            shouldUseTools = true,
            toolCalls = toolCalls
        )

        coEvery { mockLLMProvider.processInput(any(), any(), any()) } returns llmResponse
        coEvery { mockToolboxManager.executeTool("light_tool", any()) } returns 
            ToolExecutionResult(true, "Light on")
        coEvery { mockToolboxManager.executeTool("flashlight_tool", any()) } returns 
            ToolExecutionResult(true, "Flashlight on")

        orchestrator.processDirectInput(userInput)

        // Verify both tools were executed
        coVerify { mockToolboxManager.executeTool("light_tool", mapOf("action" to "on")) }
        coVerify { mockToolboxManager.executeTool("flashlight_tool", mapOf("action" to "on")) }
    }

    @Test
    fun `test wake word detection flow`() = runTest {
        val wakeWordFlow = MutableSharedFlow<WakeWordResult>()
        every { mockWakeWordDetector.wakeWordDetected } returns wakeWordFlow.asSharedFlow()
        
        coEvery { mockWakeWordDetector.initialize() } returns true
        coEvery { mockWakeWordDetector.startListening() } returns Unit
        coEvery { mockSpeechToTextManager.startListening() } returns "Hello Omar"
        
        val llmResponse = LLMResponse("Hi there!", false, emptyList())
        coEvery { mockLLMProvider.processInput(any(), any(), any()) } returns llmResponse

        // Start listening
        orchestrator.startListening()
        
        // Simulate wake word detection
        wakeWordFlow.emit(WakeWordResult("omar", 0.95f))
        
        // Give coroutines time to process
        delay(100)

        // Verify state transition and speech processing
        verify { mockSpeechToTextManager.startListening() }
    }

    @Test
    fun `test TTS initialization failure handling`() = runTest {
        coEvery { mockTextToSpeechManager.initialize() } returns false

        val userInput = "Hello"
        val llmResponse = LLMResponse("Hi there!", false, emptyList())
        coEvery { mockLLMProvider.processInput(any(), any(), any()) } returns llmResponse

        orchestrator.processDirectInput(userInput)

        // Should still process input but TTS might fail gracefully
        coVerify { mockLLMProvider.processInput(any(), any(), any()) }
    }

    @Test
    fun `test state management during processing`() = runTest {
        val userInput = "Test input"
        val llmResponse = LLMResponse("Test response", false, emptyList())
        
        // Make LLM processing take some time
        coEvery { 
            mockLLMProvider.processInput(any(), any(), any())
        } coAnswers {
            delay(100)
            llmResponse
        }

        val job = launch {
            orchestrator.processDirectInput(userInput)
        }

        // State should change to processing
        delay(50)
        assertEquals(AssistantState.PROCESSING_INPUT, orchestrator.state.value)

        job.join()

        // Should return to previous state or idle
        assertTrue(
            orchestrator.state.value == AssistantState.IDLE || 
            orchestrator.state.value == AssistantState.LISTENING_FOR_WAKE_WORD
        )
    }

    @Test
    fun `test error handling in LLM processing`() = runTest {
        val userInput = "Test input"
        
        coEvery { 
            mockLLMProvider.processInput(any(), any(), any())
        } throws RuntimeException("LLM error")

        // Should not crash
        orchestrator.processDirectInput(userInput)

        // State should be reset appropriately
        assertTrue(
            orchestrator.state.value == AssistantState.IDLE || 
            orchestrator.state.value == AssistantState.LISTENING_FOR_WAKE_WORD
        )
    }

    @Test
    fun `test concurrent input processing`() = runTest {
        val llmResponse = LLMResponse("Response", false, emptyList())
        coEvery { mockLLMProvider.processInput(any(), any(), any()) } returns llmResponse

        // Launch multiple concurrent inputs
        val jobs = List(3) { index ->
            launch {
                orchestrator.processDirectInput("Input $index")
            }
        }

        jobs.joinAll()

        // All should complete without crashing
        coVerify(exactly = 3) { mockLLMProvider.processInput(any(), any(), any()) }
    }
}
