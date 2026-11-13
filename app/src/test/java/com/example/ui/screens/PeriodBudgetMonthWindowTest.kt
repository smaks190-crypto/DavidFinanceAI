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

    @Test
    fun previousFromJanuaryMovesToPreviousDecember() {
        val month = calculateAdjacentPeriodMonth(selectedMonthIdx = 0, selectedYear = 2026, step = -1)
        assertEquals(11, month.monthIdx)
        assertEquals(2025, month.year)
    }

    @Test
    fun nextFromDecemberMovesToNextJanuary() {
        val month = calculateAdjacentPeriodMonth(selectedMonthIdx = 11, selectedYear = 2026, step = 1)
        assertEquals(0, month.monthIdx)
        assertEquals(2027, month.year)
    }

    @Test
    fun adjacentMonthsKeepYear() {
        val previous = calculateAdjacentPeriodMonth(selectedMonthIdx = 7, selectedYear = 2026, step = -1)
        val next = calculateAdjacentPeriodMonth(selectedMonthIdx = 7, selectedYear = 2026, step = 1)
        assertEquals(6, previous.monthIdx)
        assertEquals(2026, previous.year)
        assertEquals(8, next.monthIdx)
        assertEquals(2026, next.year)
    }

    @Test
    fun navigationIsReversibleAcrossYearBoundary() {
        val previous = calculateAdjacentPeriodMonth(selectedMonthIdx = 0, selectedYear = 2026, step = -1)
        val restored = calculateAdjacentPeriodMonth(
            selectedMonthIdx = previous.monthIdx,
            selectedYear = previous.year,
            step = 1
        )
        assertEquals(0, restored.monthIdx)
        assertEquals(2026, restored.year)
    }
}
