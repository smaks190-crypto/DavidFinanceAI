package com.example.davidapp.live

import com.example.data.repository.ParsedVoiceOperation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DavidLiveAmbiguousOperationResolverTest {
    private val op = ParsedVoiceOperation("Продукты", "expense", 500.0, "Продукты", date = "2026-11-13")
    @Test fun localSuccessDoesNotCallCloud() = runTest {
        val result = DavidLiveAmbiguousOperationResolver.resolve("Потратил 500 рублей", true, { op }, { error("Cloud must not run") })
        assertEquals(op, result)
    }
    @Test fun negationCannotBecomeAnExpenseEvenIfLocalParserReturnsOne() = runTest {
        assertNull(DavidLiveAmbiguousOperationResolver.resolve("Не потратил 500 рублей", true, { op }, { error("Must not call cloud") }))
    }
    @Test fun ambiguousExplicitExpenseUsesCloud() = runTest {
        assertEquals(op, DavidLiveAmbiguousOperationResolver.resolve("Потратил пятьсот на продукты", true, { null }, { listOf(op) }))
    }
    @Test fun missingKeyAndQuestionsNeverCallCloud() = runTest {
        assertNull(DavidLiveAmbiguousOperationResolver.resolve("Потратил пятьсот", false, { null }, { error("No key") }))
        listOf("Сколько потратил?", "Вернул долг пятьсот", "Переведи пятьсот", "Потратил бы пятьсот если бы мог", "Не потратил пятьсот")
            .forEach { text -> assertNull(DavidLiveAmbiguousOperationResolver.resolve(text, true, { null }, { error(text) })) }
    }
    @Test fun invalidOrMultipleOperationsRequireClarification() = runTest {
        for (results in listOf(listOf(op, op), listOf(op.copy(amount = Double.NaN)), listOf(op.copy(date = "2026-02-30")), listOf(op.copy(debtId = "invented")))) {
            assertNull(DavidLiveAmbiguousOperationResolver.resolve("Потратил пятьсот", true, { null }, { results }))
        }
    }
    @Test fun networkFailureDoesNotProduceProposalAndCancellationPropagates() = runTest {
        assertNull(DavidLiveAmbiguousOperationResolver.resolve("Потратил пятьсот", true, { null }, { throw java.io.IOException() }))
        val cancelled = CancellationException("cancelled")
        try {
            DavidLiveAmbiguousOperationResolver.resolve("Потратил пятьсот", true, { null }, { throw cancelled })
            fail("Cancellation swallowed")
        } catch (actual: CancellationException) { assertEquals(cancelled.message, actual.message) }
    }
}
