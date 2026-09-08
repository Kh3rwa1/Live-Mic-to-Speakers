# Audio-tool UX and runtime reliability upgrade

This is a focused improvement, not a measured 10/10 certification or permission to publish.

## Changes

- Adaptive, single-column home actions with descriptions that distinguish live monitoring, hold-to-record, recording and the device library. Ads follow the tools instead of separating them.
- Shared high-contrast controls, visible keyboard focus, scrollable tool screens and full, wrapping labels. No fixed-height container around changing route/status text.
- Real microphone input meters: PCM peaks for live monitoring and serialized MediaRecorder amplitude samples for recording/hold. These replace decorative looping waves and are not calibrated sound-pressure measurements.
- Persistent readiness, permission, save and failure feedback. No idle screen claims that the microphone is active. Empty previews are disabled; saved-file and queue state are visible.
- Hold-to-record supports slide-out cancellation and keyboard/screen-reader start/stop. Queued saved clips survive activity recreation; saved files remain in their existing folders.
- Session-scoped recorder error callbacks and amplitude-read failure cleanup. Platform callbacks never release recorder resources themselves. Stale errors cannot stop a newer session, and queued saves still finish after the screen is destroyed.
- CI prints bounded Android JUnit failure details and retains recent failures in diagnostic excerpts instead of early SDK warnings. Existing check coverage, reporter isolation and permissions are unchanged.

## Local verification

On the authored sources, the offline Java compiler compiled the production controller and level conversion with Java 8 compatibility. Eight new runtime scenarios passed 50 repetitions, including 2,500 serialized restart cycles. An additional 100 queued start-failure checks protect recovery messages from being overwritten by idle-state callbacks. Four Python UI/source contracts passed, including normal-text contrast checks, alongside eight CI-reporting regressions. Eight XML files parsed and 12 Java files passed syntax parsing.

This sandbox cannot download the Android SDK. These results are not Android compilation, instrumentation, native screenshot review or physical-device audio tests. The added JUnit and instrumentation tests participate in the repository's existing CI matrix. Read the final PR head's checks, not an earlier commit's results.

## Required before merge/release

- [ ] Final-head debug/release builds, lint, old and new JVM tests, dependency and native-alignment checks.
- [ ] Entire existing API/font-scale device matrix, including long route text, large timers, empty previews and navigation.
- [ ] Inspect fresh native screenshots; exercise TalkBack, keyboard, cancellation gestures, RTL, narrow windows and all saved/library/settings screens. The native visual/accessibility review is still pending.
- [ ] Test runtime microphone failures, full storage, calls/focus changes and headphone disconnection on physical phones.
- [ ] Measure latency, dropouts, battery and wired/USB/Bluetooth behavior at safe volume. No unsupported low-latency guarantee or blind buffer tuning is introduced.
- [ ] Complete real-ad/privacy and signed-artifact checks in RELEASE_CHECKLIST.md.

Application ID, signing and ad identifiers, SDK levels, production gates, recording locations, sharing-provider boundaries and existing tests are preserved. Nothing is merged or published by this change. The broader library/player/privacy audit and physical-device sign-off remain necessary.
