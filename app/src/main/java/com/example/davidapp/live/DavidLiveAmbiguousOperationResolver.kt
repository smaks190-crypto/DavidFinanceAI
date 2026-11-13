package com.example.davidapp.live

import com.example.data.repository.FinancialOperationKind
import com.example.data.repository.ParsedVoiceOperation
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Limits the existing income/expense AI parser to explicit recording requests. */
internal object DavidLiveAmbiguousOperationResolver {
    private val action = Regex("^(?:я )?(?:потратил[а]?|купил[а]?|оплатил[а]?|получил[а]?|заработал[а]?|запиши)(?![\\p{L}\\p{N}_])")
    private val excluded = Regex("долг|долж|погас|погаш|перев[еоё]|(?:^|[^\\p{L}])(не|если|завтра|планирую|хочу)(?![\\p{L}])")

    fun eligible(text: String): Boolean {
        val normalized = text.trim().lowercase(Locale.ROOT)
        return normalized.length in 3..1000 && '?' !in normalized &&
            action.containsMatchIn(normalized) && !excluded.containsMatchIn(normalized)
    }

    suspend fun resolve(
        text: String,
        hasKey: Boolean,
        local: suspend () -> ParsedVoiceOperation?,
        remote: suspend () -> List<ParsedVoiceOperation>
    ): ParsedVoiceOperation? {
        // Questions, plans and debt operations must not become ordinary purchases.
        val normalized = text.trim().lowercase(Locale.ROOT)
        if ('?' in normalized || excluded.containsMatchIn(normalized)) return null
        if (!eligible(text)) return local()
        val localResult = local()
        if (localResult != null) return localResult
        if (!hasKey) return null
        val results = try {
            withTimeoutOrNull(15_000) { remote() } ?: return null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        val op = results.singleOrNull() ?: return null
        if (op.financialKind != FinancialOperationKind.TRANSACTION || op.type !in setOf("income", "expense") ||
            !op.amount.isFinite() || op.amount <= 0 || op.category.isBlank() ||
            op.cashAccountId != null || op.destinationAccountId != null || op.debtId != null) return null
        if (runCatching { LocalDate.parse(op.date) }.isFailure) return null
        return op
    }
}
