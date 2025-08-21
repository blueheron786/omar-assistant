package com.omar.assistant.nlp

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

/**
 * LocalNLUProcessor - Local Natural Language Understanding
 * 
 * This class processes user commands locally without sending data to the cloud,
 * ensuring maximum privacy. It uses pattern matching, keyword extraction,
 * and simple rule-based parsing to understand user intents.
 */
class LocalNLUProcessor(private val context: Context) {
    
    private val TAG = "LocalNLUProcessor"
    
    // Intent patterns for different commands
    private val intentPatterns = mutableMapOf<String, List<IntentPattern>>()
    
    // Common synonyms and variations
    private val synonyms = mapOf(
        "turn_on" to listOf("turn on", "switch on", "enable", "activate", "start", "open"),
        "turn_off" to listOf("turn off", "switch off", "disable", "deactivate", "stop", "close"),
        "light" to listOf("light", "lights", "lamp", "bulb", "lighting"),
        "music" to listOf("music", "song", "audio", "sound", "track"),
        "hello" to listOf("hello", "hi", "hey", "greetings", "good morning", "good afternoon"),
        "goodbye" to listOf("goodbye", "bye", "see you", "farewell", "good night"),
        "time" to listOf("time", "clock", "hour", "minute"),
        "weather" to listOf("weather", "temperature", "forecast", "climate")
    )
    
    init {
        initializeIntentPatterns()
    }
    
    /**
     * Initialize intent patterns for command recognition
     */
    private fun initializeIntentPatterns() {
        intentPatterns[Intent.ACTION_TURN_ON] = listOf(
            IntentPattern(
                patterns = listOf("turn on {entity}", "switch on {entity}", "enable {entity}"),
                entities = listOf(Intent.ENTITY_LIGHT, Intent.ENTITY_MUSIC)
            ),
            IntentPattern(
                patterns = listOf("lights on", "turn the lights on", "switch the lights on"),
                entities = listOf(Intent.ENTITY_LIGHT),
                fixedEntity = Intent.ENTITY_LIGHT
            )
        )
        
        intentPatterns[Intent.ACTION_TURN_OFF] = listOf(
            IntentPattern(
                patterns = listOf("turn off {entity}", "switch off {entity}", "disable {entity}"),
                entities = listOf(Intent.ENTITY_LIGHT, Intent.ENTITY_MUSIC)
            ),
            IntentPattern(
                patterns = listOf("lights off", "turn the lights off", "switch the lights off"),
                entities = listOf(Intent.ENTITY_LIGHT),
                fixedEntity = Intent.ENTITY_LIGHT
            )
        )
        
        intentPatterns[Intent.ACTION_GREET] = listOf(
            IntentPattern(
                patterns = listOf("hello", "hi", "hey", "good morning", "good afternoon"),
                entities = emptyList()
            )
        )
        
        intentPatterns[Intent.ACTION_GOODBYE] = listOf(
            IntentPattern(
                patterns = listOf("goodbye", "bye", "see you later", "good night"),
                entities = emptyList()
            )
        )
        
        intentPatterns[Intent.ACTION_GET] = listOf(
            IntentPattern(
                patterns = listOf("what time is it", "current time", "tell me the time"),
                entities = listOf(Intent.ENTITY_TIME),
                fixedEntity = Intent.ENTITY_TIME
            ),
            IntentPattern(
                patterns = listOf("what's the weather", "weather today", "how's the weather"),
                entities = listOf(Intent.ENTITY_WEATHER),
                fixedEntity = Intent.ENTITY_WEATHER
            )
        )
        
        intentPatterns[Intent.ACTION_HELP] = listOf(
            IntentPattern(
                patterns = listOf("help", "what can you do", "commands", "assistance"),
                entities = emptyList()
            )
        )
    }
    
    /**
     * Process a command and extract intent
     */
    fun processCommand(command: String): Intent {
        val normalizedCommand = normalizeCommand(command)
        Log.d(TAG, "Processing command: '$command' -> '$normalizedCommand'")
        
        // Try to match against known patterns
        for ((action, patterns) in intentPatterns) {
            for (pattern in patterns) {
                val matchResult = matchPattern(normalizedCommand, pattern)
                if (matchResult != null) {
                    Log.d(TAG, "Matched pattern for action: $action")
                    return matchResult
                }
            }
        }
        
        // Fall back to keyword-based matching
        return extractIntentFromKeywords(normalizedCommand)
    }
    
    /**
     * Normalize command text for better matching
     */
    private fun normalizeCommand(command: String): String {
        return command
            .lowercase()
            .trim()
            .replace(Regex("\\s+"), " ") // Replace multiple spaces with single space
            .replace(Regex("[.,!?]"), "") // Remove punctuation
    }
    
    /**
     * Match command against a specific pattern
     */
    private fun matchPattern(command: String, pattern: IntentPattern): Intent? {
        for (patternStr in pattern.patterns) {
            val match = matchSinglePattern(command, patternStr)
            if (match != null) {
                val entity = pattern.fixedEntity ?: match.entity
                return Intent(
                    action = getActionFromPattern(pattern),
                    entity = entity,
                    parameters = match.parameters,
                    confidence = match.confidence
                )
            }
        }
        return null
    }
    
    /**
     * Match against a single pattern string
     */
    private fun matchSinglePattern(command: String, pattern: String): PatternMatch? {
        // Handle exact matches first
        if (command == pattern) {
            return PatternMatch(confidence = 1.0f)
        }
        
        // Handle patterns with placeholders
        if (pattern.contains("{entity}")) {
            val patternRegex = pattern.replace("{entity}", "(.+)").toRegex()
            val matchResult = patternRegex.find(command)
            if (matchResult != null) {
                val entityValue = matchResult.groupValues[1].trim()
                val entity = mapToKnownEntity(entityValue)
                return PatternMatch(
                    entity = entity,
                    confidence = calculateConfidence(command, pattern)
                )
            }
        }
        
        // Fuzzy matching for similar phrases
        val similarity = calculateSimilarity(command, pattern)
        if (similarity > 0.7f) {
            return PatternMatch(confidence = similarity)
        }
        
        return null
    }
    
    /**
     * Extract intent using keyword-based approach
     */
    private fun extractIntentFromKeywords(command: String): Intent {
        val words = command.split(" ")
        
        // Check for action keywords
        val action = when {
            words.any { it in synonyms["turn_on"] ?: emptyList() } -> Intent.ACTION_TURN_ON
            words.any { it in synonyms["turn_off"] ?: emptyList() } -> Intent.ACTION_TURN_OFF
            words.any { it in synonyms["hello"] ?: emptyList() } -> Intent.ACTION_GREET
            words.any { it in synonyms["goodbye"] ?: emptyList() } -> Intent.ACTION_GOODBYE
            words.contains("time") || command.contains("what time") -> Intent.ACTION_GET
            words.contains("weather") || command.contains("temperature") -> Intent.ACTION_GET
            words.contains("help") -> Intent.ACTION_HELP
            else -> Intent.ACTION_UNKNOWN
        }
        
        // Check for entity keywords
        val entity = when {
            words.any { it in synonyms["light"] ?: emptyList() } -> Intent.ENTITY_LIGHT
            words.any { it in synonyms["music"] ?: emptyList() } -> Intent.ENTITY_MUSIC
            words.any { it in synonyms["time"] ?: emptyList() } -> Intent.ENTITY_TIME
            words.any { it in synonyms["weather"] ?: emptyList() } -> Intent.ENTITY_WEATHER
            else -> null
        }
        
        val confidence = calculateKeywordConfidence(command, action, entity)
        
        Log.d(TAG, "Keyword extraction result - Action: $action, Entity: $entity, Confidence: $confidence")
        
        return Intent(
            action = action,
            entity = entity,
            confidence = confidence
        )
    }
    
    /**
     * Map text to known entity types
     */
    private fun mapToKnownEntity(text: String): String? {
        val normalizedText = text.lowercase().trim()
        
        return when {
            synonyms["light"]?.any { normalizedText.contains(it) } == true -> Intent.ENTITY_LIGHT
            synonyms["music"]?.any { normalizedText.contains(it) } == true -> Intent.ENTITY_MUSIC
            synonyms["time"]?.any { normalizedText.contains(it) } == true -> Intent.ENTITY_TIME
            synonyms["weather"]?.any { normalizedText.contains(it) } == true -> Intent.ENTITY_WEATHER
            else -> null
        }
    }
    
    /**
     * Calculate similarity between two strings using Levenshtein distance
     */
    private fun calculateSimilarity(str1: String, str2: String): Float {
        val longer = if (str1.length > str2.length) str1 else str2
        val shorter = if (str1.length > str2.length) str2 else str1
        
        if (longer.isEmpty()) return 1.0f
        
        val editDistance = levenshteinDistance(longer, shorter)
        return (longer.length - editDistance).toFloat() / longer.length
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     */
    private fun levenshteinDistance(str1: String, str2: String): Int {
        val dp = Array(str1.length + 1) { IntArray(str2.length + 1) }
        
        for (i in 0..str1.length) dp[i][0] = i
        for (j in 0..str2.length) dp[0][j] = j
        
        for (i in 1..str1.length) {
            for (j in 1..str2.length) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[str1.length][str2.length]
    }
    
    /**
     * Calculate confidence for pattern matching
     */
    private fun calculateConfidence(command: String, pattern: String): Float {
        val similarity = calculateSimilarity(command, pattern)
        val lengthRatio = minOf(command.length, pattern.length).toFloat() / 
                         maxOf(command.length, pattern.length)
        return (similarity + lengthRatio) / 2.0f
    }
    
    /**
     * Calculate confidence for keyword-based extraction
     */
    private fun calculateKeywordConfidence(command: String, action: String, entity: String?): Float {
        var confidence = 0.0f
        
        // Confidence based on action detection
        confidence += when (action) {
            Intent.ACTION_UNKNOWN -> 0.1f
            else -> 0.5f
        }
        
        // Confidence boost for entity detection
        if (entity != null) {
            confidence += 0.3f
        }
        
        // Confidence boost for exact keyword matches
        val words = command.split(" ")
        if (words.size <= 5) confidence += 0.2f // Short commands are usually clearer
        
        return confidence.coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Get action from pattern
     */
    private fun getActionFromPattern(pattern: IntentPattern): String {
        return intentPatterns.entries.find { it.value.contains(pattern) }?.key ?: Intent.ACTION_UNKNOWN
    }
    
    /**
     * Add custom intent pattern
     */
    fun addIntentPattern(action: String, pattern: IntentPattern) {
        if (!intentPatterns.containsKey(action)) {
            intentPatterns[action] = mutableListOf()
        }
        (intentPatterns[action] as MutableList).add(pattern)
        Log.d(TAG, "Added custom pattern for action: $action")
    }
    
    /**
     * Data classes for pattern matching
     */
    data class IntentPattern(
        val patterns: List<String>,
        val entities: List<String>,
        val fixedEntity: String? = null
    )
    
    private data class PatternMatch(
        val entity: String? = null,
        val parameters: Map<String, String> = emptyMap(),
        val confidence: Float
    )
}
