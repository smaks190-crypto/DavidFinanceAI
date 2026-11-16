# Inline manual date picker — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use Superpowers executing-plans. Check current GitHub PR diff and CI results; no merge without approval.

**Goal:** Transform manual input content into a calendar in the same window, with no extra dialog.
**Architecture:** Extract existing calendar UI to SingleDatePickerContent, keep SingleDatePickerDialog as wrapper, animate only VoiceManualInputPane. Draft state remains outside AnimatedContent.
**Tech Stack:** Kotlin, Jetpack Compose AnimatedContent, SizeTransform, AndroidX Compose UI tests.
**Spec:** docs/superpowers/specs/2026-11-13-inline-manual-date-picker-design.md

## Global Constraints
- Work only on draft PR #107, branch refactor/period-budget-month-window-20261113.
- Do not alter addTransaction confirmation, Room, Safe-to-Spend, AI routing, API usage or voice/sphere.
- Ordinary AddTransactionDialog keeps the full-screen date picker and its behavior.

## Review Focus
- Cancel calendar leaves amount, category, description and date untouched.
- Confirm calendar updates ONLY date; no transaction saved.
- Android Back cancels calendar before closing manual form.
- Keyboard/small screen and rapid navigation do not overlay two windows.
- Other date picker callers are unchanged, including dismiss behavior.

## Task 1: Fix existing build
**File:** app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt
- [x] GitHub Actions logs from 43477045985 / 43477134989: unresolved KeyboardArrowRight at line 822 after selector import removal.
- [x] Restore missing import, verified GitHub Actions Build Android APK & AAB success for SHA 06383054a90c7dc15f54ed9c5f302b639c97e1c1.

## Task 2: Reuse existing calendar UI
**Files:** app/src/main/java/com/example/ui/components/SingleDatePickerDialog.kt, app/src/androidTest/java/com/example/ui/components/SingleDatePickerContentUiTest.kt
- [ ] Add tests: confirm inline date without dismissing parent and cancel without saving.
- [ ] Verify failing tests before feature (requires Android SDK; unavailable locally).
- [ ] Extract shared UI while preserving old dialog wrapper.
- [ ] Run Compose instrumentation on configured Android emulator and document result.

## Task 3: Animate manual input only
**File:** app/src/main/java/com/example/ui/components/VoiceManualInputPane.kt
- [ ] Keep draft state outside AnimatedContent, use BackHandler, hide keyboard.
- [ ] Show reused calendar inline in the existing window and return to form after confirm/cancel.
- [ ] Verify no unwanted files touched, no network use or financial changes.
- [ ] Run Gradle :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin (automatic CI or local).
- [ ] On device: compare same-window animation, cancel/confirm, system back, small screen, off-line, normal AddTransactionDialog.

**Status:** Android/Compose tests for the new feature pending. Keep PR draft, do not merge.

## Stage 4: Day selection contract & accessibility (same PR)

**Files:** `app/src/main/java/com/example/ui/components/SingleDatePickerDialog.kt`; `app/src/androidTest/java/com/example/ui/components/SingleDatePickerContentUiTest.kt`.

**Current issue:** Previous two UI tests verified confirm/back for an untouched initial date, but did not exercise tapping a different day before either action. Date cells also lacked stable accessibility semantics or test tags; 1..31 text can be ambiguous across months.

**Minimal implementation:** Add a stable ISO-based semantics test tag (`calendar_day_YYYY-MM-DD`), a spoken date content description and `selected` state to existing day cells. No change to how dates are calculated, saved or displayed, and no new UI component. Add tests: select another date -> selected semantic state -> confirm only this date; select another date -> back -> no confirmation.

**Invariants:** Existing full-screen SingleDatePickerDialog behavior, layout, theme and scroll remain unchanged; Compose tests verify both embedded and legacy mode. Room write only through existing manual save button. No API/Gemini cost, no network changes, no reference licensing changes.

**Verification:** 
- [ ] New `SingleDatePickerContentUiTest` cases fail before the implementation because `onNodeWithTag(calendar_day_...)` cannot find a node; local Android SDK not available, so this RED is a code precondition, not an executed Android test.
- [ ] Run `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` in GitHub Actions.
- [ ] Existing `release-ui-checks.yml` runs all four date-picker tests on emulator, not only compile.
- [ ] Inspect XML test count 4, failures 0.
- [ ] On-device verify TalkBack announces date and selection, and that manual input survives cancel/confirm and offline operation.
- [ ] Keep this PR draft, do not merge.

## Stage 5: Exercise the actual manual-entry transition, not just the calendar body

**Facts:** the previous four UI tests cover `SingleDatePickerContent` (calendar itself), not `AnimatedContent` from manual operation entry. The manual operation form keeps amount, description, category and selected date in `remember` outside the animated branch.

**Why:** a future refactor could accidentally move manual draft state inside the branch and silently clear it. Also, the 900+-line `VoiceManualInputPane.kt` embeds both a complex form and the calendar transition.

**Minimal refactor:** extract just the existing transition markup, with identical timing, dimensions and callbacks, into `ManualInlineDatePickerTransition.kt`. Keep its input state and `BackHandler` in `VoiceManualInputPane`. No new calendar or service. Expose a `formContent` composable slot and the same `SingleDatePickerContent` as before.

**Files:**
- Add `app/src/main/java/com/example/ui/components/ManualInlineDatePickerTransition.kt`
- Modify `app/src/main/java/com/example/ui/components/VoiceManualInputPane.kt` (delegate existing transition only)
- Modify `app/src/androidTest/java/com/example/ui/components/SingleDatePickerContentUiTest.kt` (two additional host tests)
- Update this plan; no change in Room or `AddTransactionDialog.kt`

**Tests:**
- [ ] Code precondition / RED: new tests call `ManualInlineDatePickerTransition`, which does not exist in previous SHA; local Android SDK unavailable, so Android RED run cannot be claimed.
- [ ] GREEN: compile `:app:assembleDebug :app:assembleDebugAndroidTest`.
- [ ] Emulator: six `SingleDatePickerContentUiTest` cases, with two host tests that click calendar, choose another date, cancel/confirm, and assert unchanged amount/description.
- [ ] Phone: fill draft, open picker twice, verify no second Dialog and no data loss or duplicate transaction.
- [ ] Small screen: confirm bottom action remains reachable and no IME overflow.
- [ ] Offline and Gemini: unchanged, zero added calls.

**Risks:** animated content can temporarily render outgoing and incoming subtrees during transitions; maintain state outside the switch and avoid changing parent modal layout. The existing fixed 490dp calendar height is unchanged in this step; responsiveness requires a separate measured UI pass.

**Licensing:** original project code moved only, no external GPL/AGPL/Apache code borrowed.

## Stage 6 — Calendar height measured from the capsule's available viewport

**Approval:** user requested to proceed after stage-6 proposal, without merging PR #107.

**Facts:** `MorphingBottomBar` already measures the visible window in a top-level `BoxWithConstraints` and calculates `fullModalHeight`. Its manual entry is wrapped in `heightIn(max = 540.dp).verticalScroll(...)`; a height measurement *inside* that scrolling subtree is unbounded. `ManualInlineDatePickerTransition` currently forces `.height(490.dp)`.

**Alternatives:** (1) Use `LocalConfiguration.screenHeightDp` inside the calendar (rejected: not the actual viewport; IME/split screen risk); (2) add a nested `BoxWithConstraints` below `verticalScroll` (rejected: infinite height constraints); (3) pass the already measured parent viewport cap (chosen: minimal, deterministic).

**Files and responsibilities:**
- `MorphingBottomBar.kt`: compute `manualInlineCalendarHeight(minOf(maxHeight, fullModalHeight))` *outside* verticalScroll, pass it to manual input. No changes to other modal cases.
- `VoiceManualInputPane.kt`: forward optional `calendarHeight`, preserving existing call signatures.
- `ManualInlineDatePickerTransition.kt`: replace fixed height with supplied Dp; keep default 490 dp and define a pure bound function `manualInlineCalendarHeight(availableHeight)` clamped 0..490.
- `SingleDatePickerContentUiTest.kt`: add a bound function test and a clipped compact-height 320.dp viewport test with a visible, clickable confirmation button.
- Add this plan/spec note; no new Gradle module or service.

**Invariants:** normal screens remain at 490 dp; single reusable calendar and its scrollable LazyColumn preserved; button remains inside bounded height; open/close duration, field state, Room, Safe-to-Spend, Gemini, permissions, and write confirmation unchanged. No extra Gemini calls or third-party licensed code.

**Risks:** extremely small screens might leave too little height for the month list, even while the bottom button remains; IME resize can change the parent height during keyboard dismissal; long captions/accessibility scaling need physical-device verification.

**Tests and validation:**
- [ ] RED: test-only source does not compile against old code because the new function/parameter is absent. Android SDK not installed locally, so no executed Android RED claim.
- [ ] GREEN: `:app:assembleDebug` and `:app:assembleDebugAndroidTest` for final SHA; check Kotlin errors.
- [ ] Run 8 `SingleDatePickerContentUiTest` on emulator, inspect XML test count and 0 errors/failures.
- [ ] On phone check split screen, IME, long month scroll, cancel/confirm, rapid taps, amount/description preserved, normal full-screen date dialog and offline.
- [ ] Diff and main unchanged; PR stays draft. Avoid rerunning older CI unnecessarily.
