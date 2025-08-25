package com.omar.assistant.toolbox

import android.util.Log

/**
 * Manages available tools and handles tool execution
 * Provides a registry for custom tools that can be dynamically discovered
 */
class ToolboxManager {
    
    companion object {
        private const val TAG = "ToolboxManager"
    }
    
    private val tools = mutableMapOf<String, Tool>()
    
    /**
     * Registers a new tool in the toolbox
     */
    fun registerTool(tool: Tool) {
        tools[tool.name] = tool
        Log.d(TAG, "Registered tool: ${tool.name}")
    }
    
    /**
     * Unregisters a tool from the toolbox
     */
    fun unregisterTool(toolName: String) {
        tools.remove(toolName)
        Log.d(TAG, "Unregistered tool: $toolName")
    }
    
    /**
     * Gets all available tools
     */
    fun getAllTools(): List<Tool> = tools.values.toList()
    
    /**
     * Gets a specific tool by name
     */
    fun getTool(name: String): Tool? = tools[name]
    
    /**
     * Executes a tool with the given parameters
     */
    suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolExecutionResult {
        val tool = tools[toolName]
        
        if (tool == null) {
            Log.e(TAG, "Tool not found: $toolName")
            return ToolExecutionResult(
                success = false,
                message = "Tool '$toolName' not found"
            )
        }
        
        try {
            // Validate parameters
            if (!tool.validateParameters(parameters)) {
                Log.e(TAG, "Invalid parameters for tool: $toolName")
                return ToolExecutionResult(
                    success = false,
                    message = "Invalid parameters for tool '$toolName'"
                )
            }
            
            Log.d(TAG, "Executing tool: $toolName with parameters: $parameters")
            val result = tool.execute(parameters)
            Log.d(TAG, "Tool execution result: $result")
            
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing tool: $toolName", e)
            return ToolExecutionResult(
                success = false,
                message = "Error executing tool '$toolName': ${e.message}"
            )
        }
    }
    
    /**
     * Gets tools that match a specific category or keyword
     */
    fun findTools(keyword: String): List<Tool> {
        return tools.values.filter { tool ->
            tool.name.contains(keyword, ignoreCase = true) ||
            tool.description.contains(keyword, ignoreCase = true)
        }
    }
    
    /**
     * Gets information about all tools as a formatted string
     */
    fun getToolsInfo(): String {
        return tools.values.joinToString("\n\n") { tool ->
            "Tool: ${tool.name}\n" +
            "Description: ${tool.description}\n" +
            "Parameters: ${tool.parameters.entries.joinToString(", ") { "${it.key}: ${it.value}" }}"
        }
    }
    
    /**
     * Clears all registered tools
     */
    fun clearAllTools() {
        tools.clear()
        Log.d(TAG, "All tools cleared")
    }
    
    /**
     * Gets the number of registered tools
     */
    fun getToolCount(): Int = tools.size
}
