package com.zayd.assistant.test

import org.junit.runner.RunWith
import org.junit.runners.Suite
import com.zayd.assistant.ui.MainActivityIntegrationTest
import com.zayd.assistant.service.VoiceAssistantServiceIntegrationTest
import com.zayd.assistant.speech.TextToSpeechManagerIntegrationTest

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
