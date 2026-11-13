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

    // Android ICU does not accept Java's (?U) flag. Explicit Unicode word edges
    // also avoid the differing meaning of \b on Android and recent JVMs.
    private const val WORD_START = "(?<![\\p{L}\\p{M}\\p{N}_])"
    private const val WORD_END = "(?![\\p{L}\\p{M}\\p{N}_])"

    private val markers = Regex("долг|долж|погас|погаш|перев[еоё]", RegexOption.IGNORE_CASE)
    private val amount = Regex("(?<![0-9.,-])[0-9]+(?:[.,][0-9]{1,2})?(?:\\s*(?:тысяч[аи]?|тыс\\.?|к)(?![а-я]))?", RegexOption.IGNORE_CASE)

    fun parse(text: String, today: LocalDate = LocalDate.now()): Result {
        val lower = text.lowercase(Locale.ROOT).trim().trimEnd('.', '!')
        if (!markers.containsMatchIn(lower)) return Result.Ordinary
        val clarify = Result.Clarify("Уточните операцию: направление долга или перевода, одну сумму и дату. Ничего не записано.")
        if (lower.contains('?') || Regex("${WORD_START}(если|планирую|хочу|буду|завтра|сколько|не)${WORD_END}").containsMatchIn(lower)) return clarify
        val routes = listOf(
            Regex("^(?:я )?(?:погасил|погасила|вернул|вернула) (?:свой )?долг${WORD_END}") to FinancialOperationKind.REPAY_WE_OWE,
            Regex("^мне (?:вернули|вернул|вернула) долг${WORD_END}") to FinancialOperationKind.REPAY_OWES_US,
            Regex("^(?:я )?(?:взял|взяла) в долг${WORD_END}") to FinancialOperationKind.BORROW,
            Regex("^(?:я )?(?:дал|дала) в долг${WORD_END}") to FinancialOperationKind.LEND,
            Regex("^я (?:должен|должна)${WORD_END}") to FinancialOperationKind.REGISTER_WE_OWE,
            Regex("^мне должны${WORD_END}") to FinancialOperationKind.REGISTER_OWES_US,
            Regex("^(?:я )?(?:перев[её]л|перевела|перевести|переведи)${WORD_END}") to FinancialOperationKind.TRANSFER
        )
        val route = routes.firstOrNull { it.first.containsMatchIn(lower) } ?: return clarify
        var remainder = lower.replaceFirst(route.first, "").trim()
        var date = today
        val explicitDate = Regex("${WORD_START}[0-9]{4}-[0-9]{2}-[0-9]{2}${WORD_END}").find(remainder)
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
        val number = Regex("[0-9]+(?:[.,][0-9]{1,2})?").find(match.value)?.value ?: return clarify
        val multiplier = if (match.value.trim() != number) 1000 else 1
        val value = (number.replace(',', '.').toDoubleOrNull() ?: return clarify) * multiplier
        if (!value.isFinite() || value <= 0) return clarify
        val kind = route.second
        if (kind.isDeclaration && Regex("(?:со? сч[её]та|с карты|на сч[её]т|на карту)").containsMatchIn(remainder)) return clarify
        val name = if (kind.createsDebt) remainder.removeRange(match.range)
            .replace(Regex("${WORD_START}(?:рублей|рубля|рубль|руб|р)${WORD_END}|₽"), "")
            .replace(Regex("(?:со? сч[её]та|с карты|на сч[её]т|на карту).*$"), "")
            .replace(Regex("^\\s*у\\s+"), "").trim(' ', ',', '.', ':') else ""
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
            val route = Regex("(?:со? сч[её]та|с карты)\\s+(.+?)\\s+на (?:сч[её]т|карту)\\s+(.+?)(?:\\s+[0-9]|$)", RegexOption.IGNORE_CASE).find(text)
            return operation.copy(cashAccountId = route?.let { exactNamed(cash, it.groupValues[1]) },
                destinationAccountId = route?.let { exactNamed(cash, it.groupValues[2].trimEnd('.', '!')) })
        }
        val cashMention = Regex("(?:со? сч[её]та|с карты|на сч[её]т|на карту)\\s+(.+?)(?:\\s+[0-9]|$)", RegexOption.IGNORE_CASE).find(text)
        val resolved = operation.copy(cashAccountId = cashMention?.let { exactNamed(cash, it.groupValues[1].trimEnd('.', '!')) },
            requiresCashAccount = cashMention != null)
        if (operation.financialKind.repaysDebt) {
            val candidates = accounts.filter { if (operation.financialKind.isWeOwe) FinancialSemanticsCalculator.isWeOweDebt(it) else FinancialSemanticsCalculator.isOwesUsDebt(it) }
            val named = candidates.filter { it.name.isNotBlank() && Regex("${WORD_START}${Regex.escape(it.name)}${WORD_END}", RegexOption.IGNORE_CASE).containsMatchIn(text) }
            return resolved.copy(debtId = named.singleOrNull()?.id)
        }
        return resolved
    }
}
