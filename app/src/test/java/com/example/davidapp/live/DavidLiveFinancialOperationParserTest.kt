package com.example.davidapp.live

import com.example.data.db.AccountEntity
import com.example.data.repository.FinancialOperationKind
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DavidLiveFinancialOperationParserTest {
    private val today = LocalDate.of(2026, 11, 13)
    private fun draft(text: String) = (DavidLiveFinancialOperationParser.parse(text, today) as DavidLiveFinancialOperationParser.Result.Draft).operation
    @Test fun routesAllDebtDirectionsAndTransfers() {
        val cases = mapOf(
            "Я должен Ивану 500 рублей" to FinancialOperationKind.REGISTER_WE_OWE,
            "Мне должны Иван 500 рублей" to FinancialOperationKind.REGISTER_OWES_US,
            "Взял в долг у Ивана 500 рублей" to FinancialOperationKind.BORROW,
            "Дал в долг Ивану 500 рублей" to FinancialOperationKind.LEND,
            "Вернул долг Ивану 500 рублей" to FinancialOperationKind.REPAY_WE_OWE,
            "Мне вернули долг 500 рублей" to FinancialOperationKind.REPAY_OWES_US,
            "Переведи 500 рублей со счета Карта на счет Наличные" to FinancialOperationKind.TRANSFER
        )
        cases.forEach { (text, kind) -> assertEquals(kind, draft(text).financialKind) }
        assertEquals("ивану", draft("Я должен Ивану 500 рублей").counterparty)
    }
    @Test fun dateDecimalAndThousandsAreUnambiguous() {
        assertEquals(1250.0, draft("Взял в долг у Ивана 1,25 тыс рублей позавчера").amount, 0.0)
        assertEquals("2026-11-11", draft("Взял в долг у Ивана 500 рублей позавчера").date)
        assertEquals("2026-10-01", draft("Я должен Ивану 500 рублей 2026-10-01").date)
    }
    @Test fun ambiguousRequestsNeverBecomeOrdinaryExpenses() {
        listOf("Перевод 500 рублей", "Я должен Ивану -500 рублей", "Я должен Ивану 500 и Петру 600", "Я должен Ивану 500 рублей 2026-02-30",
            "Если я погасил долг 500 рублей", "Не вернул долг 500 рублей", "Сколько я должен 500 рублей?", "Завтра верну долг 500 рублей")
            .forEach { assertTrue(it, DavidLiveFinancialOperationParser.parse(it, today) is DavidLiveFinancialOperationParser.Result.Clarify) }
    }
    @Test fun transfersResolveOnlyExplicitUniqueAccountNames() {
        val accounts = listOf(AccountEntity(id = "a", name = "Карта"), AccountEntity(id = "b", name = "Наличные"))
        val text = "Переведи 500 рублей со счета Карта на счет Наличные"
        val resolved = DavidLiveFinancialOperationParser.resolveAccounts(draft(text), text, accounts)
        assertEquals("a", resolved.cashAccountId)
        assertEquals("b", resolved.destinationAccountId)
        val ambiguous = DavidLiveFinancialOperationParser.resolveAccounts(draft(text), text, accounts + AccountEntity(id = "c", name = "Карта"))
        assertNull(ambiguous.cashAccountId)
    }
    @Test fun accountMentionRequiresSelectionAndDebtNamesAreNotMatchedBySubstring() {
        val ordinary = com.example.data.repository.ParsedVoiceOperation("Зарплата", "income", 500.0, "Зарплата")
        val accounts = listOf(AccountEntity(id = "card", name = "Карта"), AccountEntity(id = "ivan", name = "Иван", type = "we_owe"))
        val resolved = DavidLiveFinancialOperationParser.resolveAccounts(ordinary, "Пришло 500 рублей на счет Карта", accounts)
        assertEquals("card", resolved.cashAccountId)
        assertTrue(resolved.requiresCashAccount)
        val unknown = DavidLiveFinancialOperationParser.resolveAccounts(ordinary, "Пришло 500 рублей на счет Другой", accounts)
        assertNull(unknown.cashAccountId)
        assertTrue(unknown.requiresCashAccount)
        val repayment = draft("Вернул долг Иванову 500 рублей")
        assertNull(DavidLiveFinancialOperationParser.resolveAccounts(repayment, repayment.title, accounts).debtId)
    }

    @Test fun ordinaryExpenseStillUsesExistingParser() {
        assertEquals(DavidLiveFinancialOperationParser.Result.Ordinary, DavidLiveFinancialOperationParser.parse("Потратил 450 рублей на продукты", today))
    }
    @Test fun unicodeWordEdgesPreserveRussianWordsAndRejectNegation() {
        assertEquals("нелли", draft("Я должен Нелли 500 рублей").counterparty)
        assertEquals("рублейкин", draft("Я должен Рублейкин 500 рублей").counterparty)
        listOf("Я долженник 500 рублей", "Я должен Ивану не 500 рублей", "Я должен Ивану завтра 500 рублей")
            .forEach { assertTrue(it, DavidLiveFinancialOperationParser.parse(it, today) is DavidLiveFinancialOperationParser.Result.Clarify) }
        assertEquals(DavidLiveFinancialOperationParser.Result.Ordinary, DavidLiveFinancialOperationParser.parse("", today))
    }

}
