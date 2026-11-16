package com.example.davidapp.live

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.db.AccountEntity
import com.example.data.repository.FinancialOperationKind
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Runs with Android's ICU regex engine, not the host JVM implementation used by local tests. */
@RunWith(AndroidJUnit4::class)
class DavidLiveFinancialParserAndroidTest {
    @Test fun initializesAndParsesEveryFinancialRouteOnAndroid() {
        val today = LocalDate.of(2026, 11, 13)
        val cases = mapOf(
            "Я должен Ивану 500 рублей 2026-11-12" to FinancialOperationKind.REGISTER_WE_OWE,
            "Мне должны Иван 500 рублей" to FinancialOperationKind.REGISTER_OWES_US,
            "Взял в долг у Ивана 1,25 тыс рублей позавчера" to FinancialOperationKind.BORROW,
            "Дал в долг Ивану 500 рублей" to FinancialOperationKind.LEND,
            "Вернул долг Иван 500 рублей" to FinancialOperationKind.REPAY_WE_OWE,
            "Мне вернули долг 500 рублей" to FinancialOperationKind.REPAY_OWES_US,
            "Переведи 500 рублей со счета Карта на счет Наличные" to FinancialOperationKind.TRANSFER
        )
        val accounts = listOf(AccountEntity(id = "card", name = "Карта"),
            AccountEntity(id = "cash", name = "Наличные", type = "cash"),
            AccountEntity(id = "debt", name = "Иван", type = "we_owe"))
        cases.forEach { (text, kind) ->
            val result = DavidLiveFinancialOperationParser.parse(text, today)
            assertTrue(text, result is DavidLiveFinancialOperationParser.Result.Draft)
            val operation = (result as DavidLiveFinancialOperationParser.Result.Draft).operation
            assertEquals(kind, operation.financialKind)
            val resolved = DavidLiveFinancialOperationParser.resolveAccounts(operation, text, accounts)
            if (kind == FinancialOperationKind.TRANSFER) {
                assertEquals("card", resolved.cashAccountId)
                assertEquals("cash", resolved.destinationAccountId)
            }
            if (kind == FinancialOperationKind.REPAY_WE_OWE) assertEquals("debt", resolved.debtId)
        }
        assertTrue(DavidLiveFinancialOperationParser.parse("Я должен Ивану не 500 рублей", today)
            is DavidLiveFinancialOperationParser.Result.Clarify)
    }
}
