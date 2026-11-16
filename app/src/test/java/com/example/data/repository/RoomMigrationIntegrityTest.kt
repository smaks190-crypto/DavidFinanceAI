package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Opens frozen old schemas with the production migration chain and Room's schema validation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomMigrationIntegrityTest {
    @Test fun reconstructedVersion3MigratesTo7() = verifyMigration(3)
    @Test fun historicalVersion4MigratesTo7() = verifyMigration(4)
    @Test fun historicalVersion5MigratesTo7() = verifyMigration(5)

    private fun verifyMigration(version: Int) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-$version.db"
        context.deleteDatabase(name)
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
                val sql = javaClass.getResourceAsStream("/db/v$version.sql")!!.bufferedReader().use { it.readText() }
                sql.lineSequence().filterNot { it.startsWith("--") }.joinToString("\n")
                    .split(';').filter { it.isNotBlank() }.forEach { old.execSQL(it) }
                for (budget in listOf("b1", "b2")) {
                    old.execSQL("INSERT INTO budget_profiles VALUES (?, ?, 123)", arrayOf(budget, "Budget $budget"))
                    old.execSQL("INSERT INTO goals VALUES (?, ?, 'Reserve', 50000.0, 12000.25)", arrayOf("goal-$budget", budget))
                    old.execSQL("INSERT INTO ai_audits VALUES (?, ?, 'MONTH', '2026-11', 2026, 11, 'Audit', '', 123)",
                        arrayOf("audit-$budget", budget))
                    val categoryColumns = if (version >= 4) ", monthlyLimit" else ""
                    val categoryValues = if (version >= 4) ", 7000.25" else ""
                    old.execSQL("INSERT INTO categories (id, budgetId, type, name$categoryColumns) VALUES (?, ?, 'expense', 'Food'$categoryValues)",
                        arrayOf("category-$budget", budget))
                    if (version >= 4) {
                        old.execSQL("INSERT INTO accounts VALUES (?, ?, 'Card', 10000.25, 'card', '1234')",
                            arrayOf("account-$budget", budget))
                        old.execSQL("INSERT INTO accounts VALUES (?, ?, 'Loan', 300.50, 'we_owe', '')",
                            arrayOf("debt-$budget", budget))
                        old.execSQL("INSERT INTO accounts VALUES (?, ?, 'Lent', 700.25, 'owes_us', '')",
                            arrayOf("lent-$budget", budget))
                        old.execSQL("INSERT INTO notifications VALUES (?, ?, 'Reminder', 'Description', 'bell', 'indigo500', 123, 0)",
                            arrayOf("notification-$budget", budget))
                    }
                    val accountColumns = if (version >= 4) ", accountId" else ""
                    val accountValues = if (version >= 4) ", 'account-$budget'" else ""
                    val timeColumns = if (version >= 5) ", createdAt" else ""
                    val timeValues = if (version >= 5) ", 777" else ""
                    for ((type, amount) in listOf("income" to 5000.25, "expense" to 1250.50)) {
                        old.execSQL("INSERT INTO transactions (id, budgetId, type, date, category, subcategory, amount$accountColumns$timeColumns) " +
                            "VALUES (?, ?, ?, '2026-11-01', 'Food', 'Saved', ?$accountValues$timeValues)",
                            arrayOf<Any>("$type-$budget", budget, type, amount))
                    }
                }
                old.version = version
            }

            fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .allowMainThreadQueries().build()

            val migrated = open()
            try {
                val db = migrated
                // The first query forces migration and validates every table, column, index and FK.
                val transactions = db.transactionDao().getAllTransactions().first()
                assertEquals(7, db.openHelper.writableDatabase.version)
                assertEquals(4, transactions.size)
                assertEquals(setOf("b1", "b2"), db.budgetProfileDao().getAllProfiles().first().map { it.id }.toSet())
                for (budget in listOf("b1", "b2")) {
                    val rows = transactions.filter { it.budgetId == budget }
                    assertEquals(mapOf("income" to 5000.25, "expense" to 1250.50), rows.associate { it.type to it.amount })
                    assertTrue(rows.all { it.date == "2026-11-01" && it.category == "Food" && it.subcategory == "Saved" })
                    val accountId = if (version == 3) "default_acc_$budget" else "account-$budget"
                    assertTrue(rows.all { it.accountId == accountId })
                    assertTrue(rows.all { if (version == 5) it.createdAt == 777L else it.createdAt > 0 })
                    val account = db.accountDao().getAccountById(accountId)!!
                    assertEquals(budget, account.budgetId)
                    assertEquals(if (version == 3) 0.0 else 10000.25, account.balance, 0.0)
                    if (version >= 4) {
                        val debt = db.accountDao().getAccountById("debt-$budget")!!
                        assertEquals(budget, debt.budgetId)
                        assertEquals("we_owe", debt.type)
                        assertEquals(300.50, debt.balance, 0.0)
                        val lent = db.accountDao().getAccountById("lent-$budget")!!
                        assertEquals(budget, lent.budgetId)
                        assertEquals("owes_us", lent.type)
                        assertEquals(700.25, lent.balance, 0.0)
                        val reminder = db.notificationDao().getNotificationsByBudgetId(budget).first().single()
                        assertEquals("Reminder", reminder.title)
                        assertFalse(reminder.isRead)
                    }
                    db.openHelper.writableDatabase.query("SELECT currentAmount FROM goals WHERE budgetId = ?", arrayOf(budget)).use {
                        assertTrue(it.moveToFirst()); assertEquals(12000.25, it.getDouble(0), 0.0)
                    }
                    db.openHelper.writableDatabase.query("SELECT monthlyLimit FROM categories WHERE budgetId = ?", arrayOf(budget)).use {
                        assertTrue(it.moveToFirst())
                        if (version == 3) assertTrue(it.isNull(0)) else assertEquals(7000.25, it.getDouble(0), 0.0)
                    }
                    db.openHelper.writableDatabase.query("SELECT auditText FROM ai_audits WHERE budgetId = ?", arrayOf(budget)).use {
                        assertTrue(it.moveToFirst()); assertEquals("Audit", it.getString(0))
                    }
                }
                assertTrue(db.recurringPaymentDao().getAllRecurringPayments().first().isEmpty())
                assertTrue(db.recurringPaymentDao().getAllExecutionLogs().first().isEmpty())
                // New v6 tables must be writable and keep transaction links across reopening.
                db.recurringPaymentDao().insertRecurringPayment(RecurringPaymentEntity(id = 41, budgetId = "b1",
                    title = "Rent", amount = 1250.50, categoryName = "Food", nextDueDateEpochDay = 20789))
                db.recurringPaymentDao().insertExecutionLog(RecurringExecutionLogEntity(id = 51, budgetId = "b1",
                    recurringId = 41, scheduledEpochDay = 20758, status = "PAID", linkedTransactionId = "expense-b1"))
            } finally { migrated.close() }
            val reopened = open()
            try {
                val db = reopened
                assertEquals(4, db.transactionDao().getAllTransactions().first().size)
                assertEquals("expense-b1", db.recurringPaymentDao().getExecutionLogBySchedule(41, 20758)!!.linkedTransactionId)
                db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                db.openHelper.writableDatabase.query("PRAGMA integrity_check").use {
                    assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0))
                }
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }
}
