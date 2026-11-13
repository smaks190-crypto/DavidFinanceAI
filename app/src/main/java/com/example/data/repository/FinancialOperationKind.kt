package com.example.data.repository

enum class FinancialOperationKind(val label: String) {
    TRANSACTION("Операция"),
    REGISTER_WE_OWE("Я должен — без движения денег"),
    REGISTER_OWES_US("Мне должны — без движения денег"),
    BORROW("Взял в долг"),
    LEND("Дал в долг"),
    REPAY_WE_OWE("Погашение моего долга"),
    REPAY_OWES_US("Мне вернули долг"),
    TRANSFER("Перевод между счетами");

    val createsDebt: Boolean get() = this in setOf(REGISTER_WE_OWE, REGISTER_OWES_US, BORROW, LEND)
    val repaysDebt: Boolean get() = this == REPAY_WE_OWE || this == REPAY_OWES_US
    val isDeclaration: Boolean get() = this == REGISTER_WE_OWE || this == REGISTER_OWES_US
    val isWeOwe: Boolean get() = this in setOf(REGISTER_WE_OWE, BORROW, REPAY_WE_OWE)
}
