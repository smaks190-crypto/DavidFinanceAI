package com.example.davidapp.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.db.AccountEntity
import com.example.data.repository.FinancialOperationKind
import com.example.data.repository.ParsedVoiceOperation

@Composable
internal fun DavidLiveOperationCard(operation: ParsedVoiceOperation, accounts: List<AccountEntity>, saving: Boolean,
    error: String?, onConfirm: () -> Unit, onReject: () -> Unit) {
    val kind = operation.financialKind
    fun name(id: String?) = accounts.firstOrNull { it.id == id }?.name
    val complete = (!kind.createsDebt || operation.counterparty.isNotBlank()) &&
        (!kind.repaysDebt || name(operation.debtId) != null) &&
        (!operation.requiresCashAccount || name(operation.cashAccountId) != null) &&
        (kind != FinancialOperationKind.TRANSFER || (name(operation.cashAccountId) != null &&
            name(operation.destinationAccountId) != null && operation.cashAccountId != operation.destinationAccountId))
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(20.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (kind == FinancialOperationKind.TRANSACTION) {
                    if (operation.type == "expense") "Записать расход?" else "Записать доход?"
                } else kind.label, style = MaterialTheme.typography.titleLarge)
                Text("${operation.amount} ₽", style = MaterialTheme.typography.headlineMedium)
                Text("${operation.category} · ${operation.date}")
                if (operation.counterparty.isNotBlank()) Text(operation.counterparty)
                name(operation.debtId)?.let { Text("Долг: $it") }
                name(operation.cashAccountId)?.let { Text("Счёт: $it") }
                name(operation.destinationAccountId)?.let { Text("На счёт: $it") }
                if (!complete) Text("Не хватает данных. Нажмите «Нет» и назовите операцию, имя и счета точнее.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(enabled = !saving, onClick = onReject) { Text("Нет") }
                Button(enabled = complete && !saving, onClick = onConfirm) { Text(if (saving) "Сохраняю…" else "Да") }
            }
        }
    }
}
