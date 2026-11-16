package com.example.ui.components.dialogs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.db.RecurringPaymentEntity
import com.example.ui.theme.BudgetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Regression test: the overview card expands in place; managing is a separate nested action. */
class RecurringPaymentsExpandableCardUiTest {
    @get:Rule val composeTestRule = createComposeRule()

    private fun rent() = RecurringPaymentEntity(
        title = "Аренда",
        amount = 13500.0,
        categoryName = "ЖКХ",
        nextDueDateEpochDay = 105L,
        status = "ACTIVE"
    )

    @Test fun cardExpandsInPlaceAndCanCollapse() {
        var manageClicks = 0
        composeTestRule.setContent {
            BudgetTheme {
                RecurringPaymentsExpandableCard(
                    recurringPayments = listOf(rent()),
                    todayEpochDay = 100L,
                    onOpenManager = { manageClicks++ }
                )
            }
        }

        composeTestRule.onNodeWithText("Аренда").assertDoesNotExist()
        composeTestRule.onNodeWithTag("recurring_payments_expand_toggle").performClick()
        composeTestRule.onNodeWithText("Аренда").assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(0, manageClicks) }

        composeTestRule.onNodeWithTag("recurring_payments_expand_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Аренда").assertDoesNotExist()
    }

    @Test fun managerIsInsideExpandedListAndOpensOnlyWhenRequested() {
        var manageClicks = 0
        composeTestRule.setContent {
            BudgetTheme {
                RecurringPaymentsExpandableCard(
                    recurringPayments = listOf(rent()),
                    todayEpochDay = 100L,
                    onOpenManager = { manageClicks++ }
                )
            }
        }
        composeTestRule.onNodeWithTag("recurring_payments_manage").assertDoesNotExist()
        composeTestRule.onNodeWithTag("recurring_payments_expand_toggle").performClick()
        composeTestRule.onNodeWithTag("recurring_payments_manage").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recurring_payments_manage").performClick()
        composeTestRule.runOnIdle { assertEquals(1, manageClicks) }
    }

    @Test fun emptyListStillOffersAddActionInsideDropdown() {
        composeTestRule.setContent {
            BudgetTheme {
                RecurringPaymentsExpandableCard(
                    recurringPayments = emptyList(),
                    todayEpochDay = 100L,
                    onOpenManager = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("recurring_payments_expand_toggle").performClick()
        composeTestRule.onNodeWithText("Нет запланированных регулярных платежей или доходов").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recurring_payments_manage").assertIsDisplayed()
    }
}
