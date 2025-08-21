package com.omar.assistant.nlp

/**
 * Intent - Represents a parsed user intent with parameters
 */
data class Intent(
    val action: String,
    val entity: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val confidence: Float = 0.0f
) {
    companion object {
        // Common action types
        const val ACTION_TURN_ON = "turn_on"
        const val ACTION_TURN_OFF = "turn_off"
        const val ACTION_SET = "set"
        const val ACTION_GET = "get"
        const val ACTION_PLAY = "play"
        const val ACTION_STOP = "stop"
        const val ACTION_GREET = "greet"
        const val ACTION_GOODBYE = "goodbye"
        const val ACTION_HELP = "help"
        const val ACTION_UNKNOWN = "unknown"
        
        // Common entities
        const val ENTITY_LIGHT = "light"
        const val ENTITY_MUSIC = "music"
        const val ENTITY_TEMPERATURE = "temperature"
        const val ENTITY_TIME = "time"
        const val ENTITY_WEATHER = "weather"
        const val ENTITY_ALARM = "alarm"
    }
}
