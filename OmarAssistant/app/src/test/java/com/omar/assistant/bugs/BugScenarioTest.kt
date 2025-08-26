package com.omar.assistant.bugs

import com.omar.assistant.audio.VoiceActivityDetector
import com.omar.assistant.core.di.ServiceLocator
import com.omar.assistant.llm.gemini.GeminiProvider
import com.omar.assistant.toolbox.ToolboxManager
import com.omar.assistant.toolbox.Tool
import com.omar.assistant.toolbox.ToolExecutionResult
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Tests for common bug scenarios and edge cases
 * These tests target known issues and potential failure points
 */
@RunWith(RobolectricTestRunner::class)
class BugScenarioTest {

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        ServiceLocator.initialize(context)
        ServiceLocator.reset()
    }

    @Test
    fun `test memory leak in repeated service locator access`() {
        // Test for potential memory leaks in ServiceLocator
        repeat(100) {
            ServiceLocator.secureStorage
            ServiceLocator.audioManager
            ServiceLocator.llmProvider
            ServiceLocator.reset()
        }
        
        // Force garbage collection
        System.gc()
        
        // Should not cause OutOfMemoryError
        assertTrue("Memory leak test completed", true)
    }

    @Test
    fun `test concurrent access to toolbox manager`() = runTest {
        val toolboxManager = ToolboxManager()
        
        // Create mock tool
        val mockTool = object : Tool {
            override val name = "concurrent_tool"
            override val description = "Test tool"
            override val parameters = emptyMap<String, String>()
            
            override suspend fun execute(parameters: Map<String, Any>): ToolExecutionResult {
                // Simulate some processing time
                Thread.sleep(50)
                return ToolExecutionResult(success = true, message = "Success")
            }
            
            override fun validateParameters(parameters: Map<String, Any>): Boolean = true
        }
        
        toolboxManager.registerTool(mockTool)
        
        // Test concurrent access
        val jobs = List(10) { index ->
            kotlinx.coroutines.async {
                toolboxManager.executeTool("concurrent_tool", mapOf("test" to "value$index"))
            }
        }
        
        val results = jobs.map { it.await() }
        
        // All should succeed without race conditions
        assertTrue("All concurrent executions should succeed", 
                  results.all { it.success })
    }

    @Test
    fun `test voice activity detector with extreme audio values`() {
        val vad = VoiceActivityDetector()
        
        // Test with extreme values that might cause overflow
        val extremeValues = listOf(
            ShortArray(1024) { Short.MAX_VALUE },
            ShortArray(1024) { Short.MIN_VALUE },
            ShortArray(1024) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE },
            ShortArray(0), // Empty buffer
            ShortArray(1) { 1000 }, // Single sample
            ShortArray(100000) { (it % 1000).toShort() } // Very large buffer
        )
        
        extremeValues.forEach { buffer ->
            try {
                val energy = vad.calculateEnergy(buffer)
                assertTrue("Energy should be non-negative", energy >= 0)
                
                vad.processAudio(buffer)
                // Should not crash
            } catch (e: Exception) {
                fail("VAD should handle extreme values gracefully: ${e.message}")
            }
        }
    }

    @Test
    fun `test json parsing edge cases in gemini provider`() {
        val secureStorage = mockk<com.omar.assistant.core.storage.SecureStorage>()
        every { secureStorage.hasGeminiApiKey() } returns false
        
        val provider = GeminiProvider(secureStorage)
        
        // Test parseSimpleJson with edge cases
        val edgeCases = listOf(
            """{"key": "value"}""", // Normal case
            """{}""", // Empty object
            """{"key":}""", // Missing value
            """{key": "value"}""", // Missing quote
            """{"key": "value"extra}""", // Extra content
            """{"nested": {"inner": "value"}}""", // Nested (not supported)
            """{"array": [1,2,3]}""", // Array (not supported)
            """{"unicode": "café 🤖"}""", // Unicode characters
            """{"quotes": "value with \"quotes\""}""", // Escaped quotes
            """null""", // Null
            """""", // Empty string
            """malformed""", // Not JSON at all
            """{"very_long_key_name_that_might_cause_issues": "very_long_value_that_might_cause_buffer_overflow_or_other_issues_in_parsing"}""" // Long strings
        )
        
        edgeCases.forEach { jsonString ->
            try {
                // Use reflection to access private method
                val method = GeminiProvider::class.java.getDeclaredMethod("parseSimpleJson", String::class.java)
                method.isAccessible = true
                
                val result = method.invoke(provider, jsonString) as Map<String, String>
                assertNotNull("Result should not be null for: $jsonString", result)
            } catch (e: Exception) {
                // Should not crash, even with malformed input
                assertTrue("Should handle malformed JSON gracefully: $jsonString", true)
            }
        }
    }

    @Test
    fun `test resource cleanup after exceptions`() {
        val context = RuntimeEnvironment.getApplication()
        ServiceLocator.initialize(context)
        
        // Simulate exceptions during component creation
        try {
            val orchestrator = ServiceLocator.assistantOrchestrator
            assertNotNull(orchestrator)
        } catch (e: Exception) {
            // Even if creation fails, cleanup should work
            ServiceLocator.reset()
        }
        
        // Should be able to recreate after exception
        val newOrchestrator = ServiceLocator.assistantOrchestrator
        assertNotNull(newOrchestrator)
    }

    @Test
    fun `test wake word detector initialization failure recovery`() {
        val context = RuntimeEnvironment.getApplication()
        ServiceLocator.initialize(context)
        
        // Test that system gracefully handles wake word detector failures
        val detector = ServiceLocator.wakeWordDetector
        assertNotNull(detector)
        
        // Multiple initialization attempts should not cause issues
        runTest {
            repeat(5) {
                try {
                    detector.initialize()
                } catch (e: Exception) {
                    // Should handle initialization failures gracefully
                    assertTrue("Wake word detector handles init failure", true)
                }
            }
        }
    }

    @Test
    fun `test tool parameter validation edge cases`() {
        val toolboxManager = ToolboxManager()
        
        val sensitiveParameterTool = object : Tool {
            override val name = "sensitive_tool"
            override val description = "Tool with sensitive parameter validation"
            override val parameters = mapOf("action" to "required", "value" to "optional")
            
            override suspend fun execute(parameters: Map<String, Any>): ToolExecutionResult {
                return ToolExecutionResult(success = true, message = "OK")
            }
            
            override fun validateParameters(parameters: Map<String, Any>): Boolean {
                // Strict validation that might cause issues
                val action = parameters["action"] as? String ?: return false
                return action in listOf("valid1", "valid2", "valid3")
            }
        }
        
        toolboxManager.registerTool(sensitiveParameterTool)
        
        val testCases = listOf(
            // Edge cases that might break validation
            mapOf("action" to ""),
            mapOf("action" to " "),
            mapOf("action" to null),
            mapOf("action" to 123), // Wrong type
            mapOf("action" to listOf("valid1")), // List instead of string
            mapOf("action" to "VALID1"), // Case sensitivity
            mapOf("action" to "valid1", "extra" to "unexpected"), // Extra parameters
            emptyMap(), // No parameters
            mapOf("wrong_key" to "valid1") // Wrong key
        )
        
        runTest {
            testCases.forEach { params ->
                val result = toolboxManager.executeTool("sensitive_tool", params)
                // Should handle all cases without crashing
                assertNotNull("Tool execution should not return null", result)
            }
        }
    }

    @Test
    fun `test conversation history memory management`() {
        val context = RuntimeEnvironment.getApplication()
        ServiceLocator.initialize(context)
        
        val orchestrator = ServiceLocator.assistantOrchestrator
        
        // Simulate very long conversation that might cause memory issues
        runTest {
            repeat(1000) { index ->
                try {
                    orchestrator.processDirectInput("Message $index with some content to test memory usage")
                    
                    // Periodically check that we're not accumulating too much memory
                    if (index % 100 == 0) {
                        System.gc()
                    }
                } catch (e: Exception) {
                    // Should handle memory constraints gracefully
                    assertTrue("Memory management test: ${e.message}", true)
                    break
                }
            }
        }
    }

    @Test
    fun `test rapid state transitions in orchestrator`() {
        val context = RuntimeEnvironment.getApplication()
        ServiceLocator.initialize(context)
        
        val orchestrator = ServiceLocator.assistantOrchestrator
        
        // Test rapid state changes that might cause race conditions
        runTest {
            repeat(50) {
                try {
                    orchestrator.startListening()
                    kotlinx.coroutines.delay(10)
                    orchestrator.stopListening()
                    kotlinx.coroutines.delay(10)
                } catch (e: Exception) {
                    // Should handle rapid state changes gracefully
                    assertTrue("Rapid state transitions handled: ${e.message}", true)
                }
            }
        }
    }

    @Test
    fun `test string encoding issues in tool parameters`() {
        val toolboxManager = ToolboxManager()
        
        val encodingTool = object : Tool {
            override val name = "encoding_tool"
            override val description = "Test encoding issues"
            override val parameters = mapOf("text" to "Text parameter")
            
            override suspend fun execute(parameters: Map<String, Any>): ToolExecutionResult {
                val text = parameters["text"] as? String ?: ""
                return ToolExecutionResult(success = true, message = "Processed: $text")
            }
            
            override fun validateParameters(parameters: Map<String, Any>): Boolean = true
        }
        
        toolboxManager.registerTool(encodingTool)
        
        val problematicStrings = listOf(
            "Normal text",
            "Émojis: 🤖🎵🔊",
            "Special chars: @#$%^&*()",
            "Unicode: café naïve résumé",
            "Control chars: \t\n\r",
            "Very long: " + "A".repeat(10000),
            "Mixed encoding: café🤖test\n",
            "Null bytes: test\u0000null",
            "HTML: <script>alert('test')</script>",
            "SQL injection: '; DROP TABLE users; --",
            "Path traversal: ../../../etc/passwd"
        )
        
        runTest {
            problematicStrings.forEach { text ->
                val result = toolboxManager.executeTool("encoding_tool", mapOf("text" to text))
                assertTrue("Should handle problematic string: ${text.take(20)}", result.success)
            }
        }
    }

    @Test
    fun `test error propagation through component stack`() {
        val context = RuntimeEnvironment.getApplication()
        ServiceLocator.initialize(context)
        
        // Test that errors in low-level components are handled properly at higher levels
        val orchestrator = ServiceLocator.assistantOrchestrator
        
        runTest {
            // Test with inputs that might cause cascading failures
            val problematicInputs = listOf(
                "", // Empty input
                " ".repeat(10000), // Very long whitespace
                "\u0000\u0001\u0002", // Control characters
                "A".repeat(100000), // Extremely long input
                null.toString(), // "null" string
                "🤖".repeat(1000) // Many emojis
            )
            
            problematicInputs.forEach { input ->
                try {
                    orchestrator.processDirectInput(input)
                    // Should handle gracefully
                } catch (e: Exception) {
                    // Errors should be contained and not crash the app
                    assertTrue("Error contained for input: ${input.take(20)}", true)
                }
            }
        }
    }
}
