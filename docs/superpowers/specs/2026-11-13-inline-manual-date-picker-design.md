# Seamless inline calendar — design

**Scope:** Only VoiceManualInputPane, the manual-entry form within voice input. Ordinary AddTransactionDialog / editing stay unchanged.

**Facts:** VoiceManualInputPane currently opens a separate full-screen SingleDatePickerDialog on its Bento date card. That dialog also serves other call sites and owns existing scrollable month/day presentation. Form amount, description, category and date are remembered above the rendered pane.

**Options:** Duplicate the calendar (rejected: duplicate date rules); redesign modal architecture (rejected: broad impact); reuse the same calendar UI in the existing pane (chosen by user).

**Design:** Keep SingleDatePickerDialog public wrapper. Extract its rendered body into internal SingleDatePickerContent, which can be shown embedded without opening Dialog. VoiceManualInputPane uses AnimatedContent for form ↔ calendar and keeps draft states outside the transition; hide keyboard on open. Confirm modifies date only and returns to form; cancel or Android back returns with no changes. Animations: short slide + fade + SizeTransform; preserve dark style.

**Constraints:** No Room/migrations, financial math, Gemini, David Live or existing financial write flow modifications. No third-party code copied, no new network/API usage. Old full-screen date picker remains for other callers.

**Risks:** Small-screen height, keyboard insets, parent swipe-to-dismiss gestures, changing focus, old full-screen dialog regressions, calendar month range current year +/-1. Manual and instrumented UI checks mandatory before merge.
