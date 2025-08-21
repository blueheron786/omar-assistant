package com.omar.assistant.toolbox.tools

import android.content.Context
import com.omar.assistant.nlp.Intent
import com.omar.assistant.toolbox.CommandResult
import com.omar.assistant.toolbox.Tool

/**
 * GreetingTool - Handles greetings and social interactions
 */
class GreetingTool(private val context: Context) : Tool {
    
    override val name = "greeting"
    override val description = "Handles greetings and social interactions"
    override val supportedActions = listOf(Intent.ACTION_GREET, Intent.ACTION_GOODBYE)
    override val supportedEntities = emptyList<String>()
    
    private val greetingResponses = listOf(
        "Hello! How can I help you today?",
        "Hi there! What can I do for you?",
        "Hey! I'm Omar, your assistant. How may I assist you?",
        "Good to see you! What would you like me to do?",
        "Hello! I'm ready to help."
    )
    
    private val goodbyeResponses = listOf(
        "Goodbye! Have a great day!",
        "See you later!",
        "Take care!",
        "Until next time!",
        "Bye! Feel free to call me anytime."
    )
    
    override suspend fun execute(
        action: String,
        entity: String?,
        parameters: Map<String, String>
    ): CommandResult {
        
        return when (action) {
            Intent.ACTION_GREET -> greet()
            Intent.ACTION_GOODBYE -> sayGoodbye()
            else -> CommandResult(
                success = false,
                response = "I don't understand that greeting"
            )
        }
    }
    
    override fun canHandle(action: String, entity: String?): Boolean {
        return action in supportedActions
    }
    
    private fun greet(): CommandResult {
        return CommandResult(
            success = true,
            response = greetingResponses.random(),
            data = mapOf("interaction_type" to "greeting")
        )
    }
    
    private fun sayGoodbye(): CommandResult {
        return CommandResult(
            success = true,
            response = goodbyeResponses.random(),
            data = mapOf("interaction_type" to "goodbye")
        )
    }
}
