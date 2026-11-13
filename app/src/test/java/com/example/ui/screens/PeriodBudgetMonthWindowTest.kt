package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodBudgetMonthWindowTest {

    @Test
    fun januaryUsesPreviousDecemberAcrossYearBoundary() {
        val window = calculatePeriodMonthWindow(selectedMonthIdx = 0, selectedYear = 2026)

        assertEquals("2026-01-01", window.start)
        assertEquals("2026-01-31", window.end)
        assertEquals("2025-12", window.previousMonthPrefix)
    }

    @Test
    fun leapYearFebruaryEndsOnTwentyNinth() {
        val window = calculatePeriodMonthWindow(selectedMonthIdx = 1, selectedYear = 2024)

        assertEquals("2024-02-01", window.start)
        assertEquals("2024-02-29", window.end)
        assertEquals("2024-01", window.previousMonthPrefix)
    }

    @Test
    fun nonLeapFebruaryEndsOnTwentyEighth() {
        val window = calculatePeriodMonthWindow(selectedMonthIdx = 1, selectedYear = 2026)

        assertEquals("2026-02-01", window.start)
        assertEquals("2026-02-28", window.end)
        assertEquals("2026-01", window.previousMonthPrefix)
    }

    @Test
    fun decemberUsesNovemberWithoutChangingYear() {
        val window = calculatePeriodMonthWindow(selectedMonthIdx = 11, selectedYear = 2026)

        assertEquals("2026-12-01", window.start)
        assertEquals("2026-12-31", window.end)
        assertEquals("2026-11", window.previousMonthPrefix)
    }
}
