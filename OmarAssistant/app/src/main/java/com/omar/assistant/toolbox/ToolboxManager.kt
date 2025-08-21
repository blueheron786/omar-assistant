package com.omar.assistant.toolbox

import android.content.Context
import android.util.Log
import com.omar.assistant.nlp.Intent
import com.omar.assistant.toolbox.tools.*

/**
 * ToolboxManager - Manages and executes commands through registered tools
 * 
 * This class provides a registry of tools that can handle different types
 * of commands, allowing for easy extension of the assistant's capabilities.
 */
class ToolboxManager(private val context: Context) {
    
    private val TAG = "ToolboxManager"
    
    private val tools = mutableListOf<Tool>()
    
    /**
     * Register default tools
     */
    fun registerDefaultTools() {
        registerTool(LightControlTool(context))
        registerTool(TimeInfoTool(context))
        registerTool(WeatherInfoTool(context))
        registerTool(GreetingTool(context))
        registerTool(HelpTool(context))
        registerTool(SystemControlTool(context))
        
        Log.d(TAG, "Registered ${tools.size} default tools")
    }
    
    /**
     * Register a new tool
     */
    fun registerTool(tool: Tool) {
        if (tools.none { it.name == tool.name }) {
            tools.add(tool)
            Log.d(TAG, "Registered tool: ${tool.name}")
        } else {
            Log.w(TAG, "Tool already registered: ${tool.name}")
        }
    }
    
    /**
     * Unregister a tool
     */
    fun unregisterTool(toolName: String) {
        val removed = tools.removeAll { it.name == toolName }
        if (removed) {
            Log.d(TAG, "Unregistered tool: $toolName")
        }
    }
    
    /**
     * Execute a command using the appropriate tool
     */
    suspend fun executeCommand(intent: Intent): CommandResult {
        Log.d(TAG, "Executing command - Action: ${intent.action}, Entity: ${intent.entity}")
        
        // Find the best tool to handle this command
        val tool = findBestTool(intent.action, intent.entity)
        
        if (tool != null) {
            try {
                Log.d(TAG, "Using tool: ${tool.name}")
                return tool.execute(intent.action, intent.entity, intent.parameters)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing command with tool ${tool.name}", e)
                return CommandResult(
                    success = false,
                    response = "Sorry, there was an error executing that command."
                )
            }
        } else {
            Log.w(TAG, "No tool found for action: ${intent.action}, entity: ${intent.entity}")
            return CommandResult(
                success = false,
                response = handleUnknownCommand(intent)
            )
        }
    }
    
    /**
     * Find the best tool to handle a command
     */
    private fun findBestTool(action: String, entity: String?): Tool? {
        // First, try exact match
        val exactMatch = tools.find { it.canHandle(action, entity) }
        if (exactMatch != null) {
            return exactMatch
        }
        
        // If no exact match, try by action only
        val actionMatch = tools.find { action in it.supportedActions }
        if (actionMatch != null) {
            return actionMatch
        }
        
        // If still no match and it's an unknown action, use help tool
        if (action == Intent.ACTION_UNKNOWN) {
            return tools.find { it.name == "help" }
        }
        
        return null
    }
    
    /**
     * Handle unknown commands with suggestions
     */
    private fun handleUnknownCommand(intent: Intent): String {
        val suggestions = mutableListOf<String>()
        
        // Suggest similar commands based on available tools
        for (tool in tools) {
            if (tool.supportedActions.isNotEmpty()) {
                val toolSuggestion = "Try '${tool.supportedActions.first()}'"
                if (tool.supportedEntities.isNotEmpty()) {
                    suggestions.add("$toolSuggestion with ${tool.supportedEntities.first()}")
                } else {
                    suggestions.add(toolSuggestion)
                }
            }
        }
        
        return if (suggestions.isNotEmpty()) {
            "I didn't understand that command. ${suggestions.take(2).joinToString(" or ")}"
        } else {
            "I didn't understand that command. Say 'help' to see what I can do."
        }
    }
    
    /**
     * Get list of all available commands
     */
    fun getAvailableCommands(): List<String> {
        val commands = mutableListOf<String>()
        
        for (tool in tools) {
            for (action in tool.supportedActions) {
                if (tool.supportedEntities.isNotEmpty()) {
                    for (entity in tool.supportedEntities) {
                        commands.add("$action $entity")
                    }
                } else {
                    commands.add(action)
                }
            }
        }
        
        return commands.sorted()
    }
    
    /**
     * Get tool information for debugging
     */
    fun getToolInfo(): String {
        val info = StringBuilder()
        info.append("Registered Tools (${tools.size}):\n")
        
        for (tool in tools) {
            info.append("- ${tool.name}: ${tool.description}\n")
            info.append("  Actions: ${tool.supportedActions.joinToString(", ")}\n")
            info.append("  Entities: ${tool.supportedEntities.joinToString(", ")}\n")
        }
        
        return info.toString()
    }
    
    /**
     * Check if a command can be handled
     */
    fun canHandleCommand(action: String, entity: String?): Boolean {
        return findBestTool(action, entity) != null
    }
    
    /**
     * Get tools by category
     */
    fun getToolsByCategory(): Map<String, List<Tool>> {
        return tools.groupBy { tool ->
            when {
                tool.supportedEntities.contains(Intent.ENTITY_LIGHT) -> "Smart Home"
                tool.supportedActions.contains(Intent.ACTION_GET) -> "Information"
                tool.supportedActions.contains(Intent.ACTION_GREET) -> "Social"
                else -> "System"
            }
        }
    }
    
    /**
     * Execute multiple commands in sequence
     */
    suspend fun executeCommandSequence(intents: List<Intent>): List<CommandResult> {
        val results = mutableListOf<CommandResult>()
        
        for (intent in intents) {
            val result = executeCommand(intent)
            results.add(result)
            
            // Stop on first failure if needed
            if (!result.success && intent.action != Intent.ACTION_UNKNOWN) {
                break
            }
        }
        
        return results
    }
}
