package com.example

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        val description = "Поездка на метро"
        try {
            waitFor("create_budget_fab", 40_000)
            capture("01-profile-picker")
            compose.onNodeWithTag("create_budget_fab", useUnmergedTree = true).performClick()

            waitFor("inline_create_budget_card")
            capture("02-create-profile")
            compose.onNodeWithText("Личный бюджет", useUnmergedTree = true).performClick()
            compose.onNodeWithText("Создать и войти", useUnmergedTree = true).performClick()

            waitFor("tab_period", 30_000)
            waitForScreen("Можно потратить сегодня")
            capture("03-new-budget-dashboard")
            compose.onNodeWithTag("embedded_add_operation_button", useUnmergedTree = true).performClick()

            waitFor("transaction_amount_input")
            capture("04-add-expense-empty")
            compose.onNodeWithTag("transaction_amount_input", useUnmergedTree = true)
                .performTextInput("480")
            compose.onNodeWithTag("transaction_description_input", useUnmergedTree = true)
                .performTextInput(description)
            capture("05-add-expense-filled")
            compose.onNodeWithTag("save_transaction_button", useUnmergedTree = true).performClick()
            waitForClosure("save_transaction_button")

            compose.onNodeWithText("Расходы", useUnmergedTree = true)
                .performScrollTo().performClick()
            waitFor("header_counter_text")
            compose.waitUntil(15_000) {
                runCatching {
                    compose.onNodeWithTag("header_counter_text", useUnmergedTree = true)
                        .assertTextEquals("Показано 1 из 1 операций")
                }.isSuccess
            }
            val expenseRow = hasAnyAncestor(hasTestTag("transactions_lazy_list")) and
                hasClickAction() and hasAnyDescendant(hasText(description)) and
                hasAnyDescendant(hasText("-480 ₽"))
            compose.onNode(expenseRow, useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Закрыть", useUnmergedTree = true).performClick()
            waitForClosure("header_counter_text")
            compose.onNodeWithText("Можно потратить сегодня", useUnmergedTree = true).performScrollTo()
            capture("06-dashboard-after-expense")

            for ((tag, heading, filename) in listOf(
                Triple("tab_debts", "ОБЯЗАТЕЛЬСТВА И ЗАЙМЫ", "07-debts"),
                Triple("tab_goals", "КАПИТАЛ И КОПИЛКИ", "08-goals"),
                Triple("tab_annual", "ГОДОВОЙ АУДИТ", "09-annual-report"),
                Triple("tab_period", "Можно потратить сегодня", "10-return-to-dashboard")
            )) {
                waitFor(tag)
                compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()
                waitForScreen(heading)
                capture(filename)
            }
        } catch (failure: Throwable) {
            runCatching { capture("99-failure") }
            throw failure
        }
    }

    private fun waitForClosure(tag: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitForScreen(heading: String) {
        try {
            compose.waitUntil(20_000) {
                runCatching {
                    compose.onNodeWithText(heading, useUnmergedTree = true).assertIsDisplayed()
                }.isSuccess
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Screen heading not visible: $heading", timeout)
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
        val dir = File(context.filesDir, "user-journey-audit")
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
