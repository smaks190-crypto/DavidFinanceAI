# Seamless inline calendar — design

**Scope:** Only VoiceManualInputPane, the manual-entry form within voice input. Ordinary AddTransactionDialog / editing stay unchanged.

**Facts:** VoiceManualInputPane currently opens a separate full-screen SingleDatePickerDialog on its Bento date card. That dialog also serves other call sites and owns existing scrollable month/day presentation. Form amount, description, category and date are remembered above the rendered pane.

**Options:** Duplicate the calendar (rejected: duplicate date rules); redesign modal architecture (rejected: broad impact); reuse the same calendar UI in the existing pane (chosen by user).

**Design:** Keep SingleDatePickerDialog public wrapper. Extract its rendered body into internal SingleDatePickerContent, which can be shown embedded without opening Dialog. VoiceManualInputPane uses AnimatedContent for form ↔ calendar and keeps draft states outside the transition; hide keyboard on open. Confirm modifies date only and returns to form; cancel or Android back returns with no changes. Animations: short slide + fade + SizeTransform; preserve dark style.

**Constraints:** No Room/migrations, financial math, Gemini, David Live or existing financial write flow modifications. No third-party code copied, no new network/API usage. Old full-screen date picker remains for other callers.

**Risks:** Small-screen height, keyboard insets, parent swipe-to-dismiss gestures, changing focus, old full-screen dialog regressions, calendar month range current year +/-1. Manual and instrumented UI checks mandatory before merge.

## Incremental UX verification (stage 5)

The existing manual-entry AnimatedContent should be extracted as a single stateless presentation host, with no new Calendar logic. This allows tests to render the exact transition with a lightweight synthetic form that retains an amount and a description while the calendar is shown. The host must not own or mutate financial operation data. Date confirmation is the only path for a date value to leave the calendar. These synthetic UI tests are *not* a substitute for an on-device integration test with the real BudgetViewModel.

## Stage 6 — Responsive modal height

For manual input only, the calendar keeps its original 490.dp height on large viewports, but contracts to the actual measured capsule area when smaller. The parent capsule already owns a bounded BoxWithConstraints. Since its inner manual form is vertically scrollable, the available height must be measured *before* the scrolling subtree, then passed down as a UI-only Dp value. The calendar's month list is the flexible weighted child, while its confirmation button remains at the bottom. Preserve legacy full-screen date picker users and do not change finance or API behavior.
