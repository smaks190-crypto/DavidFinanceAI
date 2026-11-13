package com.example.davidapp.live

import com.example.data.db.AccountEntity
import com.example.data.repository.FinancialOperationKind
import com.example.data.repository.FinancialSemanticsCalculator
import com.example.data.repository.ParsedVoiceOperation
import java.time.LocalDate
import java.util.Locale

/** Conservative local routing. Ambiguous money movements never fall through to an expense. */
internal object DavidLiveFinancialOperationParser {
    sealed interface Result {
        data object Ordinary : Result
        data class Draft(val operation: ParsedVoiceOperation) : Result
        data class Clarify(val message: String) : Result
    }

    private val markers = Regex("(?U)долг|долж|погас|погаш|перев[еоё]", RegexOption.IGNORE_CASE)
    private val amount = Regex("(?U)(?<![\\d.,-])\\d+(?:[.,]\\d{1,2})?(?:\\s*(?:тысяч[аи]?|тыс\\.?|к)(?![а-я]))?", RegexOption.IGNORE_CASE)

    fun parse(text: String, today: LocalDate = LocalDate.now()): Result {
        val lower = text.lowercase(Locale.ROOT).trim().trimEnd('.', '!')
        if (!markers.containsMatchIn(lower)) return Result.Ordinary
        val clarify = Result.Clarify("Уточните операцию: направление долга или перевода, одну сумму и дату. Ничего не записано.")
        if (lower.contains('?') || Regex("(?U)\\b(если|планирую|хочу|буду|завтра|сколько|не)\\b").containsMatchIn(lower)) return clarify
        val routes = listOf(
            Regex("(?U)^(?:я )?(?:погасил|погасила|вернул|вернула) (?:свой )?долг\\b") to FinancialOperationKind.REPAY_WE_OWE,
            Regex("(?U)^мне (?:вернули|вернул|вернула) долг\\b") to FinancialOperationKind.REPAY_OWES_US,
            Regex("(?U)^(?:я )?(?:взял|взяла) в долг\\b") to FinancialOperationKind.BORROW,
            Regex("(?U)^(?:я )?(?:дал|дала) в долг\\b") to FinancialOperationKind.LEND,
            Regex("(?U)^я (?:должен|должна)\\b") to FinancialOperationKind.REGISTER_WE_OWE,
            Regex("(?U)^мне должны\\b") to FinancialOperationKind.REGISTER_OWES_US,
            Regex("(?U)^(?:я )?(?:перев[её]л|перевела|перевести|переведи)\\b") to FinancialOperationKind.TRANSFER
        )
        val route = routes.firstOrNull { it.first.containsMatchIn(lower) } ?: return clarify
        var remainder = lower.replaceFirst(route.first, "").trim()
        var date = today
        val explicitDate = Regex("(?U)\\b\\d{4}-\\d{2}-\\d{2}\\b").find(remainder)
        if (explicitDate != null) {
            date = runCatching { LocalDate.parse(explicitDate.value) }.getOrNull() ?: return clarify
            remainder = remainder.replace(explicitDate.value, "")
        } else if (remainder.contains("позавчера")) {
            date = today.minusDays(2); remainder = remainder.replace("позавчера", "")
        } else if (remainder.contains("вчера")) {
            date = today.minusDays(1); remainder = remainder.replace("вчера", "")
        }
        remainder = remainder.replace("сегодня", "")
        val matches = amount.findAll(remainder).toList()
        if (matches.size != 1 || remainder.contains('-')) return clarify
        val match = matches.single()
        val number = Regex("(?U)\\d+(?:[.,]\\d{1,2})?").find(match.value)!!.value
        val multiplier = if (match.value.trim() != number) 1000 else 1
        val value = (number.replace(',', '.').toDoubleOrNull() ?: return clarify) * multiplier
        if (!value.isFinite() || value <= 0) return clarify
        val kind = route.second
        if (kind.isDeclaration && Regex("(?U)(?:со? сч[её]та|с карты|на сч[её]т|на карту)").containsMatchIn(remainder)) return clarify
        val name = if (kind.createsDebt) remainder.removeRange(match.range)
            .replace(Regex("(?U)\\b(?:рублей|рубля|рубль|руб|р)\\b|₽"), "")
            .replace(Regex("(?U)(?:со? сч[её]та|с карты|на сч[её]т|на карту).*$"), "")
            .replace(Regex("(?U)^\\s*у\\s+"), "").trim(' ', ',', '.', ':') else ""
        return Result.Draft(ParsedVoiceOperation(
            title = text, type = if (kind in setOf(FinancialOperationKind.BORROW, FinancialOperationKind.REPAY_OWES_US)) "income" else "expense",
            amount = value, category = if (kind == FinancialOperationKind.TRANSFER) "Перевод" else "Долги",
            subcategory = kind.label, date = date.toString(), financialKind = kind, counterparty = name
        ))
    }

    fun resolveAccounts(operation: ParsedVoiceOperation, text: String, accounts: List<AccountEntity>): ParsedVoiceOperation {
        val cash = accounts.filter { !FinancialSemanticsCalculator.isDebtAccount(it) }
        fun exactNamed(candidates: List<AccountEntity>, fragment: String): String? = candidates
            .filter { it.name.isNotBlank() && fragment.trim().equals(it.name.trim(), ignoreCase = true) }
            .singleOrNull()?.id
        if (operation.financialKind == FinancialOperationKind.TRANSFER) {
            val route = Regex("(?U)(?:со? сч[её]та|с карты)\\s+(.+?)\\s+на (?:сч[её]т|карту)\\s+(.+?)(?:\\s+\\d|$)", RegexOption.IGNORE_CASE).find(text)
            return operation.copy(cashAccountId = route?.let { exactNamed(cash, it.groupValues[1]) },
                destinationAccountId = route?.let { exactNamed(cash, it.groupValues[2].trimEnd('.', '!')) })
        }
        val cashMention = Regex("(?U)(?:со? сч[её]та|с карты|на сч[её]т|на карту)\\s+(.+?)(?:\\s+\\d|$)", RegexOption.IGNORE_CASE).find(text)
        val resolved = operation.copy(cashAccountId = cashMention?.let { exactNamed(cash, it.groupValues[1].trimEnd('.', '!')) },
            requiresCashAccount = cashMention != null)
        if (operation.financialKind.repaysDebt) {
            val candidates = accounts.filter { if (operation.financialKind.isWeOwe) FinancialSemanticsCalculator.isWeOweDebt(it) else FinancialSemanticsCalculator.isOwesUsDebt(it) }
            val named = candidates.filter { it.name.isNotBlank() && Regex("(?U)(?<![\\p{L}\\p{N}])${Regex.escape(it.name)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(text) }
            return resolved.copy(debtId = named.singleOrNull()?.id)
        }
        return resolved
    }
}
