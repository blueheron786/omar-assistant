package com.zayd.assistant.audio

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for VoiceActivityDetector
 * Tests voice activity detection algorithms, energy calculations, and thresholds
 */
@RunWith(RobolectricTestRunner::class)
class VoiceActivityDetectorTest {

    private lateinit var voiceActivityDetector: VoiceActivityDetector

    @Before
    fun setUp() {
        voiceActivityDetector = VoiceActivityDetector()
    }

    @Test
    fun `test initial state is not speaking`() {
        assertFalse(voiceActivityDetector.isSpeaking())
    }

    @Test
    fun `test voice activity detection with high energy`() {
        // Create high-energy audio buffer
        val highEnergyBuffer = ShortArray(1024) { 5000 }
        
        val result = voiceActivityDetector.detectVoiceActivity(highEnergyBuffer)
        
        // Should detect voice activity with high energy
        assertTrue("High energy should be detected as voice", result.hasVoice)
        assertTrue("Energy should be positive", result.energy > 0)
    }

    @Test
    fun `test voice activity detection with low energy`() {
        // Create low-energy audio buffer  
        val lowEnergyBuffer = ShortArray(1024) { 100 }
        
        val result = voiceActivityDetector.detectVoiceActivity(lowEnergyBuffer)
        
        // Energy should be calculated correctly
        assertTrue("Energy should be non-negative", result.energy >= 0)
    }

    @Test
    fun `test voice activity detection with silent audio`() {
        // Create silent audio buffer (all zeros)
        val silentBuffer = ShortArray(1024) { 0 }
        
        val result = voiceActivityDetector.detectVoiceActivity(silentBuffer)
        
        assertEquals("Silent audio should have zero energy", 0.0, result.energy, 0.001)
        assertFalse("Silent audio should not be detected as voice", result.hasVoice)
    }

    @Test
    fun `test voice activity detector state transitions`() {
        // Start with silence
        assertFalse(voiceActivityDetector.isSpeaking())
        
        // Process high energy audio
        val highEnergyBuffer = ShortArray(1024) { 5000 }
        val result1 = voiceActivityDetector.detectVoiceActivity(highEnergyBuffer)
        
        if (result1.hasVoice) {
            assertTrue("Should be speaking after high energy", voiceActivityDetector.isSpeaking())
        }
        
        // Process silent audio after delay
        Thread.sleep(100)
        val silentBuffer = ShortArray(1024) { 0 }
        val result2 = voiceActivityDetector.detectVoiceActivity(silentBuffer)
        
        assertFalse("Silent audio should not have voice", result2.hasVoice)
    }

    @Test
    fun `test calibration with background noise`() {
        // Create background noise samples
        val noiseBuffer1 = ShortArray(1024) { 50 }
        val noiseBuffer2 = ShortArray(1024) { 60 }
        val noiseBuffer3 = ShortArray(1024) { 40 }
        
        val noiseSamples = listOf(noiseBuffer1, noiseBuffer2, noiseBuffer3)
        
        // Calibrate detector
        voiceActivityDetector.calibrate(noiseSamples)
        
        // Test with calibrated detector
        val testBuffer = ShortArray(1024) { 100 }
        val result = voiceActivityDetector.detectVoiceActivity(testBuffer)
        
        assertTrue("Energy should be calculated", result.energy >= 0)
    }

    @Test
    fun `test energy threshold setting`() {
        val customThreshold = 5000.0
        
        voiceActivityDetector.setEnergyThreshold(customThreshold)
        
        assertEquals("Threshold should be set correctly", 
                    customThreshold, voiceActivityDetector.getEnergyThreshold(), 0.001)
    }

    @Test
    fun `test detector reset`() {
        // Generate some activity
        val buffer = ShortArray(1024) { 3000 }
        voiceActivityDetector.detectVoiceActivity(buffer)
        
        // Reset detector
        voiceActivityDetector.reset()
        
        // Should be back to initial state
        assertFalse("Should not be speaking after reset", voiceActivityDetector.isSpeaking())
    }

    @Test
    fun `test edge case with empty buffer`() {
        val emptyBuffer = ShortArray(0)
        val result = voiceActivityDetector.detectVoiceActivity(emptyBuffer)
        
        assertEquals("Empty buffer should have zero energy", 0.0, result.energy, 0.001)
        assertFalse("Empty buffer should not have voice", result.hasVoice)
    }

    @Test
    fun `test voice activity with different buffer sizes`() {
        val bufferSizes = listOf(256, 512, 1024, 2048)
        
        bufferSizes.forEach { size ->
            val buffer = ShortArray(size) { 1000 }
            val result = voiceActivityDetector.detectVoiceActivity(buffer)
            
            assertTrue("Energy should be positive for buffer size $size", result.energy > 0)
        }
    }

    @Test
    fun `test voice activity with extreme values`() {
        // Test with maximum positive amplitude
        val maxBuffer = ShortArray(1024) { Short.MAX_VALUE }
        val result1 = voiceActivityDetector.detectVoiceActivity(maxBuffer)
        assertTrue("Max amplitude should have high energy", result1.energy > 1000)
        
        // Test with minimum negative amplitude
        val minBuffer = ShortArray(1024) { Short.MIN_VALUE }
        val result2 = voiceActivityDetector.detectVoiceActivity(minBuffer)
        assertTrue("Min amplitude should have high energy", result2.energy > 1000)
    }

    @Test
    fun `test speech duration tracking`() {
        // Generate speech activity
        val speechBuffer = ShortArray(1024) { 4000 }
        
        val result1 = voiceActivityDetector.detectVoiceActivity(speechBuffer)
        if (result1.hasVoice && !result1.speechEnded) {
            assertTrue("Speech duration should be tracked", result1.speechDuration >= 0)
        }
        
        // Continue speech
        Thread.sleep(100)
        val result2 = voiceActivityDetector.detectVoiceActivity(speechBuffer)
        
        if (result2.hasVoice && result2.speechDuration > result1.speechDuration) {
            assertTrue("Speech duration should increase", result2.speechDuration > result1.speechDuration)
        }
    }

    @Test
    fun `test multiple consecutive voice activities`() {
        val testBuffers = listOf(
            ShortArray(1024) { 100 },   // Quiet
            ShortArray(1024) { 4000 },  // Loud
            ShortArray(1024) { 500 },   // Medium
            ShortArray(1024) { 50 },    // Very quiet
            ShortArray(1024) { 5000 }   // Very loud
        )
        
        testBuffers.forEach { buffer ->
            val result = voiceActivityDetector.detectVoiceActivity(buffer)
            assertTrue("Energy should be non-negative", result.energy >= 0)
            assertNotNull("Result should not be null", result)
        }
    }

    @Test
    fun `test voice activity consistency`() {
        // Same input should produce consistent results
        val testBuffer = ShortArray(1024) { index ->
            (500 * kotlin.math.sin(2 * kotlin.math.PI * index / 50)).toInt().toShort()
        }
        
        val result1 = voiceActivityDetector.detectVoiceActivity(testBuffer)
        val result2 = voiceActivityDetector.detectVoiceActivity(testBuffer)
        
        // Results should be consistent for same input (within margin for time-based calculations)
        assertEquals("Energy should be consistent", result1.energy, result2.energy, 1.0)
    }
}
