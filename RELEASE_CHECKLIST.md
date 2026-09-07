# Release gates — do not publish on source review alone

These are release sign-off checks, not a list of unimplemented features. Record evidence for the final commit before marking a gate complete.

## Implemented safeguards to verify

- Serialized live-audio sessions and asynchronous recorder preparation/finalization.
- Cancellation while preparing, invalid-recording cleanup and stale-UI callback suppression.
- Shared focus-aware/noisy-route-aware previews and background pause/stop behavior.
- UMP-gated SDK initialization/ad requests, screen-owned ad cleanup and privacy-options access.
- Off-main history/MediaStore loading, content-URI playback and corrected search/filter identity.
- Additional JVM tests, Android recording/filter tests, debug/release builds and an API 24/34 CI matrix.

## Automated and release builds

- [ ] Build, JVM tests and Android lint pass on the final commit, for both debug and release where configured.
- [ ] API 24 and API 34 instrumentation checks pass on the final commit.
- [ ] Signed release build is installable; run device tests against the actual release artifact.

## Real-device audio matrix

- [ ] Test representative Android 7/12/14 and current Android devices.
- [ ] Open/leave live mic without starting: no microphone resources retained.
- [ ] Rapidly start/stop/restart and release hold-to-record during preparation: no overlap, late capture or stale UI.
- [ ] Deny/grant/revoke microphone and audio-library permissions, including Settings revocation.
- [ ] Use the phone speaker with Bluetooth permission denied.
- [ ] Test wired/USB/Bluetooth outputs; disconnect and reconnect during audio.
- [ ] Measure end-to-end latency for each route; publish measured ranges, not “zero latency”.
- [ ] Verify low-volume guidance; test feedback resistance cautiously, never at high volume.
- [ ] Test calls, focus loss/ducking, headphone unplug, screen lock, Home, Back and process recreation across every player/recorder.
- [ ] Verify saved-track and preview pause/resume behavior, position, and filter changes while a track plays.
- [ ] Test short/cancelled recordings, storage-full and finalization failures; no corrupt file shown as saved.
- [ ] Validate M4A files in another player and confirm share/export MIME types and paths.
- [ ] Test large audio libraries, empty libraries, missing files, search and rapid navigation while loading.
- [ ] Test TalkBack, hold-to-record's double-tap fallback, large text, small screens and localization.

## Privacy, security and store sign-off

- [ ] Configure AdMob UMP messages and test fresh install, prior consent, denial, offline/error, privacy-options changes, Activity recreation and applicable regions with designated test devices.
- [ ] Confirm no SDK/ad requests occur before the consent gate opens, including mediation adapters. Verify banner/native cleanup and fullscreen callbacks on real devices. CI disables ads.
- [ ] Review the still-broad external FileProvider path and all external-library sharing flows together before restricting paths; this pass intentionally does not break existing sharing by blindly narrowing them.
- [ ] Verify privacy-policy URL/content, Data Safety declarations, ad IDs and consent-console settings.
- [ ] Check dependencies/security reports and current Play target-SDK requirements. This pass retains targetSdk 34; no claim of current store-submission compliance is made.
- [ ] Confirm application ID, provider authority, versioning and signing. Identity was deliberately preserved; changing it can break upgrades to an existing installation.
- [ ] Complete visual/device QA and an accessibility/localization audit beyond the changed controls.

Background playback/capture remains out of scope. Implement a correctly typed foreground service, user-visible notification and platform permissions before advertising it.
