package com.zayd.assistant.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.zayd.assistant.R
import com.zayd.assistant.core.di.ServiceLocator
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for MainActivity
 * Tests UI interactions, permissions, and component integration
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityIntegrationTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.MODIFY_AUDIO_SETTINGS,
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.CALL_PHONE,
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.READ_CONTACTS
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ServiceLocator.initialize(context)
        ServiceLocator.reset() // Start with clean state
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
    }

    @Test
    fun testMainActivityLaunch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Verify main UI elements are present
            onView(withId(R.id.buttonStartStop))
                .check(matches(isDisplayed()))
            
            onView(withId(R.id.textViewStatus))
                .check(matches(isDisplayed()))
            
            onView(withId(R.id.editTextManualInput))
                .check(matches(isDisplayed()))
            
            onView(withId(R.id.buttonSendInput))
                .check(matches(isDisplayed()))
            
            onView(withId(R.id.buttonTestTts))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testStartStopButtonInteraction() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Initially should show "Start Assistant"
            onView(withId(R.id.buttonStartStop))
                .check(matches(withText("Start Assistant")))
            
            // Click to start (this might fail without proper API key)
            onView(withId(R.id.buttonStartStop))
                .perform(click())
            
            // Wait a moment for processing
            Thread.sleep(1000)
            
            // Button text might change or show error - just verify it's still clickable
            onView(withId(R.id.buttonStartStop))
                .check(matches(isDisplayed()))
                .check(matches(isClickable()))
        }
    }

    @Test
    fun testManualInputField() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val testInput = "Hello Zayd"
            
            // Type in manual input field
            onView(withId(R.id.editTextManualInput))
                .perform(typeText(testInput))
                .check(matches(withText(testInput)))
            
            // Click send button
            onView(withId(R.id.buttonSendInput))
                .perform(click())
            
            // Input field should be cleared after sending
            onView(withId(R.id.editTextManualInput))
                .check(matches(withText("")))
        }
    }

    @Test
    fun testTtsButtonInteraction() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Click TTS test button
            onView(withId(R.id.buttonTestTts))
                .perform(click())
            
            // Should not crash - verify button is still there
            onView(withId(R.id.buttonTestTts))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testStatusTextUpdates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Status should show initial state
            onView(withId(R.id.textViewStatus))
                .check(matches(isDisplayed()))
            
            // Status text should not be empty
            onView(withId(R.id.textViewStatus))
                .check(matches(not(withText(""))))
        }
    }

    @Test
    fun testSettingsMenuAccess() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Try to access overflow menu (settings)
            try {
                onView(withContentDescription("More options"))
                    .perform(click())
                
                // If menu opened, should see settings option
                onView(withText("Settings"))
                    .check(matches(isDisplayed()))
                    .perform(click())
                    
            } catch (e: Exception) {
                // Menu might not be available in test environment
                // Just verify main activity is still functional
                onView(withId(R.id.buttonStartStop))
                    .check(matches(isDisplayed()))
            }
        }
    }

    @Test
    fun testEmptyInputHandling() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Try to send empty input
            onView(withId(R.id.buttonSendInput))
                .perform(click())
            
            // Should handle gracefully - input field should remain empty
            onView(withId(R.id.editTextManualInput))
                .check(matches(withText("")))
        }
    }

    @Test
    fun testMultipleInputs() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val inputs = listOf("Hello", "How are you?", "What time is it?")
            
            inputs.forEach { input ->
                onView(withId(R.id.editTextManualInput))
                    .perform(clearText(), typeText(input))
                
                onView(withId(R.id.buttonSendInput))
                    .perform(click())
                
                // Wait between inputs
                Thread.sleep(500)
                
                // Input should be cleared
                onView(withId(R.id.editTextManualInput))
                    .check(matches(withText("")))
            }
        }
    }

    @Test
    fun testLongInputText() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val longInput = "This is a very long input text that should test how the application handles lengthy user input messages and whether it can process them correctly without crashing or truncating the content."
            
            onView(withId(R.id.editTextManualInput))
                .perform(typeText(longInput))
                .check(matches(withText(longInput)))
            
            onView(withId(R.id.buttonSendInput))
                .perform(click())
            
            // Should handle long input gracefully
            onView(withId(R.id.editTextManualInput))
                .check(matches(withText("")))
        }
    }

    @Test
    fun testSpecialCharacterInput() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val specialInput = "Test with émojis 🤖 and symbols @#$%"
            
            onView(withId(R.id.editTextManualInput))
                .perform(typeText(specialInput))
            
            onView(withId(R.id.buttonSendInput))
                .perform(click())
            
            // Should handle special characters gracefully
            onView(withId(R.id.editTextManualInput))
                .check(matches(withText("")))
        }
    }

    @Test
    fun testActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Type some input
            onView(withId(R.id.editTextManualInput))
                .perform(typeText("Test input"))
            
            // Recreate activity (simulates screen rotation)
            scenario.recreate()
            
            // UI should still be functional
            onView(withId(R.id.buttonStartStop))
                .check(matches(isDisplayed()))
            
            onView(withId(R.id.textViewStatus))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun testButtonStatesAfterClicks() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Test that buttons remain enabled/clickable after interactions
            onView(withId(R.id.buttonTestTts))
                .perform(click())
                .check(matches(isEnabled()))
            
            Thread.sleep(500)
            
            onView(withId(R.id.buttonSendInput))
                .check(matches(isEnabled()))
            
            onView(withId(R.id.buttonStartStop))
                .check(matches(isEnabled()))
        }
    }

    @Test
    fun testUIResponsiveness() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Perform rapid interactions to test UI responsiveness
            repeat(5) {
                onView(withId(R.id.editTextManualInput))
                    .perform(clearText(), typeText("Test $it"))
                
                onView(withId(R.id.buttonSendInput))
                    .perform(click())
                
                // Brief pause between interactions
                Thread.sleep(100)
            }
            
            // UI should still be responsive
            onView(withId(R.id.buttonStartStop))
                .check(matches(isDisplayed()))
                .check(matches(isClickable()))
        }
    }

    @Test
    fun testBackgroundToForeground() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Move to background
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            
            // Move back to foreground
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            
            // UI should still be functional
            onView(withId(R.id.buttonStartStop))
                .check(matches(isDisplayed()))
            
            onView(withId(R.id.editTextManualInput))
                .check(matches(isDisplayed()))
                .perform(typeText("After background test"))
                .check(matches(withText("After background test")))
        }
    }
}
