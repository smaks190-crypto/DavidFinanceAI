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
 * чистый профиль -> расход и история -> долг -> цель -> отчет -> Давид -> настройки.
 *
 * Только тестовые данные. Запрос Давиду проверяет локальный режим без Gemini API.
 * Не проверяет реальные банковские счета или голосовую связь.
 * Скриншоты и Compose semantics сохраняются после каждого шага для UX-аудита.
 */
@RunWith(AndroidJUnit4::class)
class UserJourneyVisualAuditTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun firstRunFinanceJourneyIncludingDebtGoalHistoryDavidAndSettings() {
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
            compose.onNodeWithText("Предварительный риск кассового разрыва", substring = true)
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("500 ₽/день", substring = true)
                .performScrollTo().assertIsDisplayed()
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

            waitFor("tab_debts")
            compose.onNodeWithTag("tab_debts", useUnmergedTree = true).performClick()
            waitForScreen("ОБЯЗАТЕЛЬСТВА И ЗАЙМЫ")
            capture("07-debts-empty")
            compose.onNodeWithTag("add_debt_button", useUnmergedTree = true).performClick()
            waitFor("debt_name_input")
            capture("08-add-debt-empty")
            compose.onNodeWithTag("debt_name_input", useUnmergedTree = true).performTextInput("Т-Банк")
            compose.onNodeWithTag("debt_amount_input", useUnmergedTree = true).performTextInput("1200")
            capture("09-add-debt-filled")
            compose.onNodeWithTag("save_debt_button", useUnmergedTree = true).performClick()
            waitForClosure("save_debt_button")
            compose.onNodeWithText("Т-Банк", useUnmergedTree = true).assertIsDisplayed()
            capture("10-debt-created")

            waitFor("tab_goals")
            compose.onNodeWithTag("tab_goals", useUnmergedTree = true).performClick()
            waitForScreen("КАПИТАЛ И КОПИЛКИ")
            capture("11-goals-empty")
            compose.onNodeWithTag("add_goal_button", useUnmergedTree = true).performClick()
            waitFor("goal_name_input")
            capture("12-add-goal-empty")
            compose.onNodeWithTag("goal_name_input", useUnmergedTree = true).performTextInput("Новый ноутбук")
            compose.onNodeWithTag("goal_target_input", useUnmergedTree = true).performTextInput("80000")
            capture("13-add-goal-filled")
            compose.onNodeWithTag("save_goal_button", useUnmergedTree = true).performClick()
            waitForClosure("save_goal_button")
            compose.onNodeWithText("Новый ноутбук", useUnmergedTree = true).assertIsDisplayed()
            capture("14-goal-created")

            waitFor("tab_annual")
            compose.onNodeWithTag("tab_annual", useUnmergedTree = true).performClick()
            waitForScreen("ГОДОВОЙ АУДИТ")
            capture("15-annual-report")

            waitFor("tab_period")
            compose.onNodeWithTag("tab_period", useUnmergedTree = true).performClick()
            waitForScreen("Можно потратить сегодня")
            capture("16-return-to-dashboard")

            compose.onNodeWithText("Давид AI", useUnmergedTree = true).performClick()
            waitFor("david_text_input")
            capture("17-david-chat")
            compose.onNodeWithTag("david_text_input", useUnmergedTree = true)
                .performTextInput("Сколько денег у меня осталось?")
            compose.onNodeWithTag("david_send_button", useUnmergedTree = true).performClick()
            compose.waitUntil(20_000) {
                runCatching {
                    compose.onNodeWithText("мухи отдельно, котлеты отдельно!", substring = true)
                        .assertIsDisplayed()
                }.isSuccess
            }
            compose.onNodeWithText("Расходы за период", substring = true).assertIsDisplayed()
            compose.onNodeWithText("1200 ₽", substring = true).assertIsDisplayed()
            capture("18-david-read-only-financial-answer")

            compose.onNodeWithTag("david_chat_back_button", useUnmergedTree = true).performClick()
            waitForClosure("david_text_input")
            compose.onNodeWithTag("profile_settings_button", useUnmergedTree = true).performClick()
            waitFor("settings_general_item")
            capture("19-settings-hub")
            compose.onNodeWithTag("settings_general_item", useUnmergedTree = true).performClick()
            waitForScreen("ОСНОВНАЯ ВАЛЮТА")
            capture("20-settings-general")
            compose.onNodeWithContentDescription("Назад").performClick()
            waitFor("settings_general_item")
            compose.onNodeWithContentDescription("Закрыть").performClick()
            waitForClosure("settings_general_item")
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
