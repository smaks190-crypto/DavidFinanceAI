package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConfirmedFinancialOperationsTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: BudgetRepository
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = BudgetRepository(context, db.budgetProfileDao(), db.transactionDao(), db.goalDao(), db.categoryDao(),
            db.aiAuditDao(), db.accountDao(), db.notificationDao(), db)
    }
    @After fun close() { db.close() }
    private fun op(kind: FinancialOperationKind, amount: Double = 200.0) = ParsedVoiceOperation(
        title = "Test", type = "expense", amount = amount, category = "Продукты", subcategory = kind.label,
        date = "2026-11-13", financialKind = kind
    )
    private suspend fun cash(id: String, balance: Double = 1000.0, budgetId: String = "default") {
        db.accountDao().insertAccount(AccountEntity(id = id, budgetId = budgetId, name = id, balance = balance))
    }
    private suspend fun snapshot() = FinancialSemanticsCalculator.calculateSnapshot(
        db.transactionDao().getAllTransactions().first(), accounts = db.accountDao().getAllAccounts().first()
    )
    private suspend fun rejected(id: String, operation: ParsedVoiceOperation) {
        try { repository.confirmFinancialOperation(id, "default", operation); fail("Must reject operation") }
        catch (_: IllegalArgumentException) { }
        assertNull(db.confirmedFinancialOperationDao().getById(id))
    }

    @Test fun transfersAreAtomicAndConcurrentReplaysChangeBalancesOnce() = runBlocking {
        cash("source"); cash("destination", 100.0)
        val operation = op(FinancialOperationKind.TRANSFER).copy(cashAccountId = "source", destinationAccountId = "destination")
        val results = (1..8).map { async(Dispatchers.IO) { repository.confirmFinancialOperation("transfer", "default", operation) } }.awaitAll()
        assertEquals(1, results.count { it })
        assertEquals(800.0, db.accountDao().getAccountById("source")!!.balance, 0.0)
        assertEquals(300.0, db.accountDao().getAccountById("destination")!!.balance, 0.0)
        assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
        assertEquals(1100.0, snapshot().availableBalanceRub, 0.0)
        assertEquals(0.0, snapshot().earnedIncomeRub, 0.0)
        assertEquals(1, db.notificationDao().getNotificationsByBudgetId("default").first().size)
    }

    @Test fun changedTransferPayloadIsRejectedWithoutASecondDebit() = runBlocking {
        cash("source"); cash("destination")
        val operation = op(FinancialOperationKind.TRANSFER).copy(cashAccountId = "source", destinationAccountId = "destination")
        repository.confirmFinancialOperation("transfer", "default", operation)
        try { repository.confirmFinancialOperation("transfer", "default", operation.copy(amount = 300.0)); fail("Payload conflict") }
        catch (_: IllegalArgumentException) { }
        assertEquals(800.0, db.accountDao().getAccountById("source")!!.balance, 0.0)
    }

    @Test fun failedTransferRollsBackSourceAndLedger() = runBlocking {
        cash("source"); cash("destination", Double.MAX_VALUE)
        rejected("overflow", op(FinancialOperationKind.TRANSFER, Double.MAX_VALUE).copy(cashAccountId = "source", destinationAccountId = "destination"))
        assertEquals(1000.0, db.accountDao().getAccountById("source")!!.balance, 0.0)
        assertTrue(db.notificationDao().getNotificationsByBudgetId("default").first().isEmpty())
    }

    @Test fun invalidAccountSelectionsCannotWrite() = runBlocking {
        cash("source"); cash("foreign", budgetId = "other")
        val transfer = op(FinancialOperationKind.TRANSFER).copy(cashAccountId = "source")
        rejected("same", transfer.copy(destinationAccountId = "source"))
        rejected("foreign", transfer.copy(destinationAccountId = "foreign"))
        rejected("deleted", transfer.copy(destinationAccountId = "missing"))
        db.accountDao().insertAccount(AccountEntity(id = "debt", name = "Иван", type = "we_owe", balance = 1000.0))
        rejected("debt-as-cash", transfer.copy(destinationAccountId = "debt"))
        assertEquals(1000.0, db.accountDao().getAccountById("source")!!.balance, 0.0)
    }

    @Test fun declarationsDoNotCreateCashFlowsAndReplaySurvivesDeletion() = runBlocking {
        val operation = op(FinancialOperationKind.REGISTER_WE_OWE).copy(counterparty = "Иван")
        repository.confirmFinancialOperation("declaration", "default", operation)
        assertEquals(200.0, snapshot().totalWeOweRub, 0.0)
        assertEquals(0.0, snapshot().availableBalanceRub, 0.0)
        assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
        db.accountDao().deleteAccountById("declaration:debt")
        assertFalse(repository.confirmFinancialOperation("declaration", "default", operation))
        assertNull(db.accountDao().getAccountById("declaration:debt"))
    }

    @Test fun borrowingThenRepayingUpdatesDebtAndCashWithoutEarnedIncome() = runBlocking {
        cash("wallet", 0.0)
        repository.confirmFinancialOperation("borrow", "default", op(FinancialOperationKind.BORROW, 500.0).copy(counterparty = "Иван", cashAccountId = "wallet"))
        assertEquals(500.0, snapshot().totalWeOweRub, 0.0)
        assertEquals(0.0, snapshot().earnedIncomeRub, 0.0)
        val repayment = op(FinancialOperationKind.REPAY_WE_OWE).copy(debtId = "borrow:debt", cashAccountId = "wallet")
        repository.confirmFinancialOperation("repay", "default", repayment)
        assertFalse(repository.confirmFinancialOperation("repay", "default", repayment))
        assertEquals(300.0, snapshot().totalWeOweRub, 0.0)
        assertEquals(300.0, db.accountDao().getAccountById("wallet")!!.balance, 0.0)
        assertEquals(300.0, snapshot().availableBalanceRub, 0.0)
        rejected("overpay", repayment.copy(amount = 301.0))
        rejected("wrong-direction", repayment.copy(financialKind = FinancialOperationKind.REPAY_OWES_US))
    }

    @Test fun lendingAndReturnedDebtAreNotSalary() = runBlocking {
        cash("wallet")
        repository.confirmFinancialOperation("lend", "default", op(FinancialOperationKind.LEND, 500.0).copy(counterparty = "Иван", cashAccountId = "wallet"))
        repository.confirmFinancialOperation("return", "default", op(FinancialOperationKind.REPAY_OWES_US).copy(debtId = "lend:debt", cashAccountId = "wallet"))
        assertEquals(300.0, snapshot().totalOwesUsRub, 0.0)
        assertEquals(200.0, snapshot().debtReturnedIncomeRub, 0.0)
        assertEquals(0.0, snapshot().earnedIncomeRub, 0.0)
        assertEquals(700.0, db.accountDao().getAccountById("wallet")!!.balance, 0.0)
    }

    @Test fun cashAccountLinkDoesNotTurnSalaryIntoDebtIncome() = runBlocking {
        cash("card", 0.0)
        repository.confirmFinancialOperation("salary", "default", op(FinancialOperationKind.TRANSACTION, 500.0).copy(type = "income", category = "Зарплата", cashAccountId = "card"))
        val transaction = db.transactionDao().getAllTransactions().first().single()
        assertNull(transaction.accountId)
        assertEquals("card", transaction.cashAccountId)
        assertEquals(500.0, snapshot().earnedIncomeRub, 0.0)
        assertEquals(0.0, snapshot().debtReturnedIncomeRub, 0.0)
        assertEquals(500.0, db.accountDao().getAccountById("card")!!.balance, 0.0)
        db.transactionDao().deleteAllTransactions()
        assertFalse(repository.confirmFinancialOperation("salary", "default", op(FinancialOperationKind.TRANSACTION, 500.0).copy(type = "income", category = "Зарплата", cashAccountId = "card")))
        assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
    }

    @Test fun editingAndDeletingLinkedExpenseKeepCashBalanceConsistent() = runBlocking {
        cash("wallet")
        val operation = op(FinancialOperationKind.TRANSACTION).copy(cashAccountId = "wallet")
        repository.confirmFinancialOperation("expense", "default", operation)
        assertEquals(800.0, db.accountDao().getAccountById("wallet")!!.balance, 0.0)
        repository.updateTransactionDetails("expense", "default", "expense", "2026-11-13", "Продукты", "Правка", 300.0)
        assertEquals(700.0, db.accountDao().getAccountById("wallet")!!.balance, 0.0)
        repository.deleteTransaction("expense")
        assertEquals(1000.0, db.accountDao().getAccountById("wallet")!!.balance, 0.0)
        assertFalse(repository.confirmFinancialOperation("expense", "default", operation))
    }

    @Test fun backupClonePreservesCashLinkWithinNewBudget() = runBlocking {
        db.budgetProfileDao().insertProfile(BudgetProfileEntity(id = "default", name = "Budget"))
        cash("wallet")
        repository.confirmFinancialOperation("expense", "default", op(FinancialOperationKind.TRANSACTION).copy(cashAccountId = "wallet"))
        val codec = BudgetBackupCodec()
        val backup = BudgetBackup(profiles = db.budgetProfileDao().getAllProfiles().first(),
            accounts = db.accountDao().getAllAccounts().first(), transactions = db.transactionDao().getAllTransactions().first())
        val restored = codec.prepare(codec.decode(codec.encode(backup)), "cloned", "Copy")
        assertEquals(restored.accounts!!.single().id, restored.transactions!!.single().cashAccountId)
        assertEquals("cloned", restored.transactions!!.single().budgetId)
        assertNotEquals("wallet", restored.transactions!!.single().cashAccountId)
    }

    @Test fun deletingCashAccountKeepsHistoryAndDetachesItsLink() = runBlocking {
        cash("wallet")
        val operation = op(FinancialOperationKind.TRANSACTION).copy(cashAccountId = "wallet")
        repository.confirmFinancialOperation("expense", "default", operation)
        repository.deleteAccountById("wallet")
        val transaction = db.transactionDao().getAllTransactions().first().single()
        assertNull(transaction.cashAccountId)
        assertEquals(200.0, transaction.amount, 0.0)
        assertFalse(repository.confirmFinancialOperation("expense", "default", operation))
    }

    @Test fun namedCashAccountCannotBeSilentlyOmitted() = runBlocking {
        rejected("unresolved-account", op(FinancialOperationKind.TRANSACTION).copy(requiresCashAccount = true))
        assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
    }

    @Test fun invalidDateAndMissingCounterpartyDoNotCreateDebt() = runBlocking {
        rejected("no-name", op(FinancialOperationKind.REGISTER_OWES_US))
        try { repository.confirmFinancialOperation("bad-date", "default", op(FinancialOperationKind.BORROW).copy(counterparty = "Иван", date = "2026-02-30")); fail("Invalid date") }
        catch (_: java.time.DateTimeException) { }
        assertTrue(db.accountDao().getAllAccounts().first().isEmpty())
        assertNull(db.confirmedFinancialOperationDao().getById("bad-date"))
    }
}
