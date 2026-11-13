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
