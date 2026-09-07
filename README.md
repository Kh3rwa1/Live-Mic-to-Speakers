# Live Mic to Speakers

Android utility for live microphone monitoring, hold-to-record announcements, saved recordings and local audio playback.

## Build and test

- JDK 17, Android SDK 34, Gradle 8.7 (wrapper included).
- `bash ./gradlew :app:assembleDebug`
- `bash ./gradlew :app:testDebugUnitTest :app:lintDebug`
- With an API 24+ device/emulator: `bash ./gradlew :app:connectedDebugAndroidTest`

GitHub Actions runs build/lint/JVM regression checks and API 34 navigation smoke tests. A configured workflow is not evidence that a build has passed; inspect the checks on the pull request.

## Audio behavior

- Live monitoring creates audio resources only after the user starts it. A serial worker owns each capture/playback session; stop and restart cannot overlap resource ownership.
- Nonblocking PCM reads/writes preserve partial output writes. PCM byte sizes are converted to short sample counts explicitly.
- The sample-rate probe validates capture and playback initialization. Hardware echo cancellation and noise suppression are optional; they cannot guarantee feedback-free audio.
- Audio output follows Android's selected media route. The screen displays the routed device and peak input level. There is no forced Bluetooth SCO route or guarantee of low-latency Bluetooth playback.
- Live monitoring and recording stop when their screen leaves the foreground. This is intentional; the app does not claim background microphone capture.
- Saved-track playback stops in the background and restores track, position, and playing/paused intent on return.
- New recordings use AAC in MPEG-4 (`.m4a`). Existing MP3/M4A/WAV/AAC history remains readable. Incomplete, cancelled, empty, or sub-500ms recordings are discarded.
- Recordings live in app-specific storage and are normally deleted when the app is uninstalled. Export anything you need to keep before uninstalling.

## Safety

Start at low speaker volume. Keep the microphone away from speakers; headphones are safer. Acoustic feedback can become very loud. The level display is a relative PCM peak indicator, not a calibrated sound-pressure meter.

## Verification status

The pure-Java reliability suite can run without Android; `ReliabilityChecks` is also wrapped in JUnit for Gradle. It covers unused-screen allocation, cleanup, restart serialization, initialization/device failures, cancellation during preparation, and recording retention rules.

See [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) for release gates. Source improvements alone do not establish production readiness or measured audio quality.
