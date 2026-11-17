package com.example

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Сквозной визуальный сценарий на ЧИСТОМ эмуляторе:
 * создание профиля -> главная -> расход -> долги -> цели -> годовой отчет.
 *
 * Только тестовые данные. Не проверяет реальные банковские счета, API или голосовую связь.
 * Скриншоты и Compose semantics сохраняются после каждого шага для UX-аудита.
 */
@RunWith(AndroidJUnit4::class)
class UserJourneyVisualAuditTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun firstRunExpenseAndNavigation() {
        try {
            waitFor("create_budget_fab", 40_000)
            capture("01-profile-picker")
            compose.onNodeWithTag("create_budget_fab", useUnmergedTree = true).performClick()

            waitFor("inline_create_budget_card")
            capture("02-create-profile")
            compose.onNodeWithText("Личный бюджет", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Создать и войти", useUnmergedTree = true).performClick()

            waitFor("tab_period", 30_000)
            capture("03-new-budget-dashboard")
            compose.onNodeWithTag("embedded_add_operation_button", useUnmergedTree = true).performClick()

            waitFor("transaction_amount_input")
            capture("04-add-expense-empty")
            compose.onNodeWithTag("transaction_amount_input", useUnmergedTree = true)
                .performTextInput("480")
            compose.onNodeWithTag("transaction_description_input", useUnmergedTree = true)
                .performTextInput("Поездка на метро")
            capture("05-add-expense-filled")
            compose.onNodeWithTag("save_transaction_button", useUnmergedTree = true).performClick()
            compose.waitUntil(15_000) {
                runCatching {
                    compose.onNodeWithTag("save_transaction_button", useUnmergedTree = true)
                        .assertDoesNotExist()
                    true
                }.getOrDefault(false)
            }
            capture("06-dashboard-after-expense")

            for ((tag, filename) in listOf(
                "tab_debts" to "07-debts",
                "tab_goals" to "08-goals",
                "tab_annual" to "09-annual-report",
                "tab_period" to "10-return-to-dashboard"
            )) {
                waitFor(tag)
                compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()
                compose.waitForIdle()
                capture(filename)
            }
        } catch (failure: Throwable) {
            runCatching { capture("99-failure") }
            throw failure
        }
    }

    private fun waitFor(tag: String, timeoutMs: Long = 20_000) {
        compose.waitUntil(timeoutMs) {
            runCatching {
                compose.onNodeWithTag(tag, useUnmergedTree = true).assertExists()
                true
            }.getOrDefault(false)
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(requireNotNull(context.getExternalFilesDir(null)), "user-journey-audit")
        assertTrue("Could not create screenshot directory", dir.isDirectory || dir.mkdirs())
        val root = compose.onRoot(useUnmergedTree = true)
        val bitmap = root.captureToImage().asAndroidBitmap()
        File(dir, name + ".png").outputStream().use { output ->
            assertTrue("PNG screenshot failed", bitmap.compress(
                android.graphics.Bitmap.CompressFormat.PNG, 100, output
            ))
        }
        // Semantics provides evidence for accessible names and actionable elements.
        File(dir, name + ".txt").writeText(root.printToString().take(150_000))
    }
}
