package com.zayd.assistant.test

import org.junit.runner.RunWith
import org.junit.runners.Suite
import com.zayd.assistant.toolbox.ToolboxManagerTest
import com.zayd.assistant.llm.gemini.GeminiProviderTest
import com.zayd.assistant.audio.VoiceActivityDetectorTest
import com.zayd.assistant.core.di.ServiceLocatorTest
import com.zayd.assistant.core.orchestrator.AssistantOrchestratorTest
import com.zayd.assistant.toolbox.tools.FlashlightToolTest
import com.zayd.assistant.toolbox.tools.SmartLightToolTest

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
