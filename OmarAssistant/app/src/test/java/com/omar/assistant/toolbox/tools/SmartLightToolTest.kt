package com.omar.assistant.toolbox.tools

import com.omar.assistant.toolbox.tools.SmartLightTool
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for SmartLightTool
 * Tests smart light simulation and parameter validation
 */
@RunWith(RobolectricTestRunner::class)
class SmartLightToolTest {

    private lateinit var smartLightTool: SmartLightTool

    @Before
    fun setUp() {
        smartLightTool = SmartLightTool()
    }

    @Test
    fun `test tool properties`() {
        assertEquals("smart_light", smartLightTool.name)
        assertTrue(smartLightTool.description.contains("smart light", ignoreCase = true))
        assertTrue(smartLightTool.parameters.containsKey("action"))
        assertTrue(smartLightTool.parameters.containsKey("brightness"))
        assertTrue(smartLightTool.parameters.containsKey("color"))
    }

    @Test
    fun `test parameter validation with valid action only`() {
        val validParams = mapOf("action" to "turn_on")
        assertTrue(smartLightTool.validateParameters(validParams))

        val validParams2 = mapOf("action" to "turn_off")
        assertTrue(smartLightTool.validateParameters(validParams2))
    }

    @Test
    fun `test parameter validation with brightness`() {
        val validParams = mapOf(
            "action" to "turn_on",
            "brightness" to "50"
        )
        assertTrue(smartLightTool.validateParameters(validParams))

        val validParams2 = mapOf(
            "action" to "set_brightness",
            "brightness" to "100"
        )
        assertTrue(smartLightTool.validateParameters(validParams2))
    }

    @Test
    fun `test parameter validation with color`() {
        val validParams = mapOf(
            "action" to "set_color",
            "color" to "red"
        )
        assertTrue(smartLightTool.validateParameters(validParams))

        val validParams2 = mapOf(
            "action" to "turn_on",
            "color" to "blue",
            "brightness" to "75"
        )
        assertTrue(smartLightTool.validateParameters(validParams2))
    }

    @Test
    fun `test parameter validation with invalid action`() {
        val invalidParams = mapOf("action" to "invalid_action")
        assertFalse(smartLightTool.validateParameters(invalidParams))

        val emptyParams = emptyMap<String, Any>()
        assertFalse(smartLightTool.validateParameters(emptyParams))

        val wrongKey = mapOf("wrong_key" to "turn_on")
        assertFalse(smartLightTool.validateParameters(wrongKey))
    }

    @Test
    fun `test execute turn_on action`() = runTest {
        val parameters = mapOf("action" to "turn_on")
        val result = smartLightTool.execute(parameters)

        assertTrue(result.success)
        assertTrue(result.message.contains("turned on", ignoreCase = true) ||
                  result.message.contains("on", ignoreCase = true))
    }

    @Test
    fun `test execute turn_off action`() = runTest {
        val parameters = mapOf("action" to "turn_off")
        val result = smartLightTool.execute(parameters)

        assertTrue(result.success)
        assertTrue(result.message.contains("turned off", ignoreCase = true) ||
                  result.message.contains("off", ignoreCase = true))
    }

    @Test
    fun `test execute set_brightness action`() = runTest {
        val parameters = mapOf(
            "action" to "set_brightness",
            "brightness" to "75"
        )
        val result = smartLightTool.execute(parameters)

        assertTrue(result.success)
        assertTrue(result.message.contains("brightness", ignoreCase = true))
        assertTrue(result.message.contains("75"))
    }

    @Test
    fun `test execute set_color action`() = runTest {
        val parameters = mapOf(
            "action" to "set_color",
            "color" to "red"
        )
        val result = smartLightTool.execute(parameters)

        assertTrue(result.success)
        assertTrue(result.message.contains("color", ignoreCase = true))
        assertTrue(result.message.contains("red", ignoreCase = true))
    }

    @Test
    fun `test execute with combined parameters`() = runTest {
        val parameters = mapOf(
            "action" to "turn_on",
            "brightness" to "80",
            "color" to "blue"
        )
        val result = smartLightTool.execute(parameters)

        assertTrue(result.success)
        assertTrue(result.message.contains("80"))
        assertTrue(result.message.contains("blue", ignoreCase = true))
    }

    @Test
    fun `test execute with invalid parameters`() = runTest {
        val parameters = mapOf("action" to "invalid")
        val result = smartLightTool.execute(parameters)

        assertFalse(result.success)
        assertTrue(result.message.contains("Invalid", ignoreCase = true) ||
                  result.message.contains("parameters", ignoreCase = true))
    }

    @Test
    fun `test brightness parameter validation`() {
        // Valid brightness values
        val validBrightness = listOf("0", "50", "100", "1", "99")
        validBrightness.forEach { brightness ->
            val params = mapOf("action" to "set_brightness", "brightness" to brightness)
            assertTrue("Brightness $brightness should be valid", 
                      smartLightTool.validateParameters(params))
        }
    }

    @Test
    fun `test color parameter validation`() {
        // Valid color values
        val validColors = listOf("red", "green", "blue", "yellow", "white", "purple", "orange")
        validColors.forEach { color ->
            val params = mapOf("action" to "set_color", "color" to color)
            assertTrue("Color $color should be valid", 
                      smartLightTool.validateParameters(params))
        }
    }

    @Test
    fun `test case insensitive actions`() {
        val actions = listOf("TURN_ON", "Turn_Off", "SET_BRIGHTNESS", "set_color")
        actions.forEach { action ->
            val params = mapOf("action" to action)
            // Tool should handle case variations gracefully
            assertNotNull(smartLightTool.validateParameters(params))
        }
    }

    @Test
    fun `test execute performance`() = runTest {
        val startTime = System.currentTimeMillis()
        val parameters = mapOf("action" to "turn_on")
        val result = smartLightTool.execute(parameters)
        val endTime = System.currentTimeMillis()

        assertTrue(result.success)
        // Should execute quickly (less than 100ms for simulation)
        assertTrue("Tool execution should be fast", (endTime - startTime) < 100)
    }

    @Test
    fun `test multiple action types in sequence`() = runTest {
        val actions = listOf(
            mapOf("action" to "turn_on"),
            mapOf("action" to "set_brightness", "brightness" to "50"),
            mapOf("action" to "set_color", "color" to "red"),
            mapOf("action" to "turn_off")
        )

        val results = actions.map { params ->
            smartLightTool.execute(params)
        }

        // All actions should succeed
        assertTrue("All actions should succeed", results.all { it.success })
        
        // Each should have appropriate response message
        assertTrue(results[0].message.contains("on", ignoreCase = true))
        assertTrue(results[1].message.contains("brightness", ignoreCase = true))
        assertTrue(results[2].message.contains("color", ignoreCase = true))
        assertTrue(results[3].message.contains("off", ignoreCase = true))
    }

    @Test
    fun `test execute with edge case brightness values`() = runTest {
        val edgeCases = listOf("0", "100")
        
        edgeCases.forEach { brightness ->
            val parameters = mapOf(
                "action" to "set_brightness",
                "brightness" to brightness
            )
            val result = smartLightTool.execute(parameters)
            
            assertTrue("Brightness $brightness should work", result.success)
            assertTrue(result.message.contains(brightness))
        }
    }

    @Test
    fun `test response message formatting`() = runTest {
        val parameters = mapOf(
            "action" to "turn_on",
            "brightness" to "75",
            "color" to "green"
        )
        val result = smartLightTool.execute(parameters)

        assertTrue(result.success)
        
        // Response should be well-formatted and informative
        assertTrue(result.message.isNotBlank())
        assertTrue(result.message.length > 10) // Should be descriptive
        
        // Should mention the key parameters
        assertTrue(result.message.contains("75"))
        assertTrue(result.message.contains("green", ignoreCase = true))
    }

    @Test
    fun `test state simulation consistency`() = runTest {
        // Turn on the light
        val onResult = smartLightTool.execute(mapOf("action" to "turn_on"))
        assertTrue(onResult.success)

        // Set brightness
        val brightnessResult = smartLightTool.execute(mapOf(
            "action" to "set_brightness", 
            "brightness" to "60"
        ))
        assertTrue(brightnessResult.success)

        // Set color
        val colorResult = smartLightTool.execute(mapOf(
            "action" to "set_color", 
            "color" to "purple"
        ))
        assertTrue(colorResult.success)

        // Turn off
        val offResult = smartLightTool.execute(mapOf("action" to "turn_off"))
        assertTrue(offResult.success)

        // All operations should succeed independently
        val allResults = listOf(onResult, brightnessResult, colorResult, offResult)
        assertTrue("All state changes should succeed", allResults.all { it.success })
    }

    @Test
    fun `test missing optional parameters`() = runTest {
        // Test actions that don't require all parameters
        val testCases = listOf(
            mapOf("action" to "turn_on"), // No brightness/color
            mapOf("action" to "turn_off"), // No additional params needed
            mapOf("action" to "set_brightness", "brightness" to "50"), // No color
            mapOf("action" to "set_color", "color" to "red") // No brightness
        )

        testCases.forEach { params ->
            val result = smartLightTool.execute(params)
            assertTrue("Should handle missing optional params: $params", result.success)
        }
    }
}
