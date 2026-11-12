package com.example.davidapp.live

import com.example.data.db.TransactionDao
import com.example.data.db.TransactionEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DavidLiveStateOrchestrationTest {
    private fun TestScope.controller(dao: TransactionDao? = null) = DavidLiveSessionController(
        DavidLiveFinancialToolsProvider("default", dao, null, null, null),
        controllerDispatcher = StandardTestDispatcher(testScheduler)
    )

    private fun DavidLiveSessionController.setup() = processServerMessageForTest("""{"setupComplete":{}}""")

    private fun DavidLiveSessionController.lookup() = processServerMessageForTest(
        """{"toolCall":{"functionCalls":[{"id":"recent-1","name":"get_recent_transactions","args":{}}]}}"""
    )

    @Test fun `only confirmed setup publishes success and duplicate ack does not extend pulse`() = runTest {
        val c = controller()
        val visual = c.visualState // Observe the existing flow, not its recomputing getter.
        c.triggerSuccessPulse()
        assertFalse(c.isSuccessPulseActive.value)
        assertEquals(DavidLiveVisualState.IDLE_LISTENING, visual.value)
        c.setup()
        assertEquals(DavidLiveVisualState.SUCCESS, visual.value)
        assertEquals(DavidLiveConnectionState.LISTENING, c.connectionState.value)
        runCurrent()
        advanceTimeBy(600)
        c.setup()
        advanceTimeBy(301)
        runCurrent()
        assertEquals(DavidLiveVisualState.IDLE_LISTENING, visual.value)
        c.endSession()
    }

    @Test fun `replaced pulse cannot expire new pulse or change conversation state`() = runTest {
        val c = controller()
        c.setup()
        runCurrent()
        advanceTimeBy(901)
        runCurrent()
        c.triggerSuccessPulse(500)
        runCurrent()
        advanceTimeBy(400)
        c.triggerSuccessPulse(700)
        runCurrent()
        advanceTimeBy(101)
        runCurrent()
        assertTrue(c.isSuccessPulseActive.value)
        assertEquals(DavidLiveConnectionState.LISTENING, c.connectionState.value)
        advanceTimeBy(600)
        runCurrent()
        assertFalse(c.isSuccessPulseActive.value)
        assertEquals(DavidLiveVisualState.IDLE_LISTENING, c.visualState.value)
        c.endSession()
    }

    @Test fun `old connection flash cannot dismiss confirmation after recovery`() = runTest {
        val c = controller()
        c.setup()
        runCurrent()
        advanceTimeBy(600)
        c.simulateFailureForTest(DavidLiveConnectionFailureKind.NETWORK_OFFLINE)
        c.triggerSuccessPulse()
        assertEquals(DavidLiveVisualState.OFFLINE, c.visualState.value)
        c.setup()
        runCurrent()
        advanceTimeBy(301)
        runCurrent()
        assertEquals(DavidLiveVisualState.SUCCESS, c.visualState.value)
        advanceTimeBy(600)
        runCurrent()
        assertEquals(DavidLiveVisualState.IDLE_LISTENING, c.visualState.value)
        c.endSession()
    }

    @Test fun `tool analysis and response preparation follow real events`() = runTest {
        val c = controller()
        c.setup()
        c.lookup()
        assertEquals(DavidLiveVisualState.FINANCE_LOOKUP, c.visualState.value)
        c.triggerSuccessPulse(500)
        assertEquals(DavidLiveVisualState.FINANCE_LOOKUP, c.visualState.value)
        c.setup() // A duplicate ack cannot reset in-flight work.
        assertTrue(c.isToolLookupActive.value)
        runCurrent()
        assertEquals(DavidLiveVisualState.ANALYZING, c.visualState.value)
        c.processServerMessageForTest("""{"serverContent":{"modelTurn":{"parts":[{"thought":true,"text":"analysis"}]}}}""")
        assertEquals(DavidLiveVisualState.ANALYZING, c.visualState.value)
        c.processServerMessageForTest("""{"serverContent":{"outputTranscription":{"text":"Вот ваш ответ"}}}""")
        assertEquals(DavidLiveVisualState.PREPARING_RESPONSE, c.visualState.value)
        c.audioEngine.simulateEnqueuedAndAudibleSamplesForTest(24_000, 12_000, false)
        runCurrent()
        assertEquals(DavidLiveVisualState.DAVID_SPEAKING, c.visualState.value)
        c.processServerMessageForTest("""{"serverContent":{"turnComplete":true}}""")
        assertEquals(DavidLiveVisualState.DAVID_SPEAKING, c.visualState.value)
        c.audioEngine.simulateEnqueuedAndAudibleSamplesForTest(24_000, 24_000, true)
        runCurrent()
        assertEquals(DavidLiveVisualState.IDLE_LISTENING, c.visualState.value)
        c.endSession()
    }

    @Test fun `late database result cannot overwrite restored connection`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val c = controller(suspendedTransactions(gate))
        c.setup()
        c.lookup()
        runCurrent()
        assertEquals(DavidLiveVisualState.FINANCE_LOOKUP, c.visualState.value)
        c.simulateFailureForTest(DavidLiveConnectionFailureKind.NETWORK_OFFLINE)
        assertEquals(DavidLiveVisualState.OFFLINE, c.visualState.value)
        c.setup()
        gate.complete(Unit)
        runCurrent()
        assertEquals(DavidLiveVisualState.SUCCESS, c.visualState.value)
        assertFalse(c.isToolLookupActive.value)
        assertFalse(c.isAnalyzingToolData.value)
        assertNull(c.activeToolName.value)
        c.endSession()
    }

    @Test fun `barge in invalidates tool work and old success timers`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val c = controller(suspendedTransactions(gate))
        c.setup()
        c.lookup()
        runCurrent()
        c.handleConfirmedUserBargeIn()
        c.processServerMessageForTest("""{"serverContent":{"inputTranscription":{"text":"Другой вопрос"}}}""")
        gate.complete(Unit)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(DavidLiveVisualState.THINKING, c.visualState.value)
        assertFalse(c.isAnalyzingToolData.value)
        assertFalse(c.isSuccessPulseActive.value)
        c.endSession()
    }

    @Test fun `mute does not hide response processing and closed session ignores late signals`() {
        for (state in listOf(DavidLiveConnectionState.IDLE, DavidLiveConnectionState.ENDED)) {
            assertEquals(DavidLiveVisualState.IDLE_LISTENING, DavidLiveVisualStateResolver.resolve(
                state, DavidLiveSpeaker.DAVID, isToolLookupActive = true, isSuccessPulseActive = true
            ))
        }
        assertEquals(DavidLiveVisualState.PREPARING_RESPONSE, DavidLiveVisualStateResolver.resolve(
            DavidLiveConnectionState.MUTED, DavidLiveSpeaker.USER,
            isMicMuted = true, isPreparingResponse = true, isSuccessPulseActive = true
        ))
        assertEquals(DavidLiveVisualState.DAVID_SPEAKING, DavidLiveVisualStateResolver.resolve(
            DavidLiveConnectionState.MUTED, DavidLiveSpeaker.DAVID,
            isMicMuted = true, isPreparingResponse = true, isSuccessPulseActive = true
        ))
    }

    private fun suspendedTransactions(gate: CompletableDeferred<Unit>): TransactionDao = object : TransactionDao {
        override fun getTransactionsByBudgetId(budgetId: String): Flow<List<TransactionEntity>> = flow {
            gate.await()
            emit(emptyList())
        }
        override fun getAllTransactions() = getTransactionsByBudgetId("default")
        override suspend fun insertTransaction(transaction: TransactionEntity) = error("Read-only test")
        override suspend fun insertTransactions(transactions: List<TransactionEntity>) = error("Read-only test")
        override suspend fun getExpenseCountForDate(dateStr: String) = 0
        override suspend fun getExpenseSumForDate(dateStr: String) = 0.0
        override fun getExpenseCountForDateSync(dateStr: String) = 0
        override suspend fun deleteTransactionById(id: String) = error("Read-only test")
        override suspend fun deleteTransactionsByBudgetId(budgetId: String) = error("Read-only test")
        override suspend fun deleteAllTransactions() = error("Read-only test")
    }
}
