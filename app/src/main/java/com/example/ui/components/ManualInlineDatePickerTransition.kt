package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The bottom capsule supplies its measured available height (before scrolling).
 * The original calendar height is retained whenever there is enough room.
 */
internal fun manualInlineCalendarHeight(availableHeight: Dp): Dp =
    availableHeight.coerceIn(0.dp, 490.dp)

/**
 * Changes only the visible content of manual entry; the caller retains every draft field.
 * Uses the existing calendar body and never opens another Dialog.
 */
@Composable
internal fun ManualInlineDatePickerTransition(
    isDatePickerVisible: Boolean,
    date: String,
    onDismissDatePicker: () -> Unit,
    onConfirmDatePicker: (String) -> Unit,
    calendarHeight: Dp = 490.dp,
    formContent: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = isDatePickerVisible,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 }) togetherWith
                (fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { -it / 8 }) using
                SizeTransform(clip = false)
        },
        label = "manual_inline_date_transition"
    ) { calendarVisible ->
        if (calendarVisible) {
            SingleDatePickerContent(
                initialDate = date,
                onDismiss = onDismissDatePicker,
                onConfirm = onConfirmDatePicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(calendarHeight)
                    .testTag("manual_inline_calendar"),
                embedded = true
            )
        } else {
            formContent()
        }
    }
}
