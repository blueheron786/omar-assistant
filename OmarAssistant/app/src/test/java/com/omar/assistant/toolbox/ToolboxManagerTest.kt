package com.omar.assistant.toolbox

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ToolboxManager
 * Tests tool registration, execution, validation, and error handling
 */
@RunWith(RobolectricTestRunner::class)
class ToolboxManagerTest {

    private lateinit var toolboxManager: ToolboxManager
    private lateinit var mockTool: Tool
    private lateinit var mockTool2: Tool

    @Before
    fun setUp() {
        toolboxManager = ToolboxManager()
        
        // Create mock tools for testing
        mockTool = object : Tool {
            override val name = "test_tool"
            override val description = "A test tool"
            override val parameters = mapOf("param1" to "Test parameter")
            
            override suspend fun execute(parameters: Map<String, Any>): ToolExecutionResult {
                return if (validateParameters(parameters)) {
                    ToolExecutionResult(success = true, message = "Tool executed successfully")
                } else {
                    ToolExecutionResult(success = false, message = "Invalid parameters")
                }
            }
            
            override fun validateParameters(parameters: Map<String, Any>): Boolean {
                return parameters.containsKey("param1")
            }
        }
        
        mockTool2 = object : Tool {
            override val name = "test_tool_2"
            override val description = "Another test tool"
            override val parameters = mapOf("param2" to "Second parameter")
            
            override suspend fun execute(parameters: Map<String, Any>): ToolExecutionResult {
                throw RuntimeException("Test exception")
            }
            
            override fun validateParameters(parameters: Map<String, Any>): Boolean {
                return true
            }
        }
    }

    @Test
    fun `test tool registration`() {
        // Test registering a tool
        toolboxManager.registerTool(mockTool)
        
        // Verify tool is registered
        val allTools = toolboxManager.getAllTools()
        assertEquals(1, allTools.size)
        assertEquals("test_tool", allTools[0].name)
        
        // Test getting specific tool
        val retrievedTool = toolboxManager.getTool("test_tool")
        assertNotNull(retrievedTool)
        assertEquals("test_tool", retrievedTool?.name)
    }

    @Test
    fun `test multiple tool registration`() {
        toolboxManager.registerTool(mockTool)
        toolboxManager.registerTool(mockTool2)
        
        assertEquals(2, toolboxManager.getAllTools().size)
        assertEquals(2, toolboxManager.getToolCount())
    }

    @Test
    fun `test tool unregistration`() {
        toolboxManager.registerTool(mockTool)
        toolboxManager.registerTool(mockTool2)
        
        // Unregister one tool
        toolboxManager.unregisterTool("test_tool")
        
        assertEquals(1, toolboxManager.getAllTools().size)
        assertNull(toolboxManager.getTool("test_tool"))
        assertNotNull(toolboxManager.getTool("test_tool_2"))
    }

    @Test
    fun `test successful tool execution`() = runTest {
        toolboxManager.registerTool(mockTool)
        
        val parameters = mapOf("param1" to "test_value")
        val result = toolboxManager.executeTool("test_tool", parameters)
        
        assertTrue(result.success)
        assertEquals("Tool executed successfully", result.message)
    }

    @Test
    fun `test tool execution with invalid parameters`() = runTest {
        toolboxManager.registerTool(mockTool)
        
        val parameters = mapOf("wrong_param" to "test_value")
        val result = toolboxManager.executeTool("test_tool", parameters)
        
        assertFalse(result.success)
        assertTrue(result.message.contains("Invalid parameters"))
    }

    @Test
    fun `test tool execution with non-existent tool`() = runTest {
        val result = toolboxManager.executeTool("non_existent_tool", emptyMap())
        
        assertFalse(result.success)
        assertTrue(result.message.contains("not found"))
    }

    @Test
    fun `test tool execution with exception`() = runTest {
        toolboxManager.registerTool(mockTool2)
        
        val result = toolboxManager.executeTool("test_tool_2", emptyMap())
        
        assertFalse(result.success)
        assertTrue(result.message.contains("Error executing tool"))
        assertTrue(result.message.contains("Test exception"))
    }

    @Test
    fun `test find tools by keyword`() {
        toolboxManager.registerTool(mockTool)
        toolboxManager.registerTool(mockTool2)
        
        // Search by name
        val foundByName = toolboxManager.findTools("test_tool")
        assertEquals(2, foundByName.size) // Both contain "test_tool"
        
        // Search by description
        val foundByDescription = toolboxManager.findTools("test tool")
        assertEquals(1, foundByDescription.size)
        assertEquals("test_tool", foundByDescription[0].name)
        
        // Search case insensitive
        val foundCaseInsensitive = toolboxManager.findTools("TEST")
        assertEquals(2, foundCaseInsensitive.size)
    }

    @Test
    fun `test get tools info`() {
        toolboxManager.registerTool(mockTool)
        
        val toolsInfo = toolboxManager.getToolsInfo()
        
        assertTrue(toolsInfo.contains("Tool: test_tool"))
        assertTrue(toolsInfo.contains("Description: A test tool"))
        assertTrue(toolsInfo.contains("Parameters: param1: Test parameter"))
    }

    @Test
    fun `test clear all tools`() {
        toolboxManager.registerTool(mockTool)
        toolboxManager.registerTool(mockTool2)
        
        assertEquals(2, toolboxManager.getToolCount())
        
        toolboxManager.clearAllTools()
        
        assertEquals(0, toolboxManager.getToolCount())
        assertTrue(toolboxManager.getAllTools().isEmpty())
    }

    @Test
    fun `test duplicate tool registration overwrites`() {
        toolboxManager.registerTool(mockTool)
        assertEquals(1, toolboxManager.getToolCount())
        
        // Register a tool with the same name
        val duplicateTool = object : Tool {
            override val name = "test_tool"
            override val description = "Duplicate tool"
            override val parameters = emptyMap<String, String>()
            
            override suspend fun execute(parameters: Map<String, Any>): ToolExecutionResult {
                return ToolExecutionResult(success = true, message = "Duplicate executed")
            }
            
            override fun validateParameters(parameters: Map<String, Any>): Boolean = true
        }
        
        toolboxManager.registerTool(duplicateTool)
        
        // Should still have only one tool, but with new description
        assertEquals(1, toolboxManager.getToolCount())
        val retrievedTool = toolboxManager.getTool("test_tool")
        assertEquals("Duplicate tool", retrievedTool?.description)
    }

    @Test
    fun `test empty tools operations`() {
        // Test operations on empty toolbox
        assertTrue(toolboxManager.getAllTools().isEmpty())
        assertEquals(0, toolboxManager.getToolCount())
        assertNull(toolboxManager.getTool("any_tool"))
        assertTrue(toolboxManager.findTools("anything").isEmpty())
        assertEquals("", toolboxManager.getToolsInfo())
    }

    @Test
    fun `test tools info formatting`() {
        val tool = object : Tool {
            override val name = "format_test"
            override val description = "Test formatting"
            override val parameters = mapOf(
                "param1" to "First param",
                "param2" to "Second param"
            )
            
            override suspend fun execute(parameters: Map<String, Any>): ToolExecutionResult {
                return ToolExecutionResult(success = true, message = "OK")
            }
            
            override fun validateParameters(parameters: Map<String, Any>): Boolean = true
        }
        
        toolboxManager.registerTool(tool)
        val info = toolboxManager.getToolsInfo()
        
        assertTrue(info.contains("Tool: format_test"))
        assertTrue(info.contains("Description: Test formatting"))
        assertTrue(info.contains("param1: First param"))
        assertTrue(info.contains("param2: Second param"))
    }
}
