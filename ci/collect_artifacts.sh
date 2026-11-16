#!/usr/bin/env bash
set -uo pipefail

echo "=== [CI Artifacts Collector] Collecting AndroidTest Reports, Logcat, and Screenshots ==="
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
ADB="$SDK_ROOT/platform-tools/adb"

ART_DIR="build/test-artifacts"
mkdir -p "$ART_DIR/screenshots"
mkdir -p "$ART_DIR/reports/xml"
mkdir -p "$ART_DIR/reports/html"

# Save host-side results before attempting any device command: ADB can hang when
# an emulator disconnects. A failed test must still produce its XML/HTML report.
cp -r app/build/outputs/androidTest-results/connected/* "$ART_DIR/reports/xml/" 2>/dev/null || true
cp -r app/build/reports/androidTests/connected/* "$ART_DIR/reports/html/" 2>/dev/null || true

# Diagnostics are best effort and must never hide the test outcome behind a hang.
adb_collect() {
  timeout --kill-after=2s 15s "$ADB" "$@"
}

if [ "$(timeout --kill-after=2s 5s "$ADB" get-state 2>/dev/null || true)" != "device" ]; then
  echo "Emulator unavailable; host-side test reports have been preserved."
  exit 0
fi

echo "1. Dumping Logcat..."
adb_collect logcat -d > "$ART_DIR/logcat.txt" 2>&1 || true
ls -lh "$ART_DIR/logcat.txt" || true

echo "2. Pulling UI Screenshots from Emulator..."
adb_collect pull /sdcard/Download/ui_screenshots/. "$ART_DIR/screenshots/" 2>/dev/null || true
adb_collect pull /sdcard/Android/data/ru.personalbudget.app.aadece/files/ui_screenshots/. "$ART_DIR/screenshots/" 2>/dev/null || true
adb_collect exec-out screencap -p > "$ART_DIR/screenshots/99_emulator_final_screen.png" 2>/dev/null || true
ls -lh "$ART_DIR/screenshots/" || true

adb_collect pull /sdcard/Download/release-screenshots/. "$ART_DIR/screenshots/" 2>/dev/null || true

echo "3. Copying AndroidTest XML and HTML reports..."
cp -r app/build/outputs/androidTest-results/connected/* "$ART_DIR/reports/xml/" 2>/dev/null || true
cp -r app/build/reports/androidTests/connected/* "$ART_DIR/reports/html/" 2>/dev/null || true

echo "=== [CI Artifacts Collector] XML Report Summary ==="
while IFS= read -r -d '' xml; do
  echo "--- File: $xml ---"
  cat "$xml"
done < <(find "$ART_DIR/reports/xml" -name 'TEST-*.xml' -print0)

# The workflow owns external emulators. Collect artifacts without shutting it down or publishing Git branches.
adb_collect pull /sdcard/Android/data/ru.personalbudget.app.aadece/files/release-screenshots/. "$ART_DIR/screenshots/" 2>/dev/null || true
adb_collect pull /sdcard/Android/data/ru.personalbudget.app.aadece.preview/files/release-screenshots/. "$ART_DIR/screenshots/" 2>/dev/null || true
if [ "${CI_EXTERNAL_EMULATOR:-false}" != "true" ]; then
  timeout 5 "$ADB" emu kill >/dev/null 2>&1 || true
  timeout 5 "$ADB" kill-server >/dev/null 2>&1 || true
fi

echo "=== [CI Artifacts Collector] Done ==="
