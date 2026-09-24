# Verification and remaining release sign-off

Use the checks on the exact commit you plan to merge or release. The current non-UI modernization is tracked in [PR #7](https://github.com/Kh3rwa1/Live-Mic-to-Speakers/pull/7). Earlier API 36 evidence is in [PR #6](https://github.com/Kh3rwa1/Live-Mic-to-Speakers/pull/6); those results do not validate the newer toolchain or runtime. A workflow definition is not a passing result.

## What this pass verifies

- **Lifecycle state:** recording publication is observed only by active owners, with a current revision delivered after restart and no callback after destruction. Bound playback prepares without autoplay, reports typed errors and releases resources on stop. Pure-Java tests cover snapshot bounds, playback intent and error identity.
- **Installed configuration:** instrumentation protects API 37 targeting, API 24 minimum support, the existing application identity, disabled backups and non-exported app components/recording provider.
- **Resolved dependencies:** debug and release runtime inventories are checked for removed legacy libraries, unreviewed previews, dynamic versions and supported ads/consent baselines. The exact Privacy Sandbox preview pair retained with Mobile Ads 25.4.0 is documented in `NON_UI_MODERNIZATION.md`; version/SDK drift and incomplete pairs fail, and retained previews are reported explicitly. ViewPager2 is constrained to stable 1.1.0. This policy does not replace vulnerability advisories or live SDK testing.
- **Legacy keyboard insets:** the original 83-pixel IME assertion remains in a direct test of the production padding policy on API 24+. Synthetic IME overrides are not sent through a legacy platform conversion that cannot preserve them. Original-padding and repeated-delivery assertions remain covered.
- **Actual keyboard behavior:** an instrumented test opens the software keyboard in the app window and requires an editable control to remain visible above it. The matrix covers APIs 24/34/36/37, including APIs 36 and 37 at 200% font scale; no API is excluded to make the suite pass.
- **Reliable visual evidence:** each run writes screenshots to a unique test-output directory. Gradle leaves the test APK installed until collection. Seven fresh captures (record, hold, live, settings, settings-rtl, player, keyboard) are required; missing or stale captures cannot produce a successful job.
- **Device-setting cleanup:** the runner verifies the requested font scale and software-keyboard setting, restores the original values, and preserves test failures. Use it only on a dedicated test device/emulator because Gradle installs the debug app under test.
- **Native packaging:** the release APK's packaged arm64/x86_64 ELF LOAD segments must support at least 16 KiB alignment. The Android SDK's `zipalign -c -P 16 -v 4` separately verifies ZIP alignment. Reports are uploaded with build results; oversized or malformed native libraries fail rather than being silently accepted.
- **CI regressions:** dependency-policy, diagnostic-output, SDK-bootstrap, device-runner and native-validator regressions supplement the app/ads JVM, lint and Android suites. Demo-mode and missing-production-setting rejection use separate logs, with unexpected failures surfaced rather than hidden. Generated production resources use AGP's variant API; the production gate remains mandatory.

App build and device-test jobs have read-only repository permissions and do not persist checkout credentials. Actions are pinned to resolved commits, and Gradle validates wrapper JARs. An isolated, same-repository PR reporter can read Actions logs and write concise failure comments; it never checks out or executes application code. Weekly Dependabot PRs do not merge themselves. The owner has explicitly requested merging PR #7 after its final checks pass; that is separate from production publication.

## Quality pass improvements in this release

- **Low-latency audio pipeline & adaptive buffer tuning:** Live monitoring reads and writes in 1-burst chunks (`framesPerBuffer`), eliminating up to 40 ms of input buffer queuing. `OutputBufferTuner` tunes `AudioTrack.setBufferSizeInFrames()` to 2 bursts (~8 ms @ 48 kHz) while maintaining capacity for platform initialization, adaptively growing by 1 burst on underruns up to a 4-burst ceiling.
- **Clock drift mitigation:** `DriftController` monitors `framesWritten - playbackHeadPosition`. When queue depth stays continuously above target for > 1.0 second, a smooth crossfade correction drops up to 1 burst (&le; 2 ms) without audible clicks, rate-limited to at most once per 250 ms.
- **Warm start priming:** `AudioTrack.play()` primes the pipeline with 1 burst of silence before microphone start, eliminating cold-start underrun stalls.
- **Input profiles & AudioAttributes:** Three accessible input profiles in Settings (Low latency, Balanced, Noisy room) dynamically configure hardware AEC, NS, and candidate sources (`VOICE_PERFORMANCE`, `VOICE_RECOGNITION`, `MIC`), matching `USAGE_MEDIA` or `USAGE_VOICE_COMMUNICATION`.
- **Bluetooth honesty notice:** Real-time route classification warns users of inherent Bluetooth A2DP latency while suppressing warnings on wired headphones or USB audio.
- **Live telemetry:** Real-time diagnostics dialog inspects performance mode, underrun counts, buffer size/capacity, instantaneous and 10s queue depth, uptime, and drift corrections.
- **Recording preservation & quarantine:** In-flight recordings are registered in a thread-safe `ActiveRecordings` registry across the entire process lifetime. Stale sweep tasks only delete 0-byte abandoned files older than 24 hours. Interrupted non-empty recordings are never deleted; unreadable files are quarantined with `.unrecovered` extensions and presented to the user in a dismissible library banner for permanent deletion or sharing.
- **Release signing gate:** Production release validation strictly rejects builds configured or signed with debug keystores or debug certificates, verified via `:app:verifyProductionRelease` and `:app:printReleaseSigning`.
- **Test device ID isolation:** AdMob test device IDs are only injected and registered when `BuildConfig.DEBUG` and `FLAG_DEBUGGABLE` are active, ensuring release builds never leak internal test device configurations.
- **Single dialog flow & orientation safety:** Live microphone screen strictly sequences dialogs so speaker feedback rationale and Bluetooth permission requests never overlap. Bluetooth permission is only prompted on API 31+ after a session starts on a Bluetooth route. Dialogs use `DialogFragment` surviving configuration changes, and `configChanges` overrides were removed so layouts adapt cleanly to landscape.
- **User ad preference preservation:** The user's ad on/off toggle is persisted in SharedPreferences and preserved across activity recreations and restarts without startup overwrites.

## Where to find the evidence

In the successful Actions run for the chosen commit, download:

- `live-mic-debug-apk` for installation on a test device.
- `android-test-and-lint-reports` for unit/lint results, production-gate logs, `runtime-dependencies.json`, `native-alignment.json` and ZIP-alignment output.
- `android-device-reports-api-<API>-font-<scale>` for instrumentation reports and the native screenshots.

Run CI helper checks locally with `python3 -m unittest discover -s tools/tests -v`. The offline Java parser checks syntax only; it does not replace Android compilation. Pure-Java fake-recorder checks likewise do not measure real microphone behavior.

## Still requires physical-device or owner sign-off

1. **Audio:** measure end-to-end latency, underruns and battery use on representative phones and wired/USB/Bluetooth routes. Test calls, focus loss, disconnection and explicit restart. Confirm the debug rate/burst/underrun logs on at least one low-end and one flagship phone, verify feedback detection with a real speaker at low volume, and measure battery over ten minutes of live monitoring. Begin at low volume; do not perform feedback tests at high speaker volume.
2. **Visual/accessibility:** UI design is deferred. Inspect the collected native images and test TalkBack, keyboard focus, large text, RTL, cutouts and resizing. Bounds assertions and screenshot capture are not visual or screen-reader sign-off. Library/history screens still need the wider design/localization audit.
3. **Production privacy:** use the real ad configuration and verify regional UMP choices, offline/error/revocation behavior, mediation traffic, privacy policy and Data Safety disclosures. These tests disable ads; they do not validate live ad behavior.
4. **Signed delivery:** follow [PRODUCTION_READINESS.md](PRODUCTION_READINESS.md), retain the existing app identity/signing key where applicable, advance the published version, and test the actual signed artifact. Run it on a 16 KB device or emulator as well: static ELF/ZIP checks do not certify every runtime dependency or the Play-generated APKs from an app bundle.

No signing secrets, production ad identifiers, user recordings, repository protection rules or store releases are changed by this pass. See [NON_UI_MODERNIZATION.md](NON_UI_MODERNIZATION.md) for scope and limitations.
