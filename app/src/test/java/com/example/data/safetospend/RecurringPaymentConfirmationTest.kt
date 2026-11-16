package com.example.data.safetospend

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.BudgetProfileEntity
import com.example.data.db.RecurringPaymentEntity
import com.example.data.repository.BudgetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecurringPaymentConfirmationTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: BudgetRepository
    private val due = 20_600L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = BudgetRepository(context, db.budgetProfileDao(), db.transactionDao(), db.goalDao(),
            db.categoryDao(), db.aiAuditDao(), db.accountDao(), db.notificationDao(), db)
        db.budgetProfileDao().insertProfile(BudgetProfileEntity(id = "b1", name = "Budget"))
        db.recurringPaymentDao().insertRecurringPayment(RecurringPaymentEntity(
            id = 41, budgetId = "b1", title = "Rent", amount = 500.0,
            categoryName = "Home", nextDueDateEpochDay = due))
        Unit
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun repeatedConfirmationDoesNotChangeTransactionLogOrSchedule() = runBlocking {
        val first = repository.confirmRecurringPaymentExecution(41, due)!!
        val dao = db.recurringPaymentDao()
        val payment = dao.getRecurringPaymentById(41)!!
        val log = dao.getExecutionLogBySchedule(41, due)!!
        assertTrue(payment.nextDueDateEpochDay > due)
        assertNull(repository.confirmRecurringPaymentExecution(41, due, due + 1, 999.0))
        assertEquals(listOf(first), db.transactionDao().getAllTransactions().first())
        assertEquals(listOf(log), dao.getAllExecutionLogs().first())
        assertEquals(payment, dao.getRecurringPaymentById(41))
    }

    @Test
    fun concurrentConfirmationsCreateOnlyOneFinancialOperation() = runBlocking {
        val results = List(8) {
            async(Dispatchers.Default) { repository.confirmRecurringPaymentExecution(41, due) }
        }.awaitAll()
        assertEquals(1, results.count { it != null })
        assertEquals(1, db.transactionDao().getAllTransactions().first().size)
        assertEquals(1, db.recurringPaymentDao().getAllExecutionLogs().first().size)
        val expected = RecurringScheduleCalculator.computeNextDueDate(due, PaymentFrequency.MONTHLY,
            anchorDayOfMonth = null, anchorDayOfWeek = null, customIntervalDays = null)
        assertEquals(expected, db.recurringPaymentDao().getRecurringPaymentById(41)!!.nextDueDateEpochDay)
    }

    @Test
    fun payingNextOccurrencePreservesHistoryAndOldOccurrenceRemainsIdempotent() = runBlocking {
        val first = repository.confirmRecurringPaymentExecution(41, due)!!
        val dao = db.recurringPaymentDao()
        val firstLog = dao.getExecutionLogBySchedule(41, due)!!
        val next = dao.getRecurringPaymentById(41)!!.nextDueDateEpochDay
        val second = repository.confirmRecurringPaymentExecution(41, next)!!
        assertNull(repository.confirmRecurringPaymentExecution(41, due))
        assertEquals(setOf(first.id, second.id), db.transactionDao().getAllTransactions().first().map { it.id }.toSet())
        assertEquals(2, dao.getAllExecutionLogs().first().size)
        assertEquals(firstLog, dao.getExecutionLogBySchedule(41, due))
    }

    @Test
    fun failedLogWriteRollsBackTransactionAndSchedule() = runBlocking {
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_confirmation BEFORE INSERT ON recurring_execution_logs
            BEGIN SELECT RAISE(ABORT, 'Injected failure'); END
        """.trimIndent())
        try {
            repository.confirmRecurringPaymentExecution(41, due)
            fail("Expected log write failure")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
        assertTrue(db.recurringPaymentDao().getAllExecutionLogs().first().isEmpty())
        assertEquals(due, db.recurringPaymentDao().getRecurringPaymentById(41)!!.nextDueDateEpochDay)
    }

    @Test
    fun deletedTransactionCanBeConfirmedAgainWithoutDuplicatingHistory() = runBlocking {
        val original = repository.confirmRecurringPaymentExecution(41, due)!!
        val dao = db.recurringPaymentDao()
        val next = dao.getRecurringPaymentById(41)!!.nextDueDateEpochDay
        val second = repository.confirmRecurringPaymentExecution(41, next)!!
        val secondLog = dao.getExecutionLogBySchedule(41, next)
        repository.deleteTransaction(original.id)
        assertEquals(secondLog, dao.getExecutionLogBySchedule(41, next))
        val replacement = repository.confirmRecurringPaymentExecution(41, due)!!
        assertNotEquals(original.id, replacement.id)
        assertEquals(setOf(replacement.id, second.id), db.transactionDao().getAllTransactions().first().map { it.id }.toSet())
        assertEquals(2, dao.getAllExecutionLogs().first().size)
        assertEquals(replacement.id, dao.getExecutionLogBySchedule(41, due)!!.linkedTransactionId)
    }
}
