package com.example.davidapp.live

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.davidapp.ChatMessageType
import com.example.davidapp.DavidViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DavidLiveFinancialConfirmationTest {
    @Test fun speechOnlyProposesAndRepeatedConfirmationWritesOnce() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getDatabase(app)
        withContext(Dispatchers.IO) { db.clearAllTables() }
        val vm = DavidViewModel(app)
        val store = ViewModelStore().apply { put("test", vm) }
        try {
            val turn = DavidLiveTranscriptTurn(id = "expense-one", speaker = DavidLiveSpeaker.USER,
                text = "Потратил 450 рублей на продукты", isFinal = true)
            vm.liveSessionController.onTranscriptTurnsChanged?.invoke(listOf(turn))
            val proposal = withTimeout(5_000) { vm.pendingLiveOperation.filterNotNull().first() }
            assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
            vm.liveSessionController.onTranscriptTurnsChanged?.invoke(listOf(turn))
            vm.confirmChatProposedOperation(proposal.id)
            vm.confirmChatProposedOperation(proposal.id)
            withTimeout(5_000) {
                vm.messages.first { messages -> messages.any {
                    it.id == proposal.id && (it.type as? ChatMessageType.ParsedTransactionProposal)?.isAdded == true
                } }
            }
            val transactions = db.transactionDao().getAllTransactions().first()
            assertEquals(1, transactions.size)
            assertEquals(450.0, transactions.single().amount, 0.0)
            assertNull(vm.pendingLiveOperation.value)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun cancellationNeverWritesTheProposedOperation() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getDatabase(app)
        withContext(Dispatchers.IO) { db.clearAllTables() }
        val vm = DavidViewModel(app)
        val store = ViewModelStore().apply { put("test", vm) }
        try {
            val turn = DavidLiveTranscriptTurn(id = "cancelled-expense", speaker = DavidLiveSpeaker.USER,
                text = "Потратил 450 рублей на продукты", isFinal = true)
            vm.liveSessionController.onTranscriptTurnsChanged?.invoke(listOf(turn))
            val proposal = withTimeout(5_000) { vm.pendingLiveOperation.filterNotNull().first() }
            vm.dismissChatProposedOperation(proposal.id)
            vm.confirmChatProposedOperation(proposal.id)
            assertNull(vm.pendingLiveOperation.value)
            assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun queuedTranscriptCannotCrossProfileBoundaryEvenWhenReturningToSameProfile() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = DavidViewModel(app)
        val store = ViewModelStore().apply { put("test", vm) }
        try {
            vm.liveSessionController.onTranscriptTurnsChanged?.invoke(listOf(
                DavidLiveTranscriptTurn(id = "old-profile", speaker = DavidLiveSpeaker.USER,
                    text = "Потратил 450 рублей на продукты", isFinal = true)
            ))
            vm.bindProfile("other", "Другой", "")
            vm.bindProfile("default", "Пользователь", "")
            advanceUntilIdle()
            assertNull(vm.pendingLiveOperation.value)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun partialInterruptedAndAmbiguousTransfersDoNotBecomeExpenses() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = DavidViewModel(app)
        val store = ViewModelStore().apply { put("test", vm) }
        try {
            val base = DavidLiveTranscriptTurn(id = "partial", speaker = DavidLiveSpeaker.USER,
                text = "Потратил 450 рублей на продукты", isFinal = false)
            vm.liveSessionController.onTranscriptTurnsChanged?.invoke(listOf(
                base, base.copy(id = "interrupted", isFinal = true, wasInterrupted = true),
                base.copy(id = "debt", isFinal = true, text = "Что если вернуть долг 450 рублей"),
                base.copy(id = "transfer", isFinal = true, text = "Перевод 450 рублей")
            ))
            advanceUntilIdle()
            assertNull(vm.pendingLiveOperation.value)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }


    @Test fun explicitVoiceConfirmationSavesPendingProposal() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getDatabase(app)
        withContext(Dispatchers.IO) { db.clearAllTables() }
        val vm = DavidViewModel(app)
        val store = ViewModelStore().apply { put("test", vm) }
        try {
            val expense = DavidLiveTranscriptTurn(id = "voice-expense", speaker = DavidLiveSpeaker.USER,
                text = "Потратил 450 рублей на продукты", isFinal = true)
            vm.liveSessionController.recordTranscriptTurn(DavidLiveSpeaker.USER, expense.text)
            val proposal = withTimeout(5_000) { vm.pendingLiveOperation.filterNotNull().first() }
            vm.liveSessionController.recordTranscriptTurn(DavidLiveSpeaker.USER, "Подтверждаю запись.")
            withTimeout(5_000) {
                vm.messages.first { messages -> messages.any {
                    it.id == proposal.id && (it.type as? ChatMessageType.ParsedTransactionProposal)?.isAdded == true
                } }
            }
            assertEquals(1, db.transactionDao().getAllTransactions().first().size)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }


    @Test fun correctionToDebtInvalidatesEarlierExpenseProposal() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getDatabase(app)
        withContext(Dispatchers.IO) { db.clearAllTables() }
        val vm = DavidViewModel(app)
        val store = ViewModelStore().apply { put("test", vm) }
        try {
            val turn = DavidLiveTranscriptTurn(id = "corrected", speaker = DavidLiveSpeaker.USER,
                text = "Потратил 450 рублей на продукты", isFinal = true)
            vm.liveSessionController.onTranscriptTurnsChanged?.invoke(listOf(turn))
            val proposal = withTimeout(5_000) { vm.pendingLiveOperation.filterNotNull().first() }
            vm.liveSessionController.onTranscriptTurnsChanged?.invoke(listOf(
                turn.copy(text = "Вернул долг 450 рублей")
            ))
            val corrected = withTimeout(5_000) { vm.pendingLiveOperation.filterNotNull().first() }
            assertEquals(com.example.data.repository.FinancialOperationKind.REPAY_WE_OWE,
                (corrected?.type as? ChatMessageType.ParsedTransactionProposal)?.operation?.financialKind)
            vm.confirmChatProposedOperation(proposal.id)
            assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }


    @Test fun debtDeclarationRequiresConfirmationAndCreatesNoIncome() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = AppDatabase.getDatabase(app)
        withContext(Dispatchers.IO) { db.clearAllTables() }
        val vm = DavidViewModel(app)
        val store = ViewModelStore().apply { put("test", vm) }
        try {
            vm.liveSessionController.recordTranscriptTurn(DavidLiveSpeaker.USER, "Я должен Ивану 500 рублей")
            val proposal = withTimeout(5_000) { vm.pendingLiveOperation.filterNotNull().first() }
            assertTrue(db.accountDao().getAllAccounts().first().isEmpty())
            vm.confirmChatProposedOperation(proposal.id)
            withTimeout(5_000) { vm.messages.first { messages -> messages.any {
                it.id == proposal.id && (it.type as? ChatMessageType.ParsedTransactionProposal)?.isAdded == true
            } } }
            assertEquals("we_owe", db.accountDao().getAllAccounts().first().single().type)
            assertEquals(500.0, db.accountDao().getAllAccounts().first().single().balance, 0.0)
            assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
        } finally { store.clear(); Dispatchers.resetMain() }
    }

}
