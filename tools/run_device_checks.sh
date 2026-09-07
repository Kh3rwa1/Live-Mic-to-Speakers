#!/usr/bin/env bash
set -uo pipefail
font_scale="${1:-1.0}"
case "$font_scale" in 1.0|2.0) ;; *) echo 'Unsupported test font scale'; exit 2;; esac
adb shell settings put system font_scale "$font_scale"
trap 'adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true' EXIT
bash ./gradlew --no-daemon :app:connectedDebugAndroidTest 2>&1 | tee device-test.log
result=${PIPESTATUS[0]}
mkdir -p app/build/quality-screenshots
adb pull /sdcard/Android/data/com.example.livemictospeaker/files/quality-screenshots/. app/build/quality-screenshots/ || true
exit "$result"
