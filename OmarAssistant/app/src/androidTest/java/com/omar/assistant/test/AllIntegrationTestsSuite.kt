package com.omar.assistant.test

import org.junit.runner.RunWith
import org.junit.runners.Suite
import com.omar.assistant.ui.MainActivityIntegrationTest
import com.omar.assistant.service.VoiceAssistantServiceIntegrationTest
import com.omar.assistant.speech.TextToSpeechManagerIntegrationTest

/**
 * Test suite for all integration tests
 * Runs all Android instrumentation tests in a single execution
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    MainActivityIntegrationTest::class,
    VoiceAssistantServiceIntegrationTest::class,
    TextToSpeechManagerIntegrationTest::class
)
class AllIntegrationTestsSuite
