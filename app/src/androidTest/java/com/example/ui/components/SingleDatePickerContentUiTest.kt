package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SingleDatePickerContentUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun embeddedConfirmDoesNotDismissParent() {
        var confirmed: String? = null
        var dismissed = false
        rule.setContent {
            Box(Modifier.height(490.dp)) {
                SingleDatePickerContent(
                    initialDate = "2026-11-13",
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = it },
                    modifier = Modifier.fillMaxSize(),
                    embedded = true
                )
            }
        }
        rule.onNodeWithText("Выбрать дату").performClick()
        rule.runOnIdle {
            assertEquals("2026-11-13", confirmed)
            assertFalse(dismissed)
        }
    }

    @Test fun embeddedBackDoesNotSaveDate() {
        var confirmed: String? = null
        var dismissed = false
        rule.setContent {
            Box(Modifier.height(490.dp)) {
                SingleDatePickerContent(
                    initialDate = "2026-11-13",
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = it },
                    modifier = Modifier.fillMaxSize(),
                    embedded = true
                )
            }
        }
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.runOnIdle {
            assertTrue(dismissed)
            assertEquals(null, confirmed)
        }
    }

    @Test fun selectingDifferentDayConfirmsOnlyThatDate() {
        var confirmed: String? = null
        var dismissed = false
        rule.setContent {
            Box(Modifier.height(490.dp)) {
                SingleDatePickerContent(
                    initialDate = "2026-11-13",
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = it },
                    modifier = Modifier.fillMaxSize(),
                    embedded = true
                )
            }
        }
        rule.onNodeWithTag("calendar_day_2026-11-13").assertIsSelected()
        rule.onNodeWithTag("calendar_day_2026-11-14").assertIsNotSelected().performClick()
        rule.onNodeWithTag("calendar_day_2026-11-14").assertIsSelected()
        rule.onNodeWithText("Выбрать дату").performClick()
        rule.runOnIdle {
            assertEquals("2026-11-14", confirmed)
            assertFalse("Confirm should not dismiss manual form", dismissed)
        }
    }

    @Test fun cancellingAfterChoosingAnotherDayDiscardsChange() {
        var confirmed: String? = null
        var dismissed = false
        rule.setContent {
            Box(Modifier.height(490.dp)) {
                SingleDatePickerContent(
                    initialDate = "2026-11-13",
                    onDismiss = { dismissed = true },
                    onConfirm = { confirmed = it },
                    modifier = Modifier.fillMaxSize(),
                    embedded = true
                )
            }
        }
        rule.onNodeWithTag("calendar_day_2026-11-14").performClick()
        rule.onNodeWithTag("calendar_day_2026-11-14").assertIsSelected()
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.runOnIdle {
            assertTrue("Back should close only date selection", dismissed)
            assertEquals("Unconfirmed day must not be applied", null, confirmed)
        }
    }
}
