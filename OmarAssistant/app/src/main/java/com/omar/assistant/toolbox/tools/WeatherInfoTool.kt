package com.omar.assistant.toolbox.tools

import android.content.Context
import com.omar.assistant.nlp.Intent
import com.omar.assistant.toolbox.CommandResult
import com.omar.assistant.toolbox.Tool
import kotlin.random.Random

/**
 * WeatherInfoTool - Provides weather information (simulated for privacy)
 */
class WeatherInfoTool(private val context: Context) : Tool {
    
    override val name = "weather_info"
    override val description = "Provides weather information"
    override val supportedActions = listOf(Intent.ACTION_GET)
    override val supportedEntities = listOf(Intent.ENTITY_WEATHER)
    
    // Simulated weather data for privacy (no external API calls)
    private val weatherConditions = listOf(
        "sunny", "cloudy", "partly cloudy", "rainy", "clear"
    )
    
    override suspend fun execute(
        action: String,
        entity: String?,
        parameters: Map<String, String>
    ): CommandResult {
        
        return when {
            action == Intent.ACTION_GET && entity == Intent.ENTITY_WEATHER -> getWeatherInfo()
            else -> CommandResult(
                success = false,
                response = "I can only tell you about the weather"
            )
        }
    }
    
    override fun canHandle(action: String, entity: String?): Boolean {
        return action == Intent.ACTION_GET && entity == Intent.ENTITY_WEATHER
    }
    
    private fun getWeatherInfo(): CommandResult {
        // Simulate weather data for privacy (no external API calls)
        val temperature = Random.nextInt(15, 30) // Random temperature between 15-30°C
        val condition = weatherConditions.random()
        val humidity = Random.nextInt(30, 80)
        
        return CommandResult(
            success = true,
            response = "The weather is $condition with a temperature of ${temperature}°C and ${humidity}% humidity",
            data = mapOf(
                "temperature" to temperature,
                "condition" to condition,
                "humidity" to humidity,
                "unit" to "celsius"
            )
        )
    }
}
