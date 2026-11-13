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
import androidx.compose.ui.unit.dp

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
                    .height(490.dp)
                    .testTag("manual_inline_calendar"),
                embedded = true
            )
        } else {
            formContent()
        }
    }
}
