package com.example.davidapp.live

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.ui.theme.BudgetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DavidLiveSubtitleLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun subtitleSlotKeepsSphereStillInFullScreen() = checkSubtitleGeometry(false)

    @Test fun subtitleSlotKeepsSphereStillInEmbeddedCard() = checkSubtitleGeometry(true)

    private fun checkSubtitleGeometry(embedded: Boolean) {
        val text = mutableStateOf("")
        val speaker = mutableStateOf(DavidLiveSpeaker.USER)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BudgetTheme {
                DavidLiveSheet(
                    connectionState = DavidLiveConnectionState.LISTENING,
                    liveSubtitle = text.value,
                    subtitleSpeaker = speaker.value,
                    rayMagnitudes = List(32) { 0f }, overallEnergy = 0f,
                    activeSpeaker = DavidLiveSpeaker.NONE, isMicMuted = false, errorMessage = null,
                    onToggleMicMute = {}, onRetryConnection = {}, onOpenApiKeySettings = {}, onEndSession = {},
                    isEmbeddedInModalCard = embedded
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        val sphere = compose.onNodeWithTag("david_live_circular_visualizer", useUnmergedTree = true)
        val originalBounds = sphere.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { text.value = "Сколько осталось?" }
        compose.mainClock.advanceTimeBy(500)
        val bubble = compose.onNodeWithTag("david_live_subtitle_bubble", useUnmergedTree = true)
        val userBounds = bubble.fetchSemanticsNode().boundsInRoot
        assertEquals(originalBounds, sphere.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle {
            speaker.value = DavidLiveSpeaker.DAVID
            text.value = "Подробный ответ о бюджете. ".repeat(15)
        }
        compose.mainClock.advanceTimeBy(500)
        org.junit.Assert.assertTrue(bubble.fetchSemanticsNode().boundsInRoot.left > userBounds.left)
        assertEquals(originalBounds, sphere.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { text.value = "" }
        compose.mainClock.advanceTimeBy(200)
        bubble.assertExists() // text stays mounted during the 400 ms exit fade
        assertEquals(originalBounds, sphere.fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(500)
        bubble.assertDoesNotExist()
        assertEquals(originalBounds, sphere.fetchSemanticsNode().boundsInRoot)
    }

}
