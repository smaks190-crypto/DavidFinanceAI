package com.example.davidapp.live

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DavidLiveSphereStatesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `all state transitions keep sphere bounds and touch action`() {
        val state = mutableStateOf(DavidLiveVisualState.IDLE_LISTENING)
        var clicks = 0
        var hostView: View? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            hostView = LocalView.current
            DavidLiveMeshSphereVisualizer(
                state.value, 0f, List(32) { 0f }, false, { clicks++ },
                modifier = Modifier.testTag("sphere_under_test")
            )
        }
        compose.mainClock.advanceTimeBy(500)
        val sphere = compose.onNodeWithTag("sphere_under_test")
        val bounds = sphere.fetchSemanticsNode().boundsInRoot
        for (next in DavidLiveVisualState.entries) {
            compose.runOnIdle { state.value = next }
            compose.mainClock.advanceTimeBy(500)
            assertEquals(bounds, sphere.fetchSemanticsNode().boundsInRoot)
            sphere.performClick()
            if (next == DavidLiveVisualState.SUCCESS) {
                sphere.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Давид на связи"))
            }
            // Optional review artifacts for the local test runner; no files in normal CI runs.
            System.getProperty("live.preview.dir")?.let { directory ->
                val file = File(directory, "${next.name.lowercase()}.png")
                file.parentFile?.mkdirs()
                compose.runOnIdle {
                    val view = requireNotNull(hostView)
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        }
        assertEquals(DavidLiveVisualState.entries.size, clicks)
    }
}
