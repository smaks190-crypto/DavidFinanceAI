package com.example.davidapp.live

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DavidLiveSubtitleIntegrationTest {
    @Test fun `empty buffer without server completion does not finish the turn`() = runTest {
        val controller = DavidLiveSessionController(
            DavidLiveFinancialToolsProvider("default", null, null, null, null),
            controllerDispatcher = StandardTestDispatcher(testScheduler)
        )
        controller.processServerMessageForTest("""{"setupComplete":{}}""")
        controller.processServerMessageForTest("""{"serverContent":{"outputTranscription":{"text":"Секунду"}}}""")
        controller.completeDavidTurnAfterPlaybackDrain()
        runCurrent()
        advanceTimeBy(5_000)
        assertEquals("Секунду", controller.spokenTranscript.value)
        assertEquals(DavidLiveConnectionState.DAVID_SPEAKING, controller.connectionState.value)
        controller.processServerMessageForTest("""{"serverContent":{"turnComplete":true}}""")
        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals("", controller.spokenTranscript.value)
        controller.endSession()
    }

    @Test fun `expiry waits for local playback and preserves history`() = runTest {
        val controller = DavidLiveSessionController(
            DavidLiveFinancialToolsProvider("default", null, null, null, null),
            controllerDispatcher = StandardTestDispatcher(testScheduler)
        )
        controller.processServerMessageForTest("""{"setupComplete":{}}""")
        controller.audioEngine.simulateEnqueuedAndAudibleSamplesForTest(24_000L, 12_000L, false)
        controller.processServerMessageForTest("""{"serverContent":{"outputTranscription":{"text":"Ваш бюджет в порядке"},"turnComplete":true}}""")
        runCurrent()
        advanceTimeBy(5_000)
        assertEquals(DavidLiveConnectionState.DAVID_SPEAKING, controller.connectionState.value)
        controller.audioEngine.simulateEnqueuedAndAudibleSamplesForTest(24_000L, 24_000L, true)
        runCurrent()
        assertEquals("Ваш бюджет в порядке", controller.spokenTranscript.value)
        advanceTimeBy(2_499)
        assertEquals("Ваш бюджет в порядке", controller.spokenTranscript.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("", controller.spokenTranscript.value)
        assertEquals("Ваш бюджет в порядке", controller.transcriptTurns.single().text)
        controller.endSession()
    }

    @Test fun `new user speech is not cleared by completed assistant turn`() = runTest {
        val controller = DavidLiveSessionController(
            DavidLiveFinancialToolsProvider("default", null, null, null, null),
            controllerDispatcher = StandardTestDispatcher(testScheduler)
        )
        controller.processServerMessageForTest("""{"setupComplete":{}}""")
        controller.processServerMessageForTest("""{"serverContent":{"outputTranscription":{"text":"Хорошо"},"turnComplete":true}}""")
        runCurrent()
        advanceTimeBy(2_000)
        controller.processServerMessageForTest("""{"serverContent":{"inputTranscription":{"text":"А завтра?"}}}""")
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("А завтра?", controller.spokenTranscript.value)
        assertEquals(DavidLiveSpeaker.USER, controller.subtitleSpeaker.value)
        controller.endSession()
    }
}
