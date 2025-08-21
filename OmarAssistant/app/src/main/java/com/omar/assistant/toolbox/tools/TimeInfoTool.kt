package com.omar.assistant.toolbox.tools

import android.content.Context
import com.omar.assistant.nlp.Intent
import com.omar.assistant.toolbox.CommandResult
import com.omar.assistant.toolbox.Tool
import java.text.SimpleDateFormat
import java.util.*

/**
 * TimeInfoTool - Provides time and date information
 */
class TimeInfoTool(private val context: Context) : Tool {
    
    override val name = "time_info"
    override val description = "Provides current time and date information"
    override val supportedActions = listOf(Intent.ACTION_GET)
    override val supportedEntities = listOf(Intent.ENTITY_TIME)
    
    override suspend fun execute(
        action: String,
        entity: String?,
        parameters: Map<String, String>
    ): CommandResult {
        
        return when {
            action == Intent.ACTION_GET && entity == Intent.ENTITY_TIME -> getCurrentTime()
            else -> CommandResult(
                success = false,
                response = "I can only tell you the current time"
            )
        }
    }
    
    override fun canHandle(action: String, entity: String?): Boolean {
        return action == Intent.ACTION_GET && entity == Intent.ENTITY_TIME
    }
    
    private fun getCurrentTime(): CommandResult {
        val now = Date()
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        
        val timeString = timeFormat.format(now)
        val dateString = dateFormat.format(now)
        
        return CommandResult(
            success = true,
            response = "It's $timeString on $dateString",
            data = mapOf(
                "time" to timeString,
                "date" to dateString,
                "timestamp" to now.time
            )
        )
    }
}
