#!/usr/bin/env bash
# Run only against a dedicated test device/emulator: Gradle installs the debug app under test.
set -euo pipefail
font_scale="${1:-1.0}"
case "$font_scale" in 1.0|2.0) ;; *) echo 'Unsupported test font scale'; exit 2;; esac
previous_font=$(adb shell settings get system font_scale | tr -d '\r')
previous_keyboard=$(adb shell settings get secure show_ime_with_hard_keyboard | tr -d '\r')
restore_setting() {
  if [[ "$3" == 'null' ]]; then adb shell settings delete "$1" "$2" >/dev/null 2>&1
  else adb shell settings put "$1" "$2" "$3" >/dev/null 2>&1; fi
}
restore_device_settings() {
  restore_setting system font_scale "$previous_font" || true
  restore_setting secure show_ime_with_hard_keyboard "$previous_keyboard" || true
}
trap restore_device_settings EXIT
adb shell settings put system font_scale "$font_scale"
adb shell settings put secure show_ime_with_hard_keyboard 1
[[ "$(adb shell settings get system font_scale | tr -d '\r')" == "$font_scale" ]] || { echo 'Font scale was not applied'; exit 1; }
[[ "$(adb shell settings get secure show_ime_with_hard_keyboard | tr -d '\r')" == '1' ]] || { echo 'Software keyboard was not enabled'; exit 1; }
# Preserve screenshots until collection; AGP otherwise uninstalls the app and removes its external files.
output_dir="quality-screenshots-$(date +%s)-$$"
set +e
bash ./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "-Pandroid.testInstrumentationRunnerArguments.qualityOutputDir=$output_dir" 2>&1 | tee device-test.log
statuses=("${PIPESTATUS[@]}")
set -e
result=${statuses[0]}
if [[ "$result" == 0 && "${statuses[1]}" != 0 ]]; then result=1; fi
mkdir -p app/build/quality-screenshots
for screen in record hold live settings settings-rtl player keyboard; do rm -f "app/build/quality-screenshotshots/$screen.png"; done
adb pull "/sdcard/Android/data/com.example.livemictospeaker/files/$output_dir/." app/build/quality-screenshots/ || true
if [[ "$result" == 0 ]]; then
  for screen in record hold live settings settings-rtl player keyboard; do
    if [[ ! -s "app/build/quality-screenshots/$screen.png" ]]; then
      echo "Missing native screenshot: $screen.png"; result=1
    fi
  done
fi
exit "$result"
