# Unified Recurring Payments Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Заменить два входа в управление регулярными платежами одной информативной карточкой.

**Architecture:** Существующий `SafeToSpendAndRecurringDialog` сохраняет навигацию `RECURRING_MANAGER`. Вычисление краткой сводки вынесено в чистую функцию над текущим списком `RecurringPaymentEntity`.

**Tech Stack:** Kotlin, Jetpack Compose, Room entity read-only, JUnit4.

**Spec:** `docs/superpowers/specs/2026-11-15-unified-recurring-entry-design.md`

## Global Constraints
- Не менять SafeToSpendCalculator, Room, миграции или repository.
- Не добавлять внешних вызовов, зависимостей, новых менеджеров или менять характер/цвета приложения.
- `main` и открытый PR #107 не редактировать; работать в отдельной ветке.

## Review Focus
- Пустой список; только пауза; доход без расхода; просроченный расход; узкий экран.

---

### Task 1: Чистая сводка карточки
**Files:**
- Create: `app/src/main/java/com/example/ui/components/dialogs/RecurringPaymentsCardSummary.kt`
- Test: `app/src/test/java/com/example/ui/components/dialogs/RecurringPaymentsCardSummaryTest.kt`

- [ ] Написать тесты: пусто, активные расходы/доходы, PAUSED/ARCHIVED, просрочка только расхода.
- [ ] Убедиться, что тесты падают до добавления функции (RED).
- [ ] Реализовать `buildRecurringPaymentsCardSummary` без эффектов и запросов к БД.
- [ ] Прогнать тесты и проверить результат (GREEN).

### Task 2: Одна точка входа в UI
**Files:**
- Modify: `app/src/main/java/com/example/ui/components/dialogs/SafeToSpendAndRecurringDialog.kt`

- [ ] Убрать состояние раскрытия, preview и кнопку «Управление платежами».
- [ ] На нажатие карточки назначить `onOpenRecurringManager`; отображать сводку, стрелку вправо.
- [ ] Сверить diff, ссылки на функцию, количество управляющих кликов, отсутствие новых side effects.
- [ ] Запустить `:app:assembleDebug`, `:app:testDebugUnitTest` если доступен Android SDK; иначе честно зафиксировать ограничение.
