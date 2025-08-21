package com.omar.assistant.toolbox

/**
 * CommandResult - Result of executing a command
 */
data class CommandResult(
    val success: Boolean,
    val response: String,
    val data: Map<String, Any> = emptyMap()
)

/**
 * Tool - Interface for all executable tools/commands
 */
interface Tool {
    val name: String
    val description: String
    val supportedActions: List<String>
    val supportedEntities: List<String>
    
    suspend fun execute(action: String, entity: String?, parameters: Map<String, String>): CommandResult
    fun canHandle(action: String, entity: String?): Boolean
}
