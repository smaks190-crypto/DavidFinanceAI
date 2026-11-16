package com.example.davidapp.live

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.CategoryDao
import com.example.data.db.CategoryEntity
import com.example.data.repository.BudgetRepository
import com.example.davidapp.DavidChatAuditSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DavidLiveParserCancellationTest {
    @Test fun cancellationIsNotConvertedIntoAFallbackProposal() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val cancellation = CancellationException("Profile parsing cancelled")
        val categories = object : CategoryDao by db.categoryDao() {
            override fun getCategoriesByBudgetId(budgetId: String): Flow<List<CategoryEntity>> = flow { throw cancellation }
        }
        val repository = BudgetRepository(context, db.budgetProfileDao(), db.transactionDao(), db.goalDao(), categories,
            db.aiAuditDao(), db.accountDao(), db.notificationDao(), db)
        try {
            try {
                DavidChatAuditSupport.tryParseOperationFromChat("Потратил 450 рублей на продукты", repository, "default")
                fail("Cancellation must propagate")
            } catch (actual: CancellationException) {
                assertSame(cancellation, actual)
            }
        } finally { db.close() }
    }
}
