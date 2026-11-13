package com.example.davidapp.live

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.db.AccountEntity
import com.example.data.repository.FinancialOperationKind
import com.example.data.repository.ParsedVoiceOperation
import com.example.ui.theme.BudgetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DavidLiveFinancialConfirmationDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun transferNeedsTwoDifferentAccountsAndExplicitConfirmation() {
        val operation = mutableStateOf(ParsedVoiceOperation("Перевод", "expense", 200.0, "Перевод",
            date = "2026-11-13", financialKind = FinancialOperationKind.TRANSFER))
        var confirmations = 0
        val saving = mutableStateOf(false)
        compose.setContent { BudgetTheme {
            DavidLiveFinancialConfirmationDialog(operation.value,
                listOf(AccountEntity(id = "card", name = "Карта"), AccountEntity(id = "cash", name = "Наличные", type = "cash")),
                saving.value, null, { operation.value = it }, { confirmations++ }, {})
        } }
        compose.onNodeWithText("Записать").assertIsNotEnabled()
        compose.onNodeWithText("Со счёта: Выберите").performScrollTo().performClick()
        compose.onNodeWithText("Карта · card").performClick()
        compose.onNodeWithText("На счёт: Выберите").performScrollTo().performClick()
        compose.onNodeWithText("Карта · card").performClick()
        compose.onNodeWithText("Записать").assertIsNotEnabled()
        compose.onNodeWithText("На счёт: Карта").performScrollTo().performClick()
        compose.onNodeWithText("Наличные · cash").performClick()
        compose.onNodeWithText("Записать").assertIsEnabled()
        assertEquals(0, confirmations)
        compose.onNodeWithText("Записать").performClick()
        assertEquals(1, confirmations)
        compose.runOnIdle { saving.value = true }
        compose.onNodeWithText("Сохранение…").assertIsNotEnabled()
        compose.onNodeWithText("Отмена").assertIsNotEnabled()
    }

    @Test fun declarationRequiresCounterpartyAndValidDate() {
        val operation = mutableStateOf(ParsedVoiceOperation("Долг", "expense", 200.0, "Долги",
            date = "2026-11-13", financialKind = FinancialOperationKind.REGISTER_WE_OWE))
        compose.setContent { BudgetTheme {
            DavidLiveFinancialConfirmationDialog(operation.value, emptyList(), false, null,
                { operation.value = it }, {}, {})
        } }
        compose.onNodeWithText("Записать").assertIsNotEnabled()
        compose.onNodeWithText("Имя или название долга").performTextInput("Иван")
        compose.onNodeWithText("Записать").assertIsEnabled()
        compose.onNodeWithText("Дата (ГГГГ-ММ-ДД)").performTextReplacement("2026-02-30")
        compose.onNodeWithText("Записать").assertIsNotEnabled()
    }
}
