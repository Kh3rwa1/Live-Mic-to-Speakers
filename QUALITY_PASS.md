# App quality and safety pass

This is an incremental, reviewable improvement to the existing app, not a claim of a measured 10/10 product or production certification. App identity, recordings, consent policy, advertising identifiers, and release signing are not silently changed.

## Changes

- Live monitoring observes noisy-output broadcasts, removal of external output devices, and changes to the active AudioTrack route. It stops rather than automatically resuming after an interruption. Removing even an unused external output is deliberately treated conservatively.
- Audio focus and route-loss callbacks are one-shot per session. Closing a session invalidates pending interruption callbacks.
- The live-audio worker passes a cancellation predicate into preparation and rechecks it before capture/playback starts. Resources remain owned and released by that worker.
- The live microphone stops when its Activity pauses. Feedback confirmation dialogs are de-duplicated and dismissed when leaving the screen; returning never starts capture automatically.
- Startup no longer waits an artificial 1.5 seconds or requires a separate Start screen. The direct home screen retains settings, privacy choices and consent collection.
- Home tools have real, translatable text instead of image-only labels, scrollable single-column layout, high-contrast text, large touch targets and visible keyboard focus. Live-microphone messages also use string resources. Other screens still need a complete localization/design audit.
- Deferred fullscreen completion trusts the owner's ON_RESUME event rather than repeating a lifecycle state-snapshot check during dispatch. Its regression retains no-delivery-while-paused, exactly-once, and destruction assertions, with bounded waiting for asynchronous delivery.

## Verification

The existing Android workflow automatically runs debug/release builds, app/ads JVM tests, Android lint, and API 24/34 instrumentation on this branch.

Additional checks include:
- Dependency-free interruption/concurrency/cancelled-start scenarios, also wrapped as JUnit tests.
- Android route-guard registration/cleanup without Bluetooth permission or live capture.
- Home labels, minimum touch sizes, hidden disabled-ad space, and settings/privacy navigation.

The route-guard instrumentation check does not simulate physical device disconnection. The UI assertions do not replace looking at actual native screenshots on small screens and large text settings.

## Before a release

1. Require green checks on the final reviewed commit; investigate failures, do not remove safety assertions.
2. Inspect the native home UI with TalkBack, keyboard navigation, RTL, small screens, and 1.5x/2x text. Visual QA has not been signed off by this source pass.
3. Safely test actual wired, USB and Bluetooth disconnections and explicit route switches. Begin at low volume, away from speakers. Confirm monitoring stops and requires an explicit restart.
4. Measure end-to-end latency, feedback behavior, stability, and battery use on representative phones. There is no zero-latency claim or measurement in this change.
5. Complete the existing RELEASE_CHECKLIST.md, including signed-release installation, current Play target-SDK requirements, live UMP/mediation behavior, privacy-policy/Data Safety verification, and app-specific production ad identifiers. The repository currently uses test ad identifiers; replacing them requires the developer's real configuration.

Nothing in this change enables background microphone capture, merges other pull requests, or publishes an app release.
