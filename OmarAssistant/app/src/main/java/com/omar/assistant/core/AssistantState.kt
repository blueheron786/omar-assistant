package com.omar.assistant.core

/**
 * AssistantState - Represents the current state of the Omar Assistant
 */
enum class AssistantState {
    IDLE,
    LISTENING_FOR_WAKE_WORD,
    WAKE_WORD_DETECTED,
    LISTENING_FOR_COMMAND,
    PROCESSING_COMMAND,
    SPEAKING_RESPONSE
}
