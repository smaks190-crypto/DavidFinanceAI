package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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

    @Test fun manualTransitionRetainsDraftAfterCancellingDate() {
        var isCalendarVisible by mutableStateOf(false)
        var draftAmount by mutableStateOf("1250")
        var draftDescription by mutableStateOf("Покупка продуктов")
        var date by mutableStateOf("2026-11-13")
        rule.setContent {
            ManualInlineDatePickerTransition(
                isDatePickerVisible = isCalendarVisible,
                date = date,
                onDismissDatePicker = { isCalendarVisible = false },
                onConfirmDatePicker = { date = it; isCalendarVisible = false }
            ) {
                Column {
                    BasicTextField(
                        value = draftAmount,
                        onValueChange = { draftAmount = it },
                        modifier = Modifier.testTag("draft_amount")
                    )
                    BasicTextField(
                        value = draftDescription,
                        onValueChange = { draftDescription = it },
                        modifier = Modifier.testTag("draft_description")
                    )
                    Button(onClick = { isCalendarVisible = true }) {
                        Text("Открыть календарь")
                    }
                }
            }
        }
        rule.onNodeWithTag("draft_amount").assertTextEquals("1250")
        rule.onNodeWithTag("draft_description").assertTextEquals("Покупка продуктов")
        rule.onNodeWithText("Открыть календарь").performClick()
        rule.onNodeWithTag("manual_inline_calendar").assertIsDisplayed()
        rule.onNodeWithTag("calendar_day_2026-11-14").performClick()
        rule.onNodeWithContentDescription("Назад").performClick()
        rule.onNodeWithTag("draft_amount").assertTextEquals("1250")
        rule.onNodeWithTag("draft_description").assertTextEquals("Покупка продуктов")
        rule.runOnIdle { assertEquals("2026-11-13", date) }
    }

    @Test fun manualTransitionAppliesDateOnlyOnConfirm() {
        var isCalendarVisible by mutableStateOf(false)
        var draftAmount by mutableStateOf("1250")
        var draftDescription by mutableStateOf("Покупка продуктов")
        var date by mutableStateOf("2026-11-13")
        rule.setContent {
            ManualInlineDatePickerTransition(
                isDatePickerVisible = isCalendarVisible,
                date = date,
                onDismissDatePicker = { isCalendarVisible = false },
                onConfirmDatePicker = { date = it; isCalendarVisible = false }
            ) {
                Column {
                    BasicTextField(
                        value = draftAmount,
                        onValueChange = { draftAmount = it },
                        modifier = Modifier.testTag("draft_amount")
                    )
                    BasicTextField(
                        value = draftDescription,
                        onValueChange = { draftDescription = it },
                        modifier = Modifier.testTag("draft_description")
                    )
                    Button(onClick = { isCalendarVisible = true }) {
                        Text("Открыть календарь")
                    }
                }
            }
        }
        rule.onNodeWithText("Открыть календарь").performClick()
        rule.onNodeWithTag("calendar_day_2026-11-14").performClick()
        rule.onNodeWithText("Выбрать дату").performClick()
        rule.onNodeWithTag("draft_amount").assertTextEquals("1250")
        rule.onNodeWithTag("draft_description").assertTextEquals("Покупка продуктов")
        rule.runOnIdle { assertEquals("2026-11-14", date) }
    }

    @Test fun calendarHeightIsLimitedByAvailableSpace() {
        assertEquals(490.dp, manualInlineCalendarHeight(800.dp))
        assertEquals(490.dp, manualInlineCalendarHeight(490.dp))
        assertEquals(375.dp, manualInlineCalendarHeight(375.dp))
        assertEquals(0.dp, manualInlineCalendarHeight((-20).dp))
    }

    @Test fun compactHeightKeepsConfirmationButtonInsideCalendar() {
        val compactHeight = 320.dp
        var confirmed: String? = null
        rule.setContent {
            Box(Modifier.height(compactHeight).clipToBounds()) {
                ManualInlineDatePickerTransition(
                    isDatePickerVisible = true,
                    date = "2026-11-13",
                    calendarHeight = manualInlineCalendarHeight(compactHeight),
                    onDismissDatePicker = {},
                    onConfirmDatePicker = { confirmed = it }
                ) { Text("Форма") }
            }
        }

        val calendar = rule.onNodeWithTag("manual_inline_calendar").assertIsDisplayed()
        val bounds = calendar.getUnclippedBoundsInRoot()
        assertTrue("Calendar exceeds compact viewport", bounds.height <= compactHeight)
        val button = rule.onNodeWithText("Выбрать дату").assertIsDisplayed()
        assertTrue(
            "Confirm button is outside the calendar",
            button.getUnclippedBoundsInRoot().bottom <= bounds.bottom
        )
        button.performClick()
        rule.runOnIdle { assertEquals("2026-11-13", confirmed) }
    }
}
