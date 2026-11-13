# Period Budget Month Window — Implementation Plan

> **For agentic workers:** Use Superpowers `executing-plans` / `subagent-driven-development` for further edits. Track each test gate; do not merge without owner approval.

**Goal:** Remove duplicated calendar date logic from the Compose period screen without changing any displayed financial figures.

**Architecture:** Keep the screen's `remember` state ownership. A small pure Kotlin month-window helper provides date strings; the screen retains all transaction filtering and existing financial calculators.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4; Android Gradle `:app` with existing dependencies.

**Spec:** `docs/superpowers/specs/2026-11-13-period-budget-month-window-design.md`

## Global constraints
- Main baseline SHA: `cc3f4f85c609678385e25e0e66e4a05988894d4a`.
- Do not edit Room/database migrations, existing financial formulas, Gemini integration, animations or David Live.
- Keep month indices zero-based and previous-month rollover identical.
- Do not run or rerun GitHub Actions manually; do not merge into `main`.
- Preserve offline operation and the user-confirmation boundary for financial writes.

## Review focus
1. January of year N must compare to December of year N-1 (test `januaryUsesPreviousDecemberAcrossYearBoundary`).
2. Leap-year February must end on 29 (test `leapYearFebruaryEndsOnTwentyNinth`).
3. Normal February must end on 28 (test `nonLeapFebruaryEndsOnTwentyEighth`).
4. December must retain the same-year November comparison (test `decemberUsesNovemberWithoutChangingYear`).
5. After Compose integration, month selection should preserve existing aggregates and popup date selection (manual/on-device integration check).

---

### Task 1: Pin calendar behavior
**Files:** 
- Create `app/src/test/java/com/example/ui/screens/PeriodBudgetMonthWindowTest.kt`.
- Create `app/src/main/java/com/example/ui/screens/PeriodBudgetMonthWindow.kt`.

**Interface:** `internal fun calculatePeriodMonthWindow(selectedMonthIdx: Int, selectedYear: Int): PeriodBudgetMonthWindow`, with `start`, `end`, `previousMonthPrefix` as `String`.

- [x] Write four JUnit cases exercising year rollover, leap/non-leap February and December.
- [x] Verify red by compiling an equivalent standalone Kotlin harness without the helper: missing symbol reported.
- [x] Implement the minimal calendar helper using the pre-existing algorithm.
- [x] Compile the helper with standalone Kotlin/JVM compiler and run equivalent 4-case smoke harness successfully.
- [ ] Execute `./gradlew :app:testDebugUnitTest` in configured Android environment.

### Task 2: Replace duplication in the existing UI
**Files:**
- Modify `app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt`.

**Interface:** Existing `PeriodBudgetScreen` parameters, financial objects and UI output stay unchanged. Use `monthWindow.start`, `monthWindow.end`, and `monthWindow.previousMonthPrefix`.

- [x] Replace three calendar `remember` blocks with one `remember(selectedMonthIdx, selectedYear)`.
- [x] Substitute the same previous-month prefix in the two existing calculations.
- [ ] Run `./gradlew :app:assembleDebug` and relevant UI tests on Android device/emulator.
- [ ] Manually compare month view, prior-month comparisons, category figures and Safe-to-Spend before/after.

### Task 3: Review, risk and handoff
**Files:** this plan, spec, and the three Kotlin files above.

- [ ] Check all changed files from GitHub PR diff: no business logic changes, no unnecessary dependencies, no secrets.
- [ ] Report independent local checks distinctly from unperformed Gradle and device tests.
- [ ] Open a **draft** PR targeting `main`; leave it unmerged for review.
- [ ] For broader architecture work, continue staged audit and measurements in Issue #79 before proposing modules.

## Rollback
Revert this PR to restore the original calendar blocks. No data migration or persisted setting is involved.

---

### Task 4: Stateless month selector (second incremental pass, same PR)

**Files:**
- Modify `app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt` at the two month-arrow callbacks and the top selector.
- Modify `app/src/main/java/com/example/ui/screens/PeriodBudgetMonthWindow.kt` (pure adjacent-month function).
- Modify `app/src/test/java/com/example/ui/screens/PeriodBudgetMonthWindowTest.kt` (navigation tests).
- Create `app/src/main/java/com/example/ui/screens/PeriodBudgetMonthSelector.kt` (presentation-only Compose function).

**Interface:**
- `internal data class PeriodBudgetMonthPosition(val monthIdx: Int, val year: Int)`.
- `internal fun calculateAdjacentPeriodMonth(selectedMonthIdx: Int, selectedYear: Int, step: Int): PeriodBudgetMonthPosition`, where `step` is -1 or +1, used only by arrow clicks.
- `@Composable internal fun PeriodBudgetMonthSelector(selectedMonthIdx: Int, selectedYear: Int, onPreviousMonth: () -> Unit, onNextMonth: () -> Unit)`.

**Invariant:** The component owns no state and preserves original Box/Row modifiers, text, spacing, icons, accessibility labels, color and fonts. Parent keeps event order: optional `onChangeSelectedAnnualYear`, then `onSetPeriodType(PeriodType.MONTH)`, then `onChangeSelectedMonthIdx`. Finances, Room, Gemini and other UI remain untouched.

- [ ] **RED:** add JUnit assertions for January → December of preceding year, December → January of next year, and adjacent months without year rollover.
- [ ] Compile the pure helper/JUnit-equivalent harness without production function; expected unresolved reference, not a syntax error.
- [ ] **GREEN:** implement adjacent-month calculation matching original two conditional branches.
- [ ] Compile and run the pure Kotlin harness; require all navigation assertions and original calendar tests to pass.
- [ ] Extract only the selector markup from the large screen into a stateless sibling composable; preserve event order.
- [ ] Compare unified diff against intended selector-only changes and ensure no other screen behavior is touched.
- [ ] Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` in configured Android SDK environment (or inspect automatic CI for the precise new head SHA).
- [ ] Instrumented UI: arrow semantics, January/December year boundary, screen layout appearance, and financial totals unaffected; explicitly report any unrun checks.

**Risk:** A layout / callback event regression could remain undetected by pure Kotlin tests; it needs Compose build and on-device checks. **API cost:** zero additional Gemini calls. **Licensing:** no reference code copied.
