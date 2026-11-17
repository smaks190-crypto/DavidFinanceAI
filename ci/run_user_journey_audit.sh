#!/usr/bin/env bash
set -euo pipefail

output=build/user-journey-raw
package=ru.personalbudget.app.aadece.preview
mkdir -p "$output"

# The workflow launches a fresh emulator. The preview APK is installed by Gradle.
adb shell input keyevent 224
adb shell input keyevent 82
adb shell wm dismiss-keyguard

status=0
./gradlew :app:connectedDebugAndroidTest --no-daemon \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m -XX:+UseParallelGC" \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.UserJourneyVisualAuditTest \
  || status=$?

# Copy diagnostics before the action stops the emulator, even on test failure.
adb pull "/sdcard/Android/data/$package/files/user-journey-audit/" "$output/" \
  > "$output/adb-pull.log" 2>&1 || true
if [ "$status" -ne 0 ]; then
  adb logcat -d -b crash -t 250 > "$output/device-crash.log" || true
fi
printf 'instrumentation_exit_code=%s\n' "$status" > "$output/test-status.txt"
exit "$status"
