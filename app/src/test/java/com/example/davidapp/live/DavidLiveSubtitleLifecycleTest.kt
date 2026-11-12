package com.example.davidapp.live

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DavidLiveSubtitleLifecycleTest {
    @Test fun `late audio observer cannot restart a completed subtitle`() = runTest {
        val overlay = DavidLiveSubtitleLifecycle(backgroundScope)
        overlay.beginDavidTurn()
        overlay.updateDavidText("Ответ")
        overlay.completeDavidTurn()
        runCurrent()
        advanceTimeBy(2_000)
        overlay.updateDavidText("Ответ")
        advanceTimeBy(500)
        runCurrent()
        assertEquals("", overlay.text.value)
        overlay.updateDavidText("Ответ")
        assertEquals("", overlay.text.value)
    }

    @Test fun `a pause in transcript delivery does not dismiss speech`() = runTest {
        val overlay = DavidLiveSubtitleLifecycle(backgroundScope)
        overlay.show("Ваш бюджет", DavidLiveSpeaker.DAVID)
        advanceTimeBy(10_000)
        assertEquals("Ваш бюджет", overlay.text.value)
    }

    @Test fun `completed playback holds text for 2500 ms and duplicate completion does not extend it`() = runTest {
        val overlay = DavidLiveSubtitleLifecycle(backgroundScope)
        overlay.show("Ваш бюджет", DavidLiveSpeaker.DAVID)
        overlay.completeDavidTurn()
        runCurrent()
        advanceTimeBy(2_000)
        overlay.completeDavidTurn()
        advanceTimeBy(499)
        assertEquals("Ваш бюджет", overlay.text.value)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("", overlay.text.value)
    }

    @Test fun `identical new utterance survives previous timer`() = runTest {
        val overlay = DavidLiveSubtitleLifecycle(backgroundScope)
        overlay.show("Хорошо", DavidLiveSpeaker.DAVID)
        overlay.completeDavidTurn()
        runCurrent()
        advanceTimeBy(2_000)
        overlay.show("Хорошо", DavidLiveSpeaker.DAVID)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals("Хорошо", overlay.text.value)
    }

    @Test fun `new audio clears previous turn before transcript arrives`() = runTest {
        val overlay = DavidLiveSubtitleLifecycle(backgroundScope)
        overlay.show("Ответ", DavidLiveSpeaker.DAVID)
        overlay.completeDavidTurn()
        overlay.beginDavidTurn()
        assertEquals("", overlay.text.value)
        overlay.show("Новый ответ", DavidLiveSpeaker.DAVID)
        overlay.beginDavidTurn() // another PCM chunk in the same turn
        assertEquals("Новый ответ", overlay.text.value)
    }

    @Test fun `accepted user turn is cleared when assistant audio starts`() = runTest {
        val overlay = DavidLiveSubtitleLifecycle(backgroundScope)
        overlay.show("Сколько осталось?", DavidLiveSpeaker.USER)
        assertEquals(DavidLiveSpeaker.USER, overlay.speaker.value)
        overlay.beginDavidTurn()
        assertEquals("", overlay.text.value)
        assertEquals(DavidLiveSpeaker.DAVID, overlay.speaker.value)
    }

    @Test fun `disconnect cancels expiry and cannot hide restored speech`() = runTest {
        val overlay = DavidLiveSubtitleLifecycle(backgroundScope)
        overlay.show("До обрыва", DavidLiveSpeaker.DAVID)
        overlay.completeDavidTurn()
        runCurrent()
        advanceTimeBy(1_000)
        overlay.clear()
        assertEquals("", overlay.text.value)
        overlay.show("После восстановления", DavidLiveSpeaker.DAVID)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals("После восстановления", overlay.text.value)
    }
}
