# Live Mic to Speakers

Android utility for live microphone monitoring, hold-to-record announcements, saved recordings and local audio playback.

## Build and test

- JDK 17, Android SDK 34, Gradle 8.7 (wrapper included).
- `bash ./gradlew :app:assembleDebug :app:assembleRelease`
- `bash ./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :ads:testDebugUnitTest :ads:testReleaseUnitTest`
- `bash ./gradlew :app:lintDebug :app:lintRelease :ads:lintDebug`
- With an API 24+ device/emulator: `bash ./gradlew :app:connectedDebugAndroidTest`

GitHub Actions builds debug/release, runs lint and JVM regression checks, and runs Android tests on API 24 and 34. Inspect the checks on the pull request: a configured workflow is not proof that the final commit passed. A successful build also uploads `live-mic-debug-apk` for installation testing. The release output is not a signed, device-verified production build.

## Audio and storage behavior

- Live monitoring allocates audio only after an explicit user start. One worker owns each session, including cleanup and nonblocking PCM reads/writes.
- Output follows Android's selected media route. Hardware echo cancellation/noise suppression are optional, not guarantees of feedback-free or low-latency audio.
- Recorder preparation, finalization and cleanup use a serialized worker. Releasing a hold during preparation cancels the pending start. A queued save can finish after the screen closes without updating a destroyed Activity.
- Recording previews and the local-library preview share audio-focus and headphone-disconnect handling. Preview preparation is asynchronous.
- Microphone capture and playback are foreground-screen features, not background services. Saved-track playback retains its existing track/position/playing-intent restoration behavior.
- New recordings are AAC in MPEG-4 (`.m4a`). Existing MP3/M4A/WAV/AAC history remains readable. Cancelled, failed, empty and sub-500ms recordings are discarded.
- Recordings remain in their existing app-specific folders. They are normally removed on uninstall; export anything important before uninstalling.
- Saved-history metadata scans and MediaStore queries run off the UI thread. Library playback uses MediaStore content URIs. Search matches the user's query and keeps playback identity stable while filtering.

## Sharing recordings

Long-press a saved recording and select **Share recording**, then choose the destination app. Sharing uses content URIs and temporary read-only access, not a raw file URI or a write grant. New M4A recordings use `audio/mp4`.

The provider exposes only the app's `Recording` and `HPRecording` folders (external app storage and internal fallback). Unrelated external storage, private files and cache files are excluded. Older files found in public Music folders remain playable when accessible; share those through your device's Files app rather than broadening this provider's access.

## Ads and privacy

- Mobile Ads is not initialized at application startup. Ad measurement is deferred explicitly. The UMP consent result must allow ad requests, and SDK initialization must finish, before the central ad gate opens.
- The home screen gathers consent. Settings → privacy policy → **Ad privacy choices** exposes UMP privacy options when required, or permits retrying consent when unavailable.
- Banner/native resources belong to their screen and are destroyed on teardown or a privacy-state change. Fullscreen requests have separate completion callbacks; missing/offline ads do not block navigation. Dismissal while paused waits for resume, delivers once, and is discarded if the owner is destroyed.
- App-open ads are restricted to opted-in, resumed non-audio home screens. They are not shown over microphone, recording or playback screens.
- **Deployment requirement:** configure and test the application's privacy messages in AdMob, verify actual app/ad IDs and mediation behavior, and maintain a truthful privacy policy/Data Safety disclosure. This implementation is not a certification of legal or store-policy compliance.

## Safety and verification limits

Start at low speaker volume and keep the microphone away from speakers; headphones are safer. Acoustic feedback can become very loud. The input meter is a relative PCM indicator, not a calibrated sound-pressure meter.

JVM checks cover session ownership, cancellation, finalization, restart serialization, failure cleanup, consent readiness, deferred ad completion, search logic and audio MIME mapping. Android tests additionally exercise readable M4A recording, filtered playback identity, recording-provider boundaries, read-only sharing and measurement-deferral metadata, alongside navigation/recreation smoke tests. They do not validate regional consent, mediation network traffic or live ad rendering.

See [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) for device, privacy, sharing, accessibility and release sign-off. Source changes and emulator checks cannot establish real-device latency, Bluetooth quality or production readiness.
