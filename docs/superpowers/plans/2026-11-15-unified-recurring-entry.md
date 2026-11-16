# Recurring Payments Dropdown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Оставить один **раскрываемый список** регулярных платежей со вложенным управлением.

**Architecture:** В `SafeToSpendAndRecurringDialog.kt` существующая страница обзора выводит `RecurringPaymentsExpandableCard`. Она читает `RecurringPaymentEntity`, показывает `AnimatedVisibility`, а менеджер открывается только по независимому вложенному действию. Финансовый слой не меняется.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room read-only, JUnit4, Compose UI Test.

**Spec:** `docs/superpowers/specs/2026-11-15-unified-recurring-entry-design.md`

## Global Constraints
- Никаких изменений финансовой математики, Room, подтверждений данных, Gemini и David Live.
- Не изменять `main` и PR #107. Не сливать PR #108 без проверки Android.
- Не добавлять новые параллельные сервисы и зависимости.

## Review Focus
- Нажатие на заголовок не открывает менеджер.
- Кнопка редактирования отсутствует в свёрнутом состоянии и появляется внутри раскрытия.
- Пустой список позволяет добавить запись.
- Просрочка и пауза отображаются корректно; доход не выдаётся за просроченный расход.
- На узком экране подпись не ломает расположение сумм.

---

### Task 1: Regression tests
**Files:**
- `app/src/test/java/com/example/ui/components/dialogs/RecurringPaymentsCardSummaryTest.kt`
- `app/src/androidTest/java/com/example/ui/components/dialogs/RecurringPaymentsExpandableCardUiTest.kt`

- [x] Тесты сводки написаны и изолированно прогнаны (5/5, Kotlin stubs, **не Gradle**).
- [x] Написаны Compose UI-тесты (3 сценария) на раскрытие, вложенное управление, пустой список.
- [ ] Запустить Compose UI-тесты на Android-эмуляторе; из-за отсутствующего SDK пока не подтверждено (RED/GREEN не наблюдались).

### Task 2: Interface fix
**Files:**
- `app/src/main/java/com/example/ui/components/dialogs/SafeToSpendAndRecurringDialog.kt`
- `app/src/main/java/com/example/ui/components/dialogs/RecurringPaymentsCardSummary.kt`

- [x] Оставить сводку и восстановить `AnimatedVisibility`.
- [x] Сохранить строки списка и перенести одну кнопку управления внутрь раскрываемого содержимого.
- [x] Разделить нажатия по заголовку и по внутренней кнопке, добавить testTag для UI-тестов.
- [ ] Выполнить `:app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug` и instrumented test при наличии SDK.
- [ ] Проверить на устройстве: просмотр, раскрытие/закрытие, добавление и редактирование, статус, узкий экран, офлайн.
