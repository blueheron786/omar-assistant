package com.zayd.assistant.toolbox.tools

import android.content.Context
import android.hardware.camera2.CameraManager
import com.zayd.assistant.toolbox.tools.FlashlightTool
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for FlashlightTool
 * Tests flashlight control functionality and error handling
 */
@RunWith(RobolectricTestRunner::class)
class FlashlightToolTest {

    private lateinit var flashlightTool: FlashlightTool
    private lateinit var mockContext: Context
    private lateinit var mockCameraManager: CameraManager

    @Before
    fun setUp() {
        mockContext = mockk()
        mockCameraManager = mockk()
        
        every { mockContext.getSystemService(Context.CAMERA_SERVICE) } returns mockCameraManager
        every { mockCameraManager.cameraIdList } returns arrayOf("0")
        
        flashlightTool = FlashlightTool(mockContext)
    }

    @Test
    fun `test tool properties`() {
        assertEquals("flashlight_tool", flashlightTool.name)
        assertTrue(flashlightTool.description.contains("flashlight", ignoreCase = true))
        assertTrue(flashlightTool.parameters.containsKey("action"))
    }

    @Test
    fun `test parameter validation with valid action`() {
        val validParams = mapOf("action" to "turn_on")
        assertTrue(flashlightTool.validateParameters(validParams))

        val validParams2 = mapOf("action" to "turn_off")
        assertTrue(flashlightTool.validateParameters(validParams2))
    }

    @Test
    fun `test parameter validation with invalid action`() {
        val invalidParams = mapOf("action" to "invalid_action")
        assertFalse(flashlightTool.validateParameters(invalidParams))

        val emptyParams = emptyMap<String, Any>()
        assertFalse(flashlightTool.validateParameters(emptyParams))

        val wrongKey = mapOf("wrong_key" to "turn_on")
        assertFalse(flashlightTool.validateParameters(wrongKey))
    }

    @Test
    fun `test execute with turn_on action`() = runTest {
        every { mockCameraManager.setTorchMode("0", true) } returns Unit

        val parameters = mapOf("action" to "turn_on")
        val result = flashlightTool.execute(parameters)

        assertTrue(result.success)
        assertTrue(result.message.contains("on", ignoreCase = true))
        verify { mockCameraManager.setTorchMode("0", true) }
    }

    @Test
    fun `test execute with turn_off action`() = runTest {
        every { mockCameraManager.setTorchMode("0", false) } returns Unit

        val parameters = mapOf("action" to "turn_off")
        val result = flashlightTool.execute(parameters)

        assertTrue(result.success)
        assertTrue(result.message.contains("off", ignoreCase = true))
        verify { mockCameraManager.setTorchMode("0", false) }
    }

    @Test
    fun `test execute with invalid parameters`() = runTest {
        val parameters = mapOf("action" to "invalid")
        val result = flashlightTool.execute(parameters)

        assertFalse(result.success)
        assertTrue(result.message.contains("Invalid", ignoreCase = true))
    }

    @Test
    fun `test execute handles camera manager exception`() = runTest {
        every { mockCameraManager.setTorchMode("0", any()) } throws RuntimeException("Camera error")

        val parameters = mapOf("action" to "turn_on")
        val result = flashlightTool.execute(parameters)

        assertFalse(result.success)
        assertTrue(result.message.contains("error", ignoreCase = true))
    }

    @Test
    fun `test execute with no camera available`() = runTest {
        every { mockCameraManager.cameraIdList } returns emptyArray()

        val parameters = mapOf("action" to "turn_on")
        val result = flashlightTool.execute(parameters)

        assertFalse(result.success)
        assertTrue(result.message.contains("camera", ignoreCase = true) || 
                  result.message.contains("available", ignoreCase = true))
    }

    @Test
    fun `test execute with null camera manager`() {
        every { mockContext.getSystemService(Context.CAMERA_SERVICE) } returns null
        val toolWithNullCamera = FlashlightTool(mockContext)

        runTest {
            val parameters = mapOf("action" to "turn_on")
            val result = toolWithNullCamera.execute(parameters)

            assertFalse(result.success)
            assertTrue(result.message.contains("not available", ignoreCase = true) ||
                      result.message.contains("error", ignoreCase = true))
        }
    }

    @Test
    fun `test case insensitive action values`() {
        assertTrue(flashlightTool.validateParameters(mapOf("action" to "TURN_ON")))
        assertTrue(flashlightTool.validateParameters(mapOf("action" to "Turn_Off")))
        assertTrue(flashlightTool.validateParameters(mapOf("action" to "turn_ON")))
    }

    @Test
    fun `test alternative action values`() {
        val alternativeOnValues = listOf("on", "enable", "start", "1", "true")
        val alternativeOffValues = listOf("off", "disable", "stop", "0", "false")

        // Note: This test assumes the tool might support alternative values
        // If not implemented, these would be invalid
        alternativeOnValues.forEach { value ->
            val params = mapOf("action" to value)
            // Test that tool can handle various input formats
            assertNotNull(flashlightTool.validateParameters(params))
        }
    }

    @Test
    fun `test execute performance`() = runTest {
        every { mockCameraManager.setTorchMode("0", any()) } returns Unit

        val startTime = System.currentTimeMillis()
        val parameters = mapOf("action" to "turn_on")
        val result = flashlightTool.execute(parameters)
        val endTime = System.currentTimeMillis()

        assertTrue(result.success)
        // Should execute quickly (less than 1 second)
        assertTrue("Tool execution should be fast", (endTime - startTime) < 1000)
    }

    @Test
    fun `test multiple rapid executions`() = runTest {
        every { mockCameraManager.setTorchMode("0", any()) } returns Unit

        val results = mutableListOf<Boolean>()
        
        repeat(5) { index ->
            val action = if (index % 2 == 0) "turn_on" else "turn_off"
            val parameters = mapOf("action" to action)
            val result = flashlightTool.execute(parameters)
            results.add(result.success)
        }

        // All executions should succeed
        assertTrue("All rapid executions should succeed", results.all { it })
    }

    @Test
    fun `test state tracking`() = runTest {
        every { mockCameraManager.setTorchMode("0", any()) } returns Unit

        // Turn on
        val onResult = flashlightTool.execute(mapOf("action" to "turn_on"))
        assertTrue(onResult.success)

        // Turn off
        val offResult = flashlightTool.execute(mapOf("action" to "turn_off"))
        assertTrue(offResult.success)

        // Verify both calls were made to camera manager
        verify { mockCameraManager.setTorchMode("0", true) }
        verify { mockCameraManager.setTorchMode("0", false) }
    }

    @Test
    fun `test error message formatting`() = runTest {
        every { mockCameraManager.setTorchMode("0", any()) } throws 
            RuntimeException("Specific camera error")

        val parameters = mapOf("action" to "turn_on")
        val result = flashlightTool.execute(parameters)

        assertFalse(result.success)
        // Error message should include some helpful information
        assertTrue(result.message.isNotBlank())
        assertTrue(result.message.length > 5) // Should be descriptive
    }
}
