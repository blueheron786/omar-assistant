package com.omar.assistant.test

import org.junit.runner.RunWith
import org.junit.runners.Suite
import com.omar.assistant.toolbox.ToolboxManagerTest
import com.omar.assistant.llm.gemini.GeminiProviderTest
import com.omar.assistant.audio.VoiceActivityDetectorTest
import com.omar.assistant.core.di.ServiceLocatorTest
import com.omar.assistant.core.orchestrator.AssistantOrchestratorTest
import com.omar.assistant.toolbox.tools.FlashlightToolTest
import com.omar.assistant.toolbox.tools.SmartLightToolTest

/**
 * Test suite for all unit tests
 * Runs all unit tests in a single execution
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    ToolboxManagerTest::class,
    GeminiProviderTest::class,
    VoiceActivityDetectorTest::class,
    ServiceLocatorTest::class,
    AssistantOrchestratorTest::class,
    FlashlightToolTest::class,
    SmartLightToolTest::class
)
class AllUnitTestsSuite
