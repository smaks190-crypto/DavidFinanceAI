package com.example.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.MIGRATION_6_7
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinancialOperationMigrationTest {
    @Test fun versionSixDataSurvivesAndRoomValidatesVersionSevenSchema() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val current = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val statements = mutableListOf<String>()
        current.openHelper.writableDatabase.query("SELECT sql FROM sqlite_master WHERE type IN ('table','index') AND sql IS NOT NULL AND name NOT IN ('android_metadata','sqlite_sequence','room_master_table','confirmed_financial_operations')").use { cursor ->
            while (cursor.moveToNext()) {
                statements += cursor.getString(0).replace(Regex(",\\s*`cashAccountId` TEXT"), "")
            }
        }
        current.close()
        val name = "live-finance-migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            statements.forEach { old.execSQL(it) }
            old.execSQL("INSERT INTO budget_profiles (id,name,createdAt) VALUES ('default','Budget',1)")
            old.execSQL("INSERT INTO accounts (id,budgetId,name,balance,type,accountNumber) VALUES ('debt','default','Иван',500,'we_owe','')")
            old.execSQL("INSERT INTO transactions (id,budgetId,accountId,type,date,category,subcategory,amount,createdAt) VALUES ('old','default','debt','expense','2026-11-13','Долги','Гашение',100,1)")
            old.version = 6
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().addMigrations(MIGRATION_6_7).build()
        try {
            val transaction = migrated.transactionDao().getAllTransactions().first().single()
            assertEquals("old", transaction.id)
            assertEquals("debt", transaction.accountId)
            assertNull(transaction.cashAccountId)
            assertEquals(500.0, migrated.accountDao().getAccountById("debt")!!.balance, 0.0)
            assertNull(migrated.confirmedFinancialOperationDao().getById("unused"))
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
