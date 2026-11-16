package com.example.davidapp.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.db.AccountEntity
import com.example.data.repository.FinancialOperationKind
import com.example.data.repository.FinancialSemanticsCalculator
import com.example.data.repository.ParsedVoiceOperation
import java.time.LocalDate

@Composable
internal fun DavidLiveFinancialConfirmationDialog(
    operation: ParsedVoiceOperation,
    accounts: List<AccountEntity>,
    saving: Boolean,
    error: String?,
    onChange: (ParsedVoiceOperation) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val kind = operation.financialKind
    val cash = accounts.filter { !FinancialSemanticsCalculator.isDebtAccount(it) }
    val debts = accounts.filter { if (kind.isWeOwe) FinancialSemanticsCalculator.isWeOweDebt(it) else FinancialSemanticsCalculator.isOwesUsDebt(it) }
    val valid = operation.amount.isFinite() && operation.amount > 0 &&
        runCatching { LocalDate.parse(operation.date) }.isSuccess &&
        (!operation.requiresCashAccount || cash.any { it.id == operation.cashAccountId }) &&
        (!kind.createsDebt || operation.counterparty.isNotBlank()) &&
        (!kind.repaysDebt || debts.any { it.id == operation.debtId }) &&
        (kind != FinancialOperationKind.TRANSFER ||
            (cash.any { it.id == operation.cashAccountId } && cash.any { it.id == operation.destinationAccountId } &&
                operation.cashAccountId != operation.destinationAccountId))
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (kind == FinancialOperationKind.TRANSACTION) {
            if (operation.type == "expense") "Записать расход?" else "Записать доход?"
        } else kind.label) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${operation.amount} ₽ · ${operation.category}")
                if (kind.isDeclaration) Text("Только учёт задолженности. Доход или расход не создаётся.")
                if (kind == FinancialOperationKind.TRANSFER) Text("Общий доход и расход не изменятся.")
                if (kind.createsDebt) OutlinedTextField(
                    value = operation.counterparty, onValueChange = { onChange(operation.copy(counterparty = it)) },
                    enabled = !saving, label = { Text("Имя или название долга") }, singleLine = true
                )
                OutlinedTextField(value = operation.date, onValueChange = { onChange(operation.copy(date = it)) },
                    enabled = !saving, label = { Text("Дата (ГГГГ-ММ-ДД)") }, singleLine = true)
                if (kind.repaysDebt) AccountChoice("Долг", operation.debtId, debts, false, !saving) {
                    onChange(operation.copy(debtId = it))
                }
                if (!kind.isDeclaration) AccountChoice(
                    if (kind == FinancialOperationKind.TRANSFER) "Со счёта" else "Денежный счёт",
                    operation.cashAccountId, cash, kind != FinancialOperationKind.TRANSFER && !operation.requiresCashAccount, !saving
                ) { onChange(operation.copy(cashAccountId = it)) }
                if (kind == FinancialOperationKind.TRANSFER) AccountChoice("На счёт", operation.destinationAccountId, cash, false, !saving) {
                    onChange(operation.copy(destinationAccountId = it))
                }
                if (cash.isEmpty() && kind == FinancialOperationKind.TRANSFER) Text("Сначала создайте два денежных счёта в приложении.")
                if (debts.isEmpty() && kind.repaysDebt) Text("В этом бюджете нет долгов нужного направления.")
                Text("Проверьте поля и нажмите «Записать» или скажите «подтверждаю запись».")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = valid && !saving, onClick = onConfirm) { Text(if (saving) "Сохранение…" else "Записать") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun AccountChoice(label: String, selected: String?, accounts: List<AccountEntity>, optional: Boolean, enabled: Boolean, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(enabled = enabled, onClick = { expanded = true }) {
            Text("$label: ${accounts.firstOrNull { it.id == selected }?.name ?: if (selected == null && optional) "Без счёта" else "Выберите"}")
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            if (optional) DropdownMenuItem(text = { Text("Без счёта") }, onClick = { expanded = false; onSelect(null) })
            accounts.forEach { account ->
                DropdownMenuItem(text = { Text("${account.name} · ${account.accountNumber.ifBlank { account.type }}") }, onClick = {
                    expanded = false; onSelect(account.id)
                })
            }
        }
    }
}
