# Live Mic to Speakers

Android utility for live microphone monitoring, hold-to-record announcements, saved recordings and local audio playback.

## Build and test

- JDK 17, Android SDK 34, Gradle 8.7 (wrapper included).
- `bash ./gradlew :app:assembleDebug :app:assembleRelease`
- `bash ./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :ads:testDebugUnitTest :ads:testReleaseUnitTest`
- `bash ./gradlew :app:lintDebug :app:lintRelease :ads:lintDebug`
- With an API 24+ device/emulator: `bash ./gradlew :app:connectedDebugAndroidTest`

GitHub Actions builds debug/release, runs lint and JVM regression checks, and runs Android tests on API 24 and 34. Inspect the checks on the pull request: a configured workflow is not proof that the final commit passed. The release output is not a signed, device-verified production build.

## Audio and storage behavior

- Live monitoring allocates audio only after an explicit user start. One worker owns each session, including cleanup and nonblocking PCM reads/writes.
- Output follows Android's selected media route. Hardware echo cancellation/noise suppression are optional, not guarantees of feedback-free or low-latency audio.
- Recorder preparation, finalization and cleanup use a serialized worker. Releasing a hold during preparation cancels the pending start. A queued save can finish after the screen closes without updating a destroyed Activity.
- Recording previews and the local-library preview share audio-focus and headphone-disconnect handling. Preview preparation is asynchronous.
- Microphone capture and playback are foreground-screen features, not background services. Saved-track playback retains its existing track/position/playing-intent restoration behavior.
- New recordings are AAC in MPEG-4 (`.m4a`). Existing MP3/M4A/WAV/AAC history remains readable. Cancelled, failed, empty and sub-500ms recordings are discarded.
- Recordings remain in their existing app-specific folders. They are normally removed on uninstall; export anything important before uninstalling.
- Saved-history metadata scans and MediaStore queries run off the UI thread. Library playback uses MediaStore content URIs. Search matches the user's query and keeps playback identity stable while filtering.

## Ads and privacy

- Mobile Ads is not initialized at application startup. The UMP consent result must allow ad requests, and SDK initialization must finish, before the central ad gate opens.
- The home screen gathers consent. Settings → privacy policy → **Ad privacy choices** exposes UMP privacy options when required, or permits retrying consent when unavailable.
- Banner/native resources belong to their screen and are destroyed on teardown or a privacy-state change. Fullscreen requests have separate completion callbacks; missing/offline ads do not block navigation.
- App-open ads are restricted to opted-in, resumed non-audio home screens. They are not shown over microphone, recording or playback screens.
- **Deployment requirement:** configure and test the application's privacy messages in AdMob, verify actual app/ad IDs and mediation behavior, and maintain a truthful privacy policy/Data Safety disclosure. This implementation is not a certification of legal or store-policy compliance.

## Safety and verification limits

Start at low speaker volume and keep the microphone away from speakers; headphones are safer. Acoustic feedback can become very loud. The input meter is a relative PCM indicator, not a calibrated sound-pressure meter.

JVM checks cover session ownership, cancellation, finalization, restart serialization, failure cleanup, consent readiness and search logic. Android tests additionally exercise readable M4A recording and filtered playback identity, alongside navigation/recreation smoke tests. The tests disable ads, so they do not validate regional consent or live ad rendering.

See [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) for device, privacy, sharing, accessibility and release sign-off. Source changes and emulator checks cannot establish real-device latency, Bluetooth quality or production readiness.
