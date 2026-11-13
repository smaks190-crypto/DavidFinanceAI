package com.example.ui.screens

import java.util.Calendar
import java.util.Locale

/** Calendar-only values for the period screen; financial totals stay in existing calculators. */
internal data class PeriodBudgetMonthWindow(
    val start: String,
    val end: String,
    val previousMonthPrefix: String
)

/** Month indices follow [Calendar.JANUARY] = 0 through [Calendar.DECEMBER] = 11. */
internal fun calculatePeriodMonthWindow(selectedMonthIdx: Int, selectedYear: Int): PeriodBudgetMonthWindow {
    val monthFormatted = String.format(Locale.US, "%02d", selectedMonthIdx + 1)
    val daysInSelectedMonth = Calendar.getInstance().apply {
        set(Calendar.YEAR, selectedYear)
        set(Calendar.MONTH, selectedMonthIdx)
        set(Calendar.DAY_OF_MONTH, 1)
    }.getActualMaximum(Calendar.DAY_OF_MONTH)

    val previousMonthIdx = if (selectedMonthIdx > 0) selectedMonthIdx - 1 else 11
    val previousYear = if (selectedMonthIdx > 0) selectedYear else selectedYear - 1
    return PeriodBudgetMonthWindow(
        start = "$selectedYear-$monthFormatted-01",
        end = "$selectedYear-$monthFormatted-${String.format(Locale.US, "%02d", daysInSelectedMonth)}",
        previousMonthPrefix = String.format(Locale.US, "%04d-%02d", previousYear, previousMonthIdx + 1)
    )
}
