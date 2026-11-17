#!/usr/bin/env bash
set -euo pipefail

output=build/user-journey-raw
package=ru.personalbudget.app.aadece.preview
mkdir -p "$output"

# The workflow launches a fresh emulator and builds both APKs before boot.
adb shell input keyevent 224
adb shell input keyevent 82
adb shell wm dismiss-keyguard
adb install -t app/build/outputs/apk/debug/app-debug.apk
adb install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

status=0
adb shell am instrument -w -e class com.example.UserJourneyVisualAuditTest \
  "$package.test/androidx.test.runner.AndroidJUnitRunner" 2>&1 | tee "$output/instrumentation.log" \
  || status=$?
# am instrument can exit zero even when JUnit reports a failed test.
if [ "$status" -eq 0 ] && ! grep -Eq '^OK \(1 test\)[[:space:]]*$' "$output/instrumentation.log"; then
  status=1
fi

# Copy diagnostics before the action stops the emulator, even on test failure.
{
  adb exec-out run-as "$package" tar -cf - -C files user-journey-audit | tar -xf - -C "$output"
} 2> "$output/capture-transfer.log" || true
if [ "$status" -ne 0 ]; then
  adb logcat -d -b crash -t 250 > "$output/device-crash.log" || true
fi
printf 'instrumentation_exit_code=%s\n' "$status" > "$output/test-status.txt"
exit "$status"
