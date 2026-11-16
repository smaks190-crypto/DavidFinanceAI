package com.example.ui.components.dialogs

import com.example.data.db.RecurringPaymentEntity

/**
 * Read-only summary for the entry card. Status is the same source of truth as the manager.
 * Future dates include today; an overdue income is not an overdue expense.
 */
internal data class RecurringPaymentsCardSummary(
    val activeCount: Int,
    val pausedCount: Int,
    val nearestUpcomingEpochDay: Long?,
    val hasOverdueExpense: Boolean
)

internal fun buildRecurringPaymentsCardSummary(
    payments: List<RecurringPaymentEntity>,
    todayEpochDay: Long
): RecurringPaymentsCardSummary {
    val active = payments.filter { it.status == "ACTIVE" }
    return RecurringPaymentsCardSummary(
        activeCount = active.size,
        pausedCount = payments.count { it.status == "PAUSED" },
        nearestUpcomingEpochDay = active
            .asSequence()
            .map { it.nextDueDateEpochDay }
            .filter { it >= todayEpochDay }
            .minOrNull(),
        hasOverdueExpense = active.any {
            !it.isIncome && it.nextDueDateEpochDay < todayEpochDay
        }
    )
}
