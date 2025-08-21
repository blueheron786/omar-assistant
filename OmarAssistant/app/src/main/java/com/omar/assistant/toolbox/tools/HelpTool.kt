package com.omar.assistant.toolbox.tools

import android.content.Context
import com.omar.assistant.nlp.Intent
import com.omar.assistant.toolbox.CommandResult
import com.omar.assistant.toolbox.Tool

/**
 * HelpTool - Provides help and information about available commands
 */
class HelpTool(private val context: Context) : Tool {
    
    override val name = "help"
    override val description = "Provides help and information about available commands"
    override val supportedActions = listOf(Intent.ACTION_HELP, Intent.ACTION_UNKNOWN)
    override val supportedEntities = emptyList<String>()
    
    override suspend fun execute(
        action: String,
        entity: String?,
        parameters: Map<String, String>
    ): CommandResult {
        
        return when (action) {
            Intent.ACTION_HELP -> provideHelp()
            Intent.ACTION_UNKNOWN -> handleUnknownCommand()
            else -> CommandResult(
                success = false,
                response = "I can't help with that"
            )
        }
    }
    
    override fun canHandle(action: String, entity: String?): Boolean {
        return action in supportedActions
    }
    
    private fun provideHelp(): CommandResult {
        val helpMessage = buildString {
            appendLine("I'm Omar, your personal assistant. Here's what I can do:")
            appendLine()
            appendLine("🏠 Smart Home:")
            appendLine("• Turn on/off lights")
            appendLine("• Control devices")
            appendLine()
            appendLine("ℹ️ Information:")
            appendLine("• Tell you the time")
            appendLine("• Check the weather")
            appendLine()
            appendLine("💬 Social:")
            appendLine("• Greet you")
            appendLine("• Say goodbye")
            appendLine()
            appendLine("Just say 'Omar' followed by your command. For example:")
            appendLine("• 'Omar, turn on the lights'")
            appendLine("• 'Omar, what time is it?'")
            appendLine("• 'Omar, hello'")
        }
        
        return CommandResult(
            success = true,
            response = helpMessage.trim(),
            data = mapOf("help_type" to "general")
        )
    }
    
    private fun handleUnknownCommand(): CommandResult {
        val unknownMessage = buildString {
            appendLine("I didn't understand that command.")
            appendLine()
            appendLine("Try saying:")
            appendLine("• 'Turn on the lights'")
            appendLine("• 'What time is it?'")
            appendLine("• 'Hello'")
            appendLine("• 'Help' for more options")
        }
        
        return CommandResult(
            success = true,
            response = unknownMessage.trim(),
            data = mapOf("help_type" to "unknown_command")
        )
    }
}
