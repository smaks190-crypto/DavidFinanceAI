package com.example.ui.components.dialogs

import com.example.data.db.RecurringPaymentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringPaymentsCardSummaryTest {
    private fun payment(
        due: Long,
        status: String = "ACTIVE",
        isIncome: Boolean = false
    ) = RecurringPaymentEntity(
        title = "Запись",
        amount = 100.0,
        categoryName = "Прочее",
        nextDueDateEpochDay = due,
        status = status,
        isIncome = isIncome
    )

    @Test fun emptyListInvitesToAdd() {
        val summary = buildRecurringPaymentsCardSummary(emptyList(), todayEpochDay = 100)
        assertEquals(0, summary.activeCount)
        assertEquals(0, summary.pausedCount)
        assertNull(summary.nearestUpcomingEpochDay)
        assertFalse(summary.hasOverdueExpense)
    }

    @Test fun activePaymentsAndIncomeCountTogetherButDateIsNearestUpcoming() {
        val summary = buildRecurringPaymentsCardSummary(
            listOf(payment(110), payment(105, isIncome = true), payment(120)),
            todayEpochDay = 100
        )
        assertEquals(3, summary.activeCount)
        assertEquals(105L, summary.nearestUpcomingEpochDay)
        assertFalse(summary.hasOverdueExpense)
    }

    @Test fun pausedAndArchivedDoNotAffectUpcomingDate() {
        val summary = buildRecurringPaymentsCardSummary(
            listOf(payment(101, status = "PAUSED"), payment(99, status = "ARCHIVED"), payment(105)),
            todayEpochDay = 100
        )
        assertEquals(1, summary.activeCount)
        assertEquals(1, summary.pausedCount)
        assertEquals(105L, summary.nearestUpcomingEpochDay)
        assertFalse(summary.hasOverdueExpense)
    }

    @Test fun overdueExpenseWarnsButPastIncomeDoesNot() {
        val onlyIncome = buildRecurringPaymentsCardSummary(
            listOf(payment(90, isIncome = true)), todayEpochDay = 100
        )
        assertFalse(onlyIncome.hasOverdueExpense)
        assertNull(onlyIncome.nearestUpcomingEpochDay)

        val withExpense = buildRecurringPaymentsCardSummary(
            listOf(payment(90, isIncome = true), payment(98), payment(106)), todayEpochDay = 100
        )
        assertTrue(withExpense.hasOverdueExpense)
        assertEquals(106L, withExpense.nearestUpcomingEpochDay)
    }

    @Test fun todayIsUpcomingNotOverdue() {
        val summary = buildRecurringPaymentsCardSummary(listOf(payment(100)), todayEpochDay = 100)
        assertEquals(100L, summary.nearestUpcomingEpochDay)
        assertFalse(summary.hasOverdueExpense)
    }
}
