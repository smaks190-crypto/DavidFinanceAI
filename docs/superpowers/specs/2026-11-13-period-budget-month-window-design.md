# Period Budget month window — design (2026-11-13)

## Intent
Begin the user-approved incremental Compose refactor with a **behavior-preserving, small change** in the period screen. Avoid adding new financial services or modifying David Live while PR #100 is open.

## Observed state (main `cc3f4f85c609678385e25e0e66e4a05988894d4a`)
- `PeriodBudgetScreen.kt` computes the selected month start/end, and computes the previous-month prefix twice using the same calendar formula.
- It uses `remember(selectedMonthIdx, selectedYear)` for month boundaries and separate `remember` blocks for financial aggregates.
- Financial calculations already have separate semantic and Safe-to-Spend implementations with existing regression tests.

## Approaches
1. Move all finance calculations to a new service — rejected; not a mechanical refactor and risks duplicating the existing source of truth.
2. Create many new Compose components — deferred until profiling and UI contract tests.
3. **Selected:** extract only calendar month-boundary formatting to one pure Kotlin helper used by the existing screen.

## Design and invariants
- Add `calculatePeriodMonthWindow(selectedMonthIdx, selectedYear)` within `com.example.ui.screens`, returning `start`, `end`, `previousMonthPrefix`.
- Preserve 0-based month indexing, US numeric formatting, `Calendar.getActualMaximum`, existing `remember` keys and transaction-filtering rules.
- Keep `FinancialSemanticsCalculator`, `DavidFinancialIntelligenceProvider`, Safe-to-Spend, Room, Gemini, UI layout and API requests unchanged.
- No extra dependencies, no migration, no new network calls. For the same list and month, financial results must remain unchanged.
- An independent UI build and on-device screenshot comparison remain necessary before merging.

## Limitations
Full Android Gradle compilation, instrumentation and device profiling cannot be verified from the present environment. A standalone Kotlin/JVM smoke harness checks calendar edge cases but **does not replace** `:app:testDebugUnitTest` and `:app:assembleDebug`.

## External references and licenses
Context7 Android Compose state-hoisting guidance informed boundary selection. No third-party code copied; GPL/AGPL reference implementations were not used. Follow incremental architecture constraints in Issue #79.
