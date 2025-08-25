package com.omar.assistant.toolbox

/**
 * Base interface for all tools that can be executed by the assistant
 * Tools are functions that the AI can discover and call automatically
 */
interface Tool {
    
    /**
     * Unique name of the tool
     */
    val name: String
    
    /**
     * Description of what the tool does
     */
    val description: String
    
    /**
     * Parameters that this tool accepts
     * Key = parameter name, Value = parameter description/type
     */
    val parameters: Map<String, String>
    
    /**
     * Executes the tool with the given parameters
     * Returns the result of the execution
     */
    suspend fun execute(parameters: Map<String, Any>): ToolExecutionResult
    
    /**
     * Validates if the provided parameters are valid for this tool
     */
    fun validateParameters(parameters: Map<String, Any>): Boolean
}

/**
 * Result of tool execution
 */
data class ToolExecutionResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any> = emptyMap()
)
