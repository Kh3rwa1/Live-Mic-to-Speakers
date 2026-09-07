# Verification and remaining release sign-off

Use the checks on the exact commit you plan to merge or release. The current non-UI modernization is tracked in [PR #7](https://github.com/Kh3rwa1/Live-Mic-to-Speakers/pull/7). Earlier API 36 evidence is in [PR #6](https://github.com/Kh3rwa1/Live-Mic-to-Speakers/pull/6); those results do not validate the newer toolchain or runtime. A workflow definition is not a passing result.

## What this pass verifies

- **Lifecycle state:** recording publication is observed only by active owners, with a current revision delivered after restart and no callback after destruction. Bound playback prepares without autoplay, reports typed errors and releases resources on stop. Pure-Java tests cover snapshot bounds, playback intent and error identity.
- **Installed configuration:** instrumentation protects API 37 targeting, API 24 minimum support, the existing application identity, disabled backups and non-exported app components/recording provider.
- **Resolved dependencies:** debug and release runtime inventories are checked for removed legacy libraries, preview/dynamic versions and supported ads/consent baselines. This policy does not replace vulnerability advisories or live SDK testing.
- **Legacy keyboard insets:** the original 83-pixel IME assertion remains in a direct test of the production padding policy on API 24+. Synthetic IME overrides are not sent through a legacy platform conversion that cannot preserve them. Original-padding and repeated-delivery assertions remain covered.
- **Actual keyboard behavior:** an instrumented test opens the software keyboard in the app window and requires an editable control to remain visible above it. The matrix covers APIs 24/34/36/37, including APIs 36 and 37 at 200% font scale; no API is excluded to make the suite pass.
- **Reliable visual evidence:** each run writes screenshots to a unique test-output directory. Gradle leaves the test APK installed until collection. Seven fresh captures (record, hold, live, settings, settings-rtl, player, keyboard) are required; missing or stale captures cannot produce a successful job.
- **Device-setting cleanup:** the runner verifies the requested font scale and software-keyboard setting, restores the original values, and preserves test failures. Use it only on a dedicated test device/emulator because Gradle installs the debug app under test.
- **Native packaging:** the release APK's packaged arm64/x86_64 ELF LOAD segments must support at least 16 KiB alignment. The Android SDK's `zipalign -c -P 16 -v 4` separately verifies ZIP alignment. Reports are uploaded with build results; oversized or malformed native libraries fail rather than being silently accepted.
- **CI regressions:** eleven dependency-policy tests supplement the existing nine runner and seventeen native-validator tests and the app/ads JVM, lint and Android suites. Demo-mode and missing-production-setting rejection use separate logs, with unexpected failures surfaced rather than hidden. Generated production resources use AGP's variant API; the production gate remains mandatory.

App build and device-test jobs have read-only repository permissions and do not persist checkout credentials. Actions are pinned to resolved commits, and Gradle validates wrapper JARs. An isolated, same-repository PR reporter can read Actions logs and write concise failure comments; it never checks out or executes application code. Weekly Dependabot PRs do not merge themselves.

## Where to find the evidence

In the successful Actions run for the chosen commit, download:

- `live-mic-debug-apk` for installation on a test device.
- `android-test-and-lint-reports` for unit/lint results, production-gate logs, `runtime-dependencies.json`, `native-alignment.json` and ZIP-alignment output.
- `android-device-reports-api-<API>-font-<scale>` for instrumentation reports and the native screenshots.

Run CI helper checks locally with `python3 -m unittest discover -s tools/tests -v`. The offline Java parser checks syntax only; it does not replace Android compilation. Pure-Java fake-recorder checks likewise do not measure real microphone behavior.

## Still requires physical-device or owner sign-off

1. **Audio:** measure end-to-end latency, underruns and battery use on representative phones and wired/USB/Bluetooth routes. Test calls, focus loss, disconnection and explicit restart. Begin at low volume; do not perform feedback tests at high speaker volume.
2. **Visual/accessibility:** UI design is deferred. Inspect the collected native images and test TalkBack, keyboard focus, large text, RTL, cutouts and resizing. Bounds assertions and screenshot capture are not visual or screen-reader sign-off. Library/history screens still need the wider design/localization audit.
3. **Production privacy:** use the real ad configuration and verify regional UMP choices, offline/error/revocation behavior, mediation traffic, privacy policy and Data Safety disclosures. These tests disable ads; they do not validate live ad behavior.
4. **Signed delivery:** follow [PRODUCTION_READINESS.md](PRODUCTION_READINESS.md), retain the existing app identity/signing key where applicable, advance the published version, and test the actual signed artifact. Run it on a 16 KB device or emulator as well: static ELF/ZIP checks do not certify every runtime dependency or the Play-generated APKs from an app bundle.

No signing secrets, production ad identifiers, user recordings, repository protection rules, merges or store releases are changed by this pass. See [NON_UI_MODERNIZATION.md](NON_UI_MODERNIZATION.md) for scope and limitations.
