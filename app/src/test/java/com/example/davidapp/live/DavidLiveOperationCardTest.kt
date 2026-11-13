package com.example.davidapp.live

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class DavidLiveOperationCardTest {
    @get:Rule val compose = createComposeRule()
    @Test fun cardOnlyOffersYesAndNoWithoutEditableFields() {
        var confirmed = 0
        var rejected = 0
        compose.setContent { BudgetTheme {
            DavidLiveOperationCard(ParsedVoiceOperation("Продукты", "expense", 450.0, "Продукты", date = "2026-11-13"),
                emptyList(), false, null, { confirmed++ }, { rejected++ })
        } }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Да").performClick()
        compose.onNodeWithText("Нет").performClick()
        assertEquals(1, confirmed)
        assertEquals(1, rejected)
    }
    @Test fun incompleteTransferCanOnlyBeRejected() {
        compose.setContent { BudgetTheme {
            DavidLiveOperationCard(ParsedVoiceOperation("Перевод", "expense", 450.0, "Перевод", date = "2026-11-13",
                financialKind = FinancialOperationKind.TRANSFER), emptyList(), false, null, {}, {})
        } }
        compose.onNodeWithText("Да").assertIsNotEnabled()
        compose.onNodeWithText("Нет").assertIsEnabled()
    }
}
